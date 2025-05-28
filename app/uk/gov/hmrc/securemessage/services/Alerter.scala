/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.securemessage.services

import org.apache.pekko.actor.ActorSystem
import org.mongodb.scala.bson.ObjectId
import play.api.{ Configuration, Logging }
import uk.gov.hmrc.common.message.model.TaxEntity.getEnrolments
import uk.gov.hmrc.common.message.model.{ Alertable, EmailAlert }
import uk.gov.hmrc.domain.{ Nino, SaUtr }
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.workitem.ProcessingStatus.{ PermanentlyFailed, Succeeded }
import uk.gov.hmrc.mongo.workitem.ResultStatus
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.securemessage.connectors.*
import uk.gov.hmrc.securemessage.models.EmailRequest
import uk.gov.hmrc.securemessage.models.v4.MobileNotification
import uk.gov.hmrc.securemessage.repository.ExtraAlertRepository
import uk.gov.hmrc.securemessage.scheduler.cancellable.{ CancellableOperation, CancellableOperationState, CancellableProcessor }

import javax.inject.{ Inject, Named, Singleton }
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Success

@Singleton
class ExtraAlerter @Inject() (
  val repo: ExtraAlertRepository,
  val email: EmailConnector,
  val preferencesConnector: PreferencesConnector,
  val auditConnector: AuditConnector,
  val configuration: Configuration,
  val mobilePushNotificationsConnector: MobilePushNotificationsConnector,
  @Named("invalid-template-ids-push-notifications") val invalidTemplateIdsForPushNotifications: List[String]
) extends Alerter with CancellableOperationState {

  override def sendAlertCallback(id: ObjectId): Option[String] = None

  override def processEventCallback(id: ObjectId): Option[String] = None
}

trait Alerter
    extends AuditAlerts with CancellableOperation with CancellableProcessor[EmailResults, Alertable]
    with CallbackEventUrl with Logging {

  val repo: ExtraAlertRepository
  val email: EmailConnector
  val preferencesConnector: PreferencesConnector
  val mobilePushNotificationsConnector: MobilePushNotificationsConnector
  val configuration: Configuration
  @Named("invalid-template-ids-push-notifications") val invalidTemplateIdsForPushNotifications: List[String]

  def sendAlerts()(implicit
    ec: ExecutionContext,
    as: ActorSystem
  ): Future[EmailResults] = processItems

  implicit val emptyHeaderCarrier: HeaderCarrier = HeaderCarrier()

  val unprocessedState = EmailResults()

  def pullItem(implicit ec: ExecutionContext): Future[Option[Alertable]] =
    repo.pullMessageToAlert()

  def processItem(state: EmailResults, alertable: Alertable)(implicit
    ec: ExecutionContext
  ): Future[EmailResults] =
    sendAlert(state, alertable)
      .recoverWith { case e: Exception =>
        logger.info(s"Failed to send $alertable", e)
        updateAlertAndAudit(
          alertable = alertable,
          status = PermanentlyFailed,
          e.getMessage,
          state.incrementRequeued
        )
      }

  def sendAlert(results: EmailResults, alertable: Alertable)(implicit
    ec: ExecutionContext
  ): Future[EmailResults] = {

    def emailAddressFor(
      alertable: Alertable
    ): Future[(VerifiedEmailAddressResponse, Option[String])] =
      alertable.alertParams.get("email") match {
        case Some(e) => Future.successful((EmailValidation(e), None))
        case _ =>
          preferencesConnector
            .verifiedEmailAddress(alertable.recipient)
            .map(_ -> Some("preferences"))
      }

    for {
      (verifiedEmailAddress, emailSource) <- emailAddressFor(alertable)
      handled <- verifiedEmailAddress.fold(
                   sendEmailTo(
                     _,
                     alertable,
                     results,
                     emailSource,
                     invalidTemplateIdsForPushNotifications
                   ),
                   handleUnverifiedEmailAddress(alertable, _, results)
                 )
    } yield handled
  }

  def handleUnverifiedEmailAddress(
    alertable: Alertable,
    verifiedEmailNotFound: String,
    results: EmailResults
  )(implicit ec: ExecutionContext): Future[EmailResults] = {

    def markPermanentlyFailed: Future[EmailResults] =
      updateAlertAndAudit(
        alertable,
        PermanentlyFailed,
        verifiedEmailNotFound,
        results.incrementPermanentlyFailed
      )
    markPermanentlyFailed
  }

  def sendEmailTo(
    emailAddress: String,
    alertable: Alertable,
    results: EmailResults,
    emailSource: Option[String],
    invalidTemplateIdsForPushNotifications: List[String]
  )(implicit ec: ExecutionContext): Future[EmailResults] = {
    for {
      _ <- email.send(
             EmailRequest.createEmailRequestFromAlert(
               alertable,
               emailAddress,
               alertable.taxPayerName,
               processEventCallback(alertable.id),
               sendAlertCallback(alertable.id),
               alertable.alertQueue,
               emailSource,
               getEnrolments(alertable.recipient).main
             )
           )
      _ <- {
        auditAlert(ExtraAlertSucceeded(alertable, emailAddress))
        repo.alertCompleted(
          id = alertable.id,
          status = Succeeded
        )
      }
    } yield results.incrementSent
  } andThen { case Success(_) =>
    sendMobileNotification(alertable, invalidTemplateIdsForPushNotifications)
  }

  private def updateAlertAndAudit(
    alertable: Alertable,
    status: ResultStatus,
    failureReason: String,
    results: EmailResults
  )(implicit ec: ExecutionContext): Future[EmailResults] =
    repo
      .alertCompleted(
        alertable.id,
        status
      )
      .map(_ => results)
      .andThen { case _ => auditAlert(ExtraAlertFailed(alertable, failureReason)) }

  private def sendMobileNotification(
    alertable: Alertable,
    invalidTemplateIdsForPushNotifications: List[String]
  )(implicit ec: ExecutionContext): Future[Unit] =
    if (
      invalidTemplateIdsForPushNotifications.contains(
        alertable.alertTemplateName
      )
    ) {
      Future.successful(())
    } else {
      alertable.recipient.identifier match {
        case (Nino(nino)) =>
          mobilePushNotificationsConnector.sendNotification(
            MobileNotification(Nino(nino), alertable.alertTemplateName)
          )
        case (SaUtr(saUtr)) =>
          mobilePushNotificationsConnector.sendNotification(
            MobileNotification(SaUtr(saUtr), alertable.alertTemplateName)
          )

        case _ => Future.successful(())
      }

    }
}

trait CallbackEventUrl {

  def processEventCallback(id: ObjectId): Option[String]

  def sendAlertCallback(id: ObjectId): Option[String]

}
