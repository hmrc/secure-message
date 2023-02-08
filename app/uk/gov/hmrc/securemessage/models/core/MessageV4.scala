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

package uk.gov.hmrc.securemessage.models.core

import org.joda.time.LocalDate
import org.mongodb.scala.bson.ObjectId
import play.api.libs.json.{ Format, Json, OFormat }
import uk.gov.hmrc.mongo.play.json.formats.{ MongoFormats, MongoJodaFormats }
import uk.gov.hmrc.securemessage.controllers.model.ApiFormats
import play.api.libs.functional.syntax._
import play.api.libs.json._

//ToDo created as per schema, need to update as per business rules
case class MessageV4(
  id: ObjectId,
  externalRef: ExternalRef,
  recipient: RecipientV4,
  tags: Option[TagsV4],
  messageType: String,
  validFrom: Option[LocalDate],
  content: List[Content],
  alertDetails: Option[AlertDetailsV4],
  alertQueue: Option[String],
  details: Option[Details]
)

object MessageV4 extends ApiFormats {
  implicit val messageReads: Reads[MessageV4] =
    ((__ \ "externalRef").read[ExternalRef] and
      (__ \ "recipient").read[RecipientV4] and
      (__ \ "tags").readNullable[TagsV4] and
      (__ \ "messageType").read[String] and
      (__ \ "validFrom").readNullable[LocalDate] and
      (__ \ "content").read[List[Content]] and
      (__ \ "alertDetails").readNullable[AlertDetailsV4] and
      (__ \ "alertQueue").readNullable[String] and
      (__ \ "details").readNullable[Details]) {
      (externalRef, recipient, tags, messageType, validFrom, content, alertDetails, alertQueue, details) =>
        MessageV4(
          id = new ObjectId(),
          externalRef,
          recipient,
          tags,
          messageType,
          validFrom,
          content,
          alertDetails,
          alertQueue,
          details
        )
    }
}

object MessageV4Mongo {
  implicit val localDateFormat: Format[LocalDate] = MongoJodaFormats.localDateFormat
  implicit val objectIdFormat: Format[ObjectId] = MongoFormats.objectIdFormat
  implicit val mongoMessageFormat: OFormat[MessageV4] = Json.format[MessageV4]
}

case class ExternalRef(
  id: String,
  source: String
)

object ExternalRef {
  implicit val externalRefFormat: OFormat[ExternalRef] = Json.format[ExternalRef]
}

case class RecipientV4(
  regime: String,
  taxIdentifier: TaxIdentifier,
  name: Option[Name],
  email: Option[String]
)

object RecipientV4 {
  implicit val recipientFormat: OFormat[RecipientV4] = Json.format[RecipientV4]
}

case class TaxIdentifier(
  name: String,
  value: String
)

object TaxIdentifier {
  implicit val taxIdentifierFormat: OFormat[TaxIdentifier] = Json.format[TaxIdentifier]
}

case class Name(
  line1: Option[String],
  line2: Option[String],
  line3: Option[String]
)

object Name {
  implicit val nameFormat: OFormat[Name] = Json.format[Name]
}

case class TagsV4(
  notificationType: Option[String]
)

object TagsV4 {
  implicit val tagsFormat: OFormat[TagsV4] = Json.format[TagsV4]
}

case class Content(lang: Language, subject: String, body: String)

object Content {
  implicit val contentFormat: OFormat[Content] = Json.format[Content]
}

case class AlertDetailsV4(
  data: Data
)

object AlertDetailsV4 {
  implicit val alertDetailsFormat: OFormat[AlertDetailsV4] = Json.format[AlertDetailsV4]
}

case class Data(
  key1: String,
  key2: String
)

object Data {
  implicit val dataFormat: OFormat[Data] = Json.format[Data]
}

case class DetailsV4(
  formId: String,
  issueDate: String,
  batchId: String,
  sourceData: Option[String],
  properties: Option[List[Property]]
)

object DetailsV4 {
  implicit val detailsFormat: OFormat[DetailsV4] = Json.format[DetailsV4]
}

case class Property(name: String, value: String)

object Property {
  implicit val propertyFormat: OFormat[Property] = Json.format[Property]
}
