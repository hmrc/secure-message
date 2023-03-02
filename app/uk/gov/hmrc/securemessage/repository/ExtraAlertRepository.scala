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

import org.bson.types.ObjectId
import play.api.libs.json.{ Json, OFormat }
import play.api.{ Configuration, Environment }
import uk.gov.hmrc.common.message.model._
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.workitem.{ WorkItemFields, WorkItemRepository }

import java.time
import java.time.Instant
import javax.inject.{ Inject, Singleton }
import scala.concurrent.ExecutionContext

@Singleton
class ExtraAlertRepository @Inject()(
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
      )
    ) {
  def now: Instant = Instant.now()
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
      formId)

  import uk.gov.hmrc.mongo.play.json.formats.MongoFormats.Implicits.objectIdFormat
  import MongoTaxIdentifierFormats._
  implicit val extraAlertFormat: OFormat[ExtraAlert] = Json.format[ExtraAlert]
}
