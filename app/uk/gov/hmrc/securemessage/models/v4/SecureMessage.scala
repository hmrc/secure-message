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

package uk.gov.hmrc.securemessage.models.v4

import org.apache.commons.codec.binary.Base64
import org.joda.time.{DateTime, DateTimeZone, LocalDate, LocalTime}
import org.mongodb.scala.bson.ObjectId
import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.common.message.model._
import uk.gov.hmrc.mongo.workitem.ProcessingStatus
import uk.gov.hmrc.mongo.workitem.ProcessingStatus.ToDo
import uk.gov.hmrc.securemessage.controllers.model.ApiFormats
import uk.gov.hmrc.securemessage.models.core.Language
import uk.gov.hmrc.securemessage.models.core.Language.English

import java.security.MessageDigest

case class SecureMessage(_id: ObjectId,
                         externalRef: ExternalRef,
                         recipient: TaxEntity,
                         tags: Option[Map[String, String]] = None,
                         messageType: String,
                         validFrom: LocalDate,
                         content: List[Content],
                         alertDetails: AlertDetails,
                         alertQueue: Option[String],
                         details: Option[MessageDetails],
                         emailAddress: String,
                         hash: String,
                         status: ProcessingStatus = ToDo,
                         alerts: Option[EmailAlert] = None,
                         readTime: Option[DateTime] = None,
                         verificationBrake: Option[Boolean] = None) extends uk.gov.hmrc.securemessage.models.core.Message {

  def templateId: String = alertDetails.templateId

  def auditData: Map[String, String] = alertDetails.data ++
    Map("messageId" -> _id.toString, recipient.identifier.name -> recipient.identifier.value)

  override def issueDate: DateTime = validFrom.toDateTime(LocalTime.MIDNIGHT, DateTimeZone.UTC)
}

object SecureMessage extends ApiFormats with AlertEmailTemplateMapper {

  implicit val secureMessageReads: Reads[SecureMessage] =
    ((__ \ "externalRef").read[ExternalRef] and
      (__ \ "recipient").read[Recipient] and
      (__ \ "messageType").read[String] and
      (__ \ "language").readNullable[Language] and
      (__ \ "validFrom").readNullable[LocalDate] and
      (__ \ "content").read[List[Content]] and
      (__ \ "alertQueue").readNullable[String] and
      (__ \ "details").readNullable[MessageDetails] and
      Reads[Option[Map[String, String]]](jsValue =>
        ( __ \ "alertDetails" \ "data").asSingleJson(jsValue) match {
          case JsDefined(value) => value.validate[Map[String, String]].map(Some.apply).
            orElse(JsError("sourceData: invalid source data provided"))
          case _ => JsSuccess(None)}) and
      Reads[Option[Map[String, String]]](jsValue =>
        ( __ \ "tags").asSingleJson(jsValue) match {
          case JsDefined(value) => value.validate[Map[String, String]]
            .map(Some.apply)
            .orElse(JsError("tags : invalid data provided"))
          case _ => JsSuccess(None)})) {
      (externalRef, recipient, messageType, lang, vf, content, alertQueue, messageDetails, alertDetailsData, tags) =>

        val issueDate = messageDetails.flatMap(_.issueDate).getOrElse(LocalDate.now)
        val validFrom = vf.filter(_.isAfter(issueDate)).getOrElse(issueDate)

        val hash: String = {
          val sha256Digester = MessageDigest.getInstance("SHA-256")
          Base64.encodeBase64String(
            sha256Digester.digest(
              Seq(
                content,
                messageDetails.map(_.formId).getOrElse(""),
                recipient.taxIdentifier.name,
                recipient.taxIdentifier.value,
                validFrom.toString
              ).mkString("/").getBytes("UTF-8")
            )
          )
        }

        val email = recipient.email.fold[Map[String, String]](Map.empty)(v => Map("email" -> v))

        val subjectLang = lang.getOrElse(English)
        val subject = content.find(_.lang == subjectLang).getOrElse(content.head).subject

        val responseTime: Map[String, String] =
          messageDetails.flatMap(_.waitTime).fold[Map[String, String]](Map.empty)(v => Map("waitTime" -> v))

        val data = email ++ responseTime ++ Map(
          "date" -> validFrom.toString,
          "language" -> subjectLang.entryName,
          "subject" -> subject) ++
          alertDetailsData.getOrElse(Map())

        val templateId = messageDetails
          .map(_.formId)
          .map(emailTemplateFromMessageFormId(_))
          .getOrElse(messageType)

        val recipientName = recipient.name.map(_.withDefaultLine1)

        SecureMessage(
          new ObjectId(),
          externalRef,
          TaxEntity.create(recipient.taxIdentifier, recipient.email, recipient.regime),
          tags,
          messageType,
          validFrom,
          content,
          AlertDetails(templateId, recipientName, data),
          alertQueue,
          messageDetails,
          email.getOrElse("email",""),
          hash)
    }

}

case class Content(lang: Language, subject: String, body: String)
object Content {
  implicit val contentFormat: OFormat[Content] = Json.format[Content]
}
