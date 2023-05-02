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

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import cats.syntax.option.catsSyntaxOptionId
import play.api.Logging
import uk.gov.hmrc.common.message.emailaddress.EmailAddress
import uk.gov.hmrc.common.message.model.EmailAlert
import uk.gov.hmrc.common.message.model.TaxEntity.getEnrolments
import uk.gov.hmrc.domain.{ Nino, SaUtr }
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.workitem.ProcessingStatus.{ PermanentlyFailed, Succeeded }
import uk.gov.hmrc.mongo.workitem.ResultStatus
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.securemessage.connectors.{ EmailConnector, EntityResolverConnector, MobilePushNotificationsConnector }
import uk.gov.hmrc.securemessage.controllers.routes
import uk.gov.hmrc.securemessage.models.v4.{ MobileNotification, SecureMessage }
import uk.gov.hmrc.securemessage.models.{ EmailRequest, Tags }
import uk.gov.hmrc.securemessage.repository.SecureMessageRepository

import javax.inject.{ Inject, Named, Singleton }
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Success

@Singleton
class EmailAlertService @Inject()(
  val secureMessageRepository: SecureMessageRepository,
  val emailConnector: EmailConnector,
  val entityResolverConnector: EntityResolverConnector,
  val auditConnector: AuditConnector,
  val servicesConfig: ServicesConfig,
  val mobilePushNotificationsConnector: MobilePushNotificationsConnector,
  @Named("invalid-template-ids-push-notifications") val invalidTemplateIdsForPushNotifications: List[String]
) extends AuditAlerts with Logging {

  lazy val baseUrl = servicesConfig.baseUrl("secure-message")
  lazy val alertUrl = (id: String) => Some(s"$baseUrl${routes.SecureMessageController.sendAlert(id).url}")

  implicit val headerCarrier = HeaderCarrier()

  val initialState = EmailResults()

  def sendEmailAlerts()(implicit ec: ExecutionContext, mat: Materializer): Future[EmailResults] =
    Source
      .unfoldAsync(NotUsed) { _ =>
        pullItem()
          .map {
            case None       => None
            case Some(item) => Some(NotUsed -> item)
          }
      }
      .runFoldAsync(initialState)(processItem)

  def pullItem(): Future[Option[SecureMessage]] =
    secureMessageRepository.pullMessageToAlert()

  def processItem(state: EmailResults, message: SecureMessage)(
    implicit ec: ExecutionContext
  ): Future[EmailResults] =
    sendAlert(state, message)
      .recoverWith {
        case e: Exception =>
          logger.info(s"Failed to send $message", e)
          updateAlertAndAudit(
            message = message,
            status = PermanentlyFailed,
            e.getMessage,
            state.incrementRequeued
          )
      }

  def sendAlert(results: EmailResults, message: SecureMessage)(implicit ec: ExecutionContext): Future[EmailResults] = {
    for {
      _ <- emailConnector.send(sendEmailRequest(message))
      _ <- {
        auditAlert(AlertSucceeded(message, message.emailAddress))
        secureMessageRepository.alertCompleted(
          id = message._id,
          alert = EmailAlert.success(message.emailAddress, message.templateId),
          status = Succeeded
        )
      }
    } yield {
      results.incrementSent
    }
  } andThen {
    case Success(_) =>
      sendMobileNotification(message, invalidTemplateIdsForPushNotifications)
  }

  private def updateAlertAndAudit(
    message: SecureMessage,
    status: ResultStatus,
    failureReason: String,
    results: EmailResults)(implicit ec: ExecutionContext): Future[EmailResults] =
    secureMessageRepository
      .alertCompleted(message._id, status, EmailAlert.failure(message.templateId, failureReason))
      .map(_ => results)
      .andThen { case _ => auditAlert(AlertFailed(message, failureReason)) }

  private def sendMobileNotification(
    message: SecureMessage,
    invalidTemplateIdsForPushNotifications: List[String]
  )(implicit ec: ExecutionContext): Future[Unit] =
    if (invalidTemplateIdsForPushNotifications.contains(message.templateId)) {
      Future.successful(())
    } else {
      message.recipient.identifier match {
        case Nino(nino) =>
          mobilePushNotificationsConnector.sendNotification(
            MobileNotification(Nino(nino))
          )
        case SaUtr(saUtr) =>
          mobilePushNotificationsConnector.sendNotification(
            MobileNotification(SaUtr(saUtr))
          )

        case _ => Future.successful(())
      }
    }

  def sendEmailRequest(message: SecureMessage): EmailRequest =
    EmailRequest(
      to = List(EmailAddress(message.emailAddress)),
      templateId = message.templateId,
      parameters = message.alertDetails.data ++ message.alertDetails.recipientName
        .fold(Map.empty[String, String])(_.asMap),
      auditData = message.auditData,
      eventUrl = None,
      onSendUrl = alertUrl(message._id.toString),
      alertQueue = message.alertQueue,
      emailSource = None,
      tags = Tags(
        message.externalRef.id.some,
        message.externalRef.source.some,
        getEnrolments(message.recipient).main.some).some
    )
}

case class EmailResults(sent: Int = 0, requeued: Int = 0) {
  def incrementSent: EmailResults = copy(sent = sent + 1)
  def incrementRequeued: EmailResults = copy(requeued = requeued + 1)
}
