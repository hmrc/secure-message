/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.securemessage.controllers

import play.api.Logging
import play.api.libs.json.*
import play.api.mvc.{ Action, AnyContentAsJson, ControllerComponents, Request, Result }
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.common.message.util.SecureMessageUtil
import play.api.libs.functional.syntax.*
import uk.gov.hmrc.common.message.DateValidationException
import uk.gov.hmrc.common.message.model.*

import java.time.LocalDate
import java.security.MessageDigest
import org.apache.commons.codec.binary.Base64
import org.bson.types.ObjectId
import uk.gov.hmrc.common.message.failuremodule.FailureResponseService.errorResponseResult
import uk.gov.hmrc.common.message.validationmodule.MessageValidator.isValidMessage
import uk.gov.hmrc.http.HeaderCarrier

import java.time.format.DateTimeFormatter
import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

@Singleton
class MessageController @Inject() (
  cc: ControllerComponents,
  secureMessageController: SecureMessageController,
  secureMessageUtil: SecureMessageUtil
)(implicit
  ec: ExecutionContext
) extends BackendController(cc) with AlertEmailTemplateMapper with Logging {

  def createMessageForV3(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[Message] { message =>
      isValidMessage(message) match {
        case Success(_) =>
          val v4RequestBody = AnyContentAsJson(SecureMessageUtil.createSecureMessage(request.body))
          secureMessageController.createMessage()(request.withBody(v4RequestBody))

        case Failure(e) =>
          Future.successful(buildBadRequest(e.getMessage))
      }
    }
  }

  val defaultJavaDateFormat = "yyyy-MM-dd"

  def javaDateReads(): Reads[LocalDate] =
    Reads[LocalDate](js =>
      js.validate[String]
        .map[LocalDate](dtString =>
          Try {
            LocalDate.parse(dtString, DateTimeFormatter.ofPattern(defaultJavaDateFormat))
          } match {
            case Success(reads) => reads
            case Failure(_)     => throw DateValidationException("Invalid date format provided")
          }
        )
    )

  private def buildBadRequest(errorMessage: String)(implicit request: Request[JsValue], hc: HeaderCarrier): Result = {
    logger.warn(s"Bad request: reason: $errorMessage")
    secureMessageUtil.auditCreateMessageForFailure(errorMessage)
    errorResponseResult(errorMessage)
  }

  implicit val messageApiV3Reads: Reads[Message] =
    ((__ \ "externalRef").read[ExternalRef] and
      (__ \ "recipient").read[Recipient] and
      (__ \ "messageType").read[String] and
      (__ \ "subject").read[String] and
      (__ \ "validFrom").readNullable[LocalDate](javaDateReads()) and
      (__ \ "content").read[String] and
      (__ \ "details").readNullable[MessageDetails] and
      (__ \ "alertQueue").readNullable[String] and
      (__ \ "emailAlertEventUrl").readNullable[String] and
      Reads[Option[Map[String, String]]](jsValue =>
        (__ \ "alertDetails" \ "data").asSingleJson(jsValue) match {
          case JsDefined(value) =>
            value
              .validate[Map[String, String]]
              .map(Some.apply)
              .orElse(JsError("sourceData: invalid source data provided"))
          case _ => JsSuccess(None)
        }
      ) and
      Reads[Option[Map[String, String]]](jsValue =>
        (__ \ "tags").asSingleJson(jsValue) match {
          case JsDefined(value) =>
            value
              .validate[Map[String, String]]
              .map(Some.apply)
              .orElse(JsError("tags : invalid data provided"))
          case _ => JsSuccess(None)
        }
      )) {
      (
        externalRef,
        recipient,
        messageType,
        subject,
        vf,
        content,
        messageDetails,
        alertQueue,
        emailAlertEventUrl,
        alertDetailsData,
        tags
      ) =>
        val issueDate = messageDetails.flatMap(_.issueDate).getOrElse(LocalDate.now)

        val validFrom = vf.filter(_.isAfter(issueDate)).getOrElse(issueDate)

        val id = new ObjectId

        def decodeBase64String(input: String): String =
          new String(Base64.decodeBase64(input.getBytes("UTF-8")))

        val hash: String = {
          val sha256Digester = MessageDigest.getInstance("SHA-256")
          Base64.encodeBase64String(
            sha256Digester.digest(
              Seq(
                subject,
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
        val responseTime: Map[String, String] =
          messageDetails.flatMap(_.waitTime).fold[Map[String, String]](Map.empty)(v => Map("waitTime" -> v))
        val data = email ++ responseTime ++ Map("date" -> validFrom.toString, "subject" -> subject) ++ alertDetailsData
          .getOrElse(Map())

        val rendererService = externalRef.source.toUpperCase match {
          case "2WSM" => "two-way-message"
          case _      => "message"
        }

        val details = messageDetails.map { ds =>
          val threadId = ds.threadId.getOrElse((new ObjectId).toString) // DC-1738

          Details(
            Some(ds.formId),
            Some(messageType),
            None,
            None,
            Some(ds.paperSent),
            ds.batchId,
            Some(issueDate),
            ds.replyTo,
            Some(threadId),
            ds.enquiryType,
            ds.adviser,
            ds.waitTime,
            ds.topic
          )
        }

        val templateId = messageDetails
          .map(_.formId)
          .map(emailTemplateFromMessageFormId(_))
          .getOrElse(messageType)

        val recipientName = recipient.name.map(_.withDefaultLine1)

        Message(
          id = id,
          recipient = TaxEntity.create(recipient.taxIdentifier, recipient.email, recipient.regime),
          subject = subject,
          body = details,
          validFrom = validFrom,
          lastUpdated = None,
          alertFrom = Some(validFrom),
          alertDetails = AlertDetails(templateId, recipientName, data),
          alertQueue = alertQueue,
          hash = hash,
          statutory = messageDetails.exists(_.statutory),
          renderUrl = RenderUrl(rendererService, ""),
          externalRef = Some(externalRef),
          content = Some(decodeBase64String(content)),
          sourceData = messageDetails.flatMap(_.sourceData),
          emailAlertEventUrl = emailAlertEventUrl,
          tags = tags
        )
    }
}
