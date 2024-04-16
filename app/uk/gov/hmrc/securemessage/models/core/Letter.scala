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

import java.time.{ Instant, LocalDate, ZoneOffset }
import org.mongodb.scala.bson.ObjectId
import play.api.libs.json._
import play.api.libs.functional.syntax._
import uk.gov.hmrc.common.message.model.{ Adviser, MessageContentParameters, Rescindment }
import uk.gov.hmrc.mongo.play.json.formats.{ MongoFormats, MongoJavatimeFormats }

import uk.gov.hmrc.securemessage.models.core.DateFormats.formatLocalDateReads
import uk.gov.hmrc.securemessage.models.core.DateFormats.formatLocalDateWrites

final case class RecipientName(
  title: Option[String],
  forename: Option[String],
  secondForename: Option[String],
  surname: Option[String],
  honours: Option[String],
  line1: Option[String]
)

object RecipientName {
  implicit val recipientNameFormat: OFormat[RecipientName] = Json.format[RecipientName]
}

final case class Recipient(regime: String, identifier: Identifier, email: Option[String])

object Recipient {
  implicit val recipientFormat: OFormat[Recipient] = Json.format[Recipient]
}

final case class AlertDetails(templateId: String, recipientName: Option[RecipientName])

object AlertDetails {
  implicit val format: Format[AlertDetails] = Json.format[AlertDetails]
}

final case class RenderUrl(service: String, url: String)

object RenderUrl {
  implicit val renderUrlFormat: OFormat[RenderUrl] = Json.format[RenderUrl]
}

final case class ExternalReference(id: String, source: String)

object ExternalReference {
  implicit val externalReferenceFormat: OFormat[ExternalReference] = Json.format[ExternalReference]
}

case class EmailAlert(
  emailAddress: Option[String],
  success: Boolean,
  failureReason: Option[String]
)

object EmailAlert {
  implicit val dateTimeFormat: Format[Instant] = MongoJavatimeFormats.instantFormat
  implicit val emailAlertFormat: OFormat[EmailAlert] = Json.format[EmailAlert]
}

final case class Letter(
  _id: ObjectId,
  subject: String,
  validFrom: LocalDate,
  hash: String,
  alertQueue: Option[String],
  alertFrom: Option[String],
  status: String,
  content: Option[String] = None,
  statutory: Boolean,
  lastUpdated: Option[Instant],
  recipient: Recipient,
  renderUrl: RenderUrl,
  externalRef: Option[ExternalReference],
  alertDetails: AlertDetails,
  alerts: Option[EmailAlert] = None,
  readTime: Option[Instant],
  replyTo: Option[String] = None,
  tags: Option[Map[String, String]] = None,
  rescindment: Option[Rescindment] = None,
  body: Option[Details] = None,
  contentParameters: Option[MessageContentParameters] = None
) extends Message {
  override def issueDate: Instant = validFrom.atStartOfDay().toInstant(ZoneOffset.UTC)
}

object Letter {

  private val localDateFormatString = "yyyy-MM-dd"

  implicit val localDateFormat: Format[LocalDate] =
    Format(formatLocalDateReads(localDateFormatString), formatLocalDateWrites(localDateFormatString))

  implicit val objectIdFormat: Format[ObjectId] = MongoFormats.objectIdFormat

  implicit val isoDateFormat: Format[Instant] = MongoJavatimeFormats.instantFormat

  implicit val isoTime: Reads[LocalDate] = (__ \ "validFrom").read[LocalDate]

  val legacyDefaultAlertDetails = Reads.pure(AlertDetails("newMessageAlert", None))
  val legacyStatutoryForms = Seq("SA309A", "SA309C", "SA326D", "SA328D", "SA370", "SA371")
  def legacyMessageStatutoryFromForm: Reads[Boolean] = (__ \ "body" \ "form").formatNullable[String].map {
    case Some(form) => legacyStatutoryForms.contains(form) || form.startsWith("SA316")
    case None       => false
  }
  def legacyRenderUrl: Reads[RenderUrl] =
    for {
      messageId <- (__ \ "_id").read[ObjectId]
      taxEntity <- (__ \ "recipient").read[Recipient]
    } yield RenderUrl("sa-message-renderer", s"/messages/sa/${taxEntity.identifier.value}/${messageId.toString}")

  implicit val letterReads: Reads[Letter] = ((__ \ "_id").read[ObjectId] ~
    (__ \ "subject").read[String] ~
    (__ \ "validFrom").read[LocalDate] ~
    (__ \ "hash").read[String] ~
    (__ \ "alertQueue").readNullable[String] ~
    (__ \ "alertFrom").readNullable[String] ~
    (__ \ "status").read[String] ~
    (__ \ "content").readNullable[String] ~
    (__ \ "statutory").read[Boolean].orElse(legacyMessageStatutoryFromForm) ~
    (__ \ "lastUpdated").readNullable[Instant] ~
    (__ \ "recipient").read[Recipient] ~
    (__ \ "renderUrl").read[RenderUrl].orElse(legacyRenderUrl) ~
    (__ \ "externalRef").readNullable[ExternalReference] ~
    (__ \ "alertDetails").read[AlertDetails].orElse(legacyDefaultAlertDetails) ~
    (__ \ "alerts").readNullable[EmailAlert] ~
    (__ \ "readTime").readNullable[Instant] ~
    (__ \ "replyTo").readNullable[String] ~
    (__ \ "tags").readNullable[Map[String, String]] ~
    (__ \ "rescindment").readNullable[Rescindment] ~
    (__ \ "body").readNullable[Details] ~
    (__ \ "contentParameters").readNullable[MessageContentParameters])(Letter.apply _)

  implicit val letterWrites: OWrites[Letter] = Json.writes[Letter]

  implicit val letterFormat: OFormat[Letter] = OFormat(letterReads, letterWrites)

  def dateTimeNow: JsValue = Json.toJson(Instant.now())

  def localDateNow: JsValue = Json.toJson(LocalDate.now())
}

case class Details(
  form: Option[String],
  `type`: Option[String],
  suppressedAt: Option[String],
  detailsId: Option[String],
  paperSent: Option[Boolean] = None,
  batchId: Option[String] = None,
  issueDate: Option[LocalDate] = Some(LocalDate.now),
  replyTo: Option[String] = None,
  threadId: Option[String] = None,
  enquiryType: Option[String] = None,
  adviser: Option[Adviser] = None,
  waitTime: Option[String] = None,
  topic: Option[String] = None,
  envelopId: Option[String] = None,
  properties: Option[JsValue] = None
) {

  val paramsMap = Map(
    "formId"       -> form,
    "type"         -> `type`,
    "suppressedAt" -> suppressedAt,
    "detailsId"    -> detailsId,
    "paperSent"    -> paperSent,
    "batchId"      -> batchId,
    "issueDate"    -> issueDate,
    "replyTo"      -> replyTo,
    "threadId"     -> threadId.getOrElse(""),
    "enquiryType"  -> enquiryType,
    "adviser"      -> adviser.map(_.pidId),
    "topic"        -> topic,
    "envelopId"    -> envelopId,
    "properties"   -> properties
  )

  val toMap = paramsMap.collect { case (key, Some(value)) => key -> value.toString }
}
object Details {
  private val localDateFormatString = "yyyy-MM-dd"

  implicit val localDateFormat: Format[LocalDate] =
    Format(formatLocalDateReads(localDateFormatString), formatLocalDateWrites(localDateFormatString))

//  implicit val isoTime: Reads[LocalDate] = (__ \ "issueDate").read[LocalDate]
  implicit val format: OFormat[Details] = Json.format[Details]
}
