/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.securemessage.models.core

import org.joda.time.DateTime
import play.api.libs.json.{ Format, Json, OFormat }
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

final case class RecipientName(
  title: String,
  forename: String,
  secondForename: String,
  surname: String,
  honours: String,
  line1: String)

object RecipientName {
  implicit val recipientNameFormat = Json.format[RecipientName]
}

final case class Recipient(regime: String, identifier: Identifier, email: String)

object Recipient {
  implicit val recipientFormat = Json.format[Recipient]
}

final case class AlertDetails(templateId: String, recipientName: RecipientName)

object AlertDetails {
  implicit val format: Format[AlertDetails] = Json.format[AlertDetails]
}

final case class RenderUrl(service: String, url: String)

object RenderUrl {
  implicit val renderUrlFormat = Json.format[RenderUrl]
}

final case class ExternalReference(id: String, source: String)

object ExternalReference {
  implicit val externalReferenceFormat = Json.format[ExternalReference]
}

case class EmailAlert(
  emailAddress: Option[String],
  alertTime: DateTime,
  success: Boolean,
  failureReason: Option[String]
)

object EmailAlert {
  implicit val isoDateFormat: Format[DateTime] = ReactiveMongoFormats.dateTimeFormats
  implicit val emailAlertFormat: OFormat[EmailAlert] = Json.format[EmailAlert]
}

final case class Letter(
  _id: BSONObjectID,
  subject: String,
  validFrom: String,
  hash: String,
  alertQueue: String,
  alertFrom: String,
  status: String,
  content: String,
  statutory: Boolean,
  lastUpdated: DateTime,
  recipient: Recipient,
  renderUrl: RenderUrl,
  externalRef: ExternalReference,
  alertDetails: AlertDetails,
  alerts: Option[EmailAlert] = None
)

object Letter {
  implicit val objectIdFormat: Format[BSONObjectID] = ReactiveMongoFormats.objectIdFormats

  implicit val isoDateFormat: Format[DateTime] = ReactiveMongoFormats.dateTimeFormats

  implicit val letterFormat: OFormat[Letter] = Json.format[Letter]
}
