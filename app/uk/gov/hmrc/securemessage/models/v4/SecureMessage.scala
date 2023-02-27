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
import org.joda.time.LocalDate
import org.mongodb.scala.bson.ObjectId
import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.common.message.model
import uk.gov.hmrc.common.message.model.{AlertEmailTemplateMapper, ExternalRef, MessageDetails, Recipient}
import uk.gov.hmrc.securemessage.controllers.model.ApiFormats
import uk.gov.hmrc.securemessage.models.core.Language

import java.security.MessageDigest

case class SecureMessage(_id: ObjectId = new ObjectId,
                          externalRef: ExternalRef,
                          recipient: model.Recipient,
                          tags: Option[Map[String, String]] = None,
                          messageType: String,
                          validFrom: LocalDate,
                          content: List[Content],
                          alertDetails: Option[Map[String, String]] = None,
                          alertQueue: Option[String],
                          details: Option[MessageDetails],
                          hash: String)

object SecureMessage extends ApiFormats with AlertEmailTemplateMapper {
  implicit val secureMessageReads: Reads[SecureMessage] =
    ((__ \ "externalRef").read[ExternalRef] and
      (__ \ "recipient").read[Recipient] and
      (__ \ "messageType").read[String] and
      (__ \ "validFrom").readNullable[LocalDate] and
      (__ \ "content").read[List[Content]] and
      (__ \ "alertQueue").readNullable[String] and
      (__ \ "details").readNullable[MessageDetails] and
      Reads[Option[Map[String, String]]](jsValue =>
        ( __ \ "alertDetails" \ "data").asSingleJson(jsValue) match {
          case JsDefined(value) => value.validate[Map[String, String]].map(Some.apply).
            orElse(JsError("sourceData: invalid source data provided"))
          case JsUndefined() => JsSuccess(None)}) and
      Reads[Option[Map[String, String]]](jsValue =>
        ( __ \ "tags").asSingleJson(jsValue) match {
          case JsDefined(value) => value.validate[Map[String, String]]
            .map(Some.apply)
            .orElse(JsError("tags : invalid data provided"))
          case JsUndefined() => JsSuccess(None)})) {
      (externalRef, recipient, messageType, validFrom, content, alertQueue, messageDetails, alertDetails, tags) =>

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

        SecureMessage(
          _id = new ObjectId(),
          externalRef,
          recipient,
          tags,
          messageType,
          validFrom.getOrElse(LocalDate.now),
          content,
          alertDetails,
          alertQueue,
          messageDetails,
          hash)
    }
}

case class Content(lang: Language, subject: String, body: String)
object Content {
  implicit val contentFormat: OFormat[Content] = Json.format[Content]
}
