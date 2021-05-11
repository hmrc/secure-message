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

import org.joda.time.LocalDate
import play.api.libs.json.JodaReads.{ jodaDateReads, jodaLocalDateReads }
import play.api.libs.json.JodaWrites.{ jodaDateWrites, jodaLocalDateWrites }
import play.api.libs.json.{ Format, Json, OFormat, Reads, Writes, __ }
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import org.joda.time.DateTime

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

final case class Recipient(regime: String, identifier: Identifier, email: Option[String])

object Recipient {
  implicit val recipientFormat = Json.format[Recipient]
}

final case class AlertDetails(templateId: String, recipientName: Option[RecipientName])

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
  success: Boolean,
  failureReason: Option[String]
)

object EmailAlert {
  implicit val dateTimeFormat: Format[DateTime] = ReactiveMongoFormats.dateTimeFormats
  implicit val emailAlertFormat: OFormat[EmailAlert] = Json.format[EmailAlert]
}

final case class Letter(
  _id: BSONObjectID,
  subject: String,
  validFrom: LocalDate,
  hash: String,
  alertQueue: String,
  alertFrom: Option[String],
  status: String,
  content: String,
  statutory: Boolean,
  lastUpdated: Option[DateTime],
  recipient: Recipient,
  renderUrl: RenderUrl,
  externalRef: Option[ExternalReference],
  alertDetails: AlertDetails,
  alerts: Option[EmailAlert] = None,
  readTime: Option[DateTime]
)

object Letter {

  private val localDateFormatString = "yyyy-MM-dd"

  implicit val localDateFormat: Format[LocalDate] =
    Format(jodaLocalDateReads(localDateFormatString), jodaLocalDateWrites(localDateFormatString))

  implicit val objectIdFormat: Format[BSONObjectID] = ReactiveMongoFormats.objectIdFormats

  implicit val isoDateFormat: Format[DateTime] = ReactiveMongoFormats.dateTimeFormats

  private val dateFormatString = "yyyy-MM-dd'T'HH:mm:ss.SSSZ"

  implicit val isoTime: Reads[LocalDate] = (__ \ "validFrom").read[LocalDate]

  implicit val dateFormatWrites: Writes[DateTime] =
    Format(jodaDateReads(dateFormatString), jodaDateWrites(dateFormatString))

  implicit val letterFormat: OFormat[Letter] = Json.format[Letter]
}
