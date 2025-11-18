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

package uk.gov.hmrc.securemessage.repository

import com.mongodb.client.model.Indexes.ascending
import org.bson.types.ObjectId

import java.time.{ Duration, LocalDate }
import org.mongodb.scala.model.{ Filters, IndexOptions, Updates }
import org.mongodb.scala.result.DeleteResult
import org.mongodb.scala.{ SingleObservableFuture, model }
import play.api.libs.json.{ Json, OFormat }
import play.api.{ Configuration, Environment }
import uk.gov.hmrc.common.message.model.*
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.workitem.ProcessingStatus.{ Cancelled, Deferred, ToDo }
import uk.gov.hmrc.mongo.workitem.{ ProcessingStatus, WorkItem, WorkItemFields, WorkItemRepository }
import uk.gov.hmrc.securemessage.models.v4.{ BrakeBatchApproval, ExtraAlertConfig }

import java.time
import java.time.Instant
import javax.inject.{ Inject, Singleton }
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class ExtraAlertRepository @Inject() (
  val environment: Environment,
  val configuration: Configuration,
  mongo: MongoComponent
)(implicit val ec: ExecutionContext)
    extends WorkItemRepository[ExtraAlert](
      "alert",
      mongo,
      ExtraAlert.extraAlertFormat,
      WorkItemFields(
        id = "_id",
        receivedAt = "receivedAt",
        updatedAt = "updatedAt",
        availableAt = "availableAt",
        status = "status",
        failureCount = "failureCount",
        item = "item"
      ),
      replaceIndexes = false,
      extraIndexes = Seq(
        model.IndexModel(
          ascending("item.messageRecipient", "item.reference", "item.extraReference"),
          IndexOptions().name("extra-alert-index").unique(true).background(true)
        ),
        model.IndexModel(
          ascending("item.reference"),
          IndexOptions().name("item-reference-index").unique(false).background(true)
        )
      )
    ) {

  lazy val extraAlertConfig: Seq[ExtraAlertConfig] = ExtraAlertConfig(configuration)

  def pullMessageToAlert(): Future[Option[Alertable]] =
    pullOutstanding(failedBefore = now().minusMillis(retryIntervalMillis.toLong), availableBefore = now())
      .map(_.flatMap { case WorkItem(workItemId, _, _, _, _, _, alert) =>
        Option(new Alertable {
          def alertParams: Map[String, String] = alert.alertDetails.data

          def auditData: Map[String, String] = Map(
            "alertId"                              -> id.toString,
            alert.messageRecipient.identifier.name -> alert.messageRecipient.identifier.value,
            "messageId"                            -> alert.reference
          )

          def statutory: Boolean = false

          def alertTemplateName: String = alert.emailTemplateId

          def taxPayerName: Option[TaxpayerName] = alert.alertDetails.recipientName

          def id = new ObjectId(workItemId.toString)

          def externalRef: Option[ExternalRef] = alert.externalRef

          def recipient = alert.messageRecipient

          def hardCopyAuditData: Map[String, String] =
            throw new UnsupportedOperationException("No hard copy requests for extra alerts")

          def validFrom: LocalDate =
            throw new UnsupportedOperationException("validFrom not supported in extra alerts")

          def alertQueue: Option[String] = None

          def source: Option[String] = None
        })
      })

  lazy val retryIntervalMillis =
    configuration
      .getOptional[FiniteDuration](s"messages.retryFailedAfter")
      .getOrElse(throw new RuntimeException(s"messages.retryFailedAfter not specified"))
      .toMillis
      .toInt

  def alertCompleted(id: ObjectId, status: ProcessingStatus): Future[Boolean] =
    markAs(id, status)

  def removeAlerts(ref: String)(implicit ec: ExecutionContext): Future[DeleteResult] =
    collection.deleteMany(Filters.equal("item.reference", ref)).toFuture()

  def brakeBatchAccepted(brakeBatchApproval: BrakeBatchApproval)(implicit ec: ExecutionContext): Future[Boolean] = {

    val extraAlertDelay = extraAlertConfig
      .find(_.mainTemplate == brakeBatchApproval.templateId)
      .map(_.delay)
      .getOrElse(Duration.ofMillis(0))
      .toMillis
    val availableAt = now().plusMillis(extraAlertDelay.toLong)

    collection
      .updateOne(
        Filters.and(
          Filters.equal("status", Deferred.name),
          Filters.equal(
            "item.formId",
            if (brakeBatchApproval.formId.equals("Unspecified")) null else brakeBatchApproval.formId
          ),
          Filters.equal("item.alertDetails.templateId", s"${brakeBatchApproval.templateId}_D2")
        ),
        Updates.combine(Updates.set("status", ToDo.name), Updates.set("availableAt", availableAt))
      )
      .toFuture()
      .map(_.getModifiedCount >= 1)
  }

  def brakeBatchRejected(brakeBatchApproval: BrakeBatchApproval)(implicit ec: ExecutionContext): Future[Boolean] =
    collection
      .updateOne(
        Filters.and(
          Filters.equal("status", Deferred.name),
          Filters.equal(
            "item.formId",
            if (brakeBatchApproval.formId.equals("Unspecified")) null else brakeBatchApproval.formId
          ),
          Filters.equal("item.alertDetails.templateId", s"${brakeBatchApproval.templateId}_D2")
        ),
        Updates.set("status", Cancelled.name)
      )
      .toFuture()
      .map(_.getModifiedCount >= 1)

  def now(): Instant = Instant.now()
  override def inProgressRetryAfter: time.Duration = time.Duration.ofHours(1)
}

case class ExtraAlert(
  id: ObjectId = new ObjectId(),
  messageRecipient: TaxEntity,
  reference: String,
  emailTemplateId: String,
  alertDetails: AlertDetails,
  externalRef: Option[ExternalRef] = None,
  extraReference: Option[String] = Option.empty[String],
  formId: Option[String] = None
)

object ExtraAlert {
  // REQUIRED: DO NOT DELETE INTELLIJ's FAILURE TO RECOGNISE
  def build(
    messageRecipient: TaxEntity,
    reference: String,
    emailTemplateId: String,
    alertDetails: AlertDetails,
    externalRef: Option[ExternalRef] = None,
    extraReference: Option[String] = Option.empty[String],
    formId: Option[String] = None
  ): ExtraAlert =
    new ExtraAlert(
      new ObjectId(),
      messageRecipient,
      reference,
      emailTemplateId,
      alertDetails,
      externalRef,
      extraReference,
      formId
    )

  import uk.gov.hmrc.mongo.play.json.formats.MongoFormats.Implicits.objectIdFormat
  import MongoTaxIdentifierFormats._
  implicit val extraAlertFormat: OFormat[ExtraAlert] = Json.format[ExtraAlert]
}
