/*
 * Copyright 2024 HM Revenue & Customs
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

import org.mongodb.scala.bson.ObjectId
import play.api.Logging
import play.api.i18n.{ I18nSupport, Messages }
import play.api.libs.json.Json
import play.utils.UriEncoding
import play.api.mvc.*
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import uk.gov.hmrc.auth.core.*
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.securemessage.services.{ HtmlCreatorService, SAMessageRendererService, SecureMessageServiceImpl }
import uk.gov.hmrc.securemessage.templates.AtsTemplate
import uk.gov.hmrc.common.message.model.ConversationItem
import uk.gov.hmrc.securemessage.{ MessageNotFound, UserNotAuthorised }
import uk.gov.hmrc.securemessage.models.{ JourneyStep, RenderType }
import play.api.i18n.Messages.implicitMessagesProviderToMessages
import play.twirl.api.Html
import uk.gov.hmrc.securemessage.models.core.Letter

import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

class SecureMessageRenderer @Inject() (
  cc: ControllerComponents,
  val authConnector: AuthConnector,
  override val auditConnector: AuditConnector,
  messageService: SecureMessageServiceImpl,
  htmlCreatorService: HtmlCreatorService,
  saMessageRendererService: SAMessageRendererService
)(implicit ec: ExecutionContext)
    extends BackendController(cc) with AuthorisedFunctions with Auditing with Logging with I18nSupport {

  private val ATS_v2_renderTemplateId = "ats_v2"

  def view(id: ObjectId): Action[AnyContent] = Action.async { implicit request =>
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)
    messageService.getLetter(id) map {
      case Some(letter) if letter.contentParameters.exists(_.templateId == ATS_v2_renderTemplateId) =>
        Ok(AtsTemplate(letter.subject, letter.validFrom))
          .withHeaders("X-Title" -> UriEncoding.encodePathSegment(letter.subject, "UTF-8"))
      case r => InternalServerError
    }
  }

  def getContentBy(id: String, msgType: String): Action[AnyContent] = Action.async { implicit request =>
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

    def renderMessage(replyType: RenderType.ReplyType): Future[Result] =
      messageService.findMessageListById(id).flatMap {
        case Left(UserNotAuthorised(msg)) =>
          logger.warn(s"Error retrieving messages: $msg")
          Future.successful(Unauthorized(msg))
        case Left(MessageNotFound(msg)) =>
          logger.warn(s"Error retrieving messages: $msg")
          Future.successful(NotFound(msg))
        case Left(err) =>
          logger.warn(s"Error retrieving messages: ${err.message}")
          Future.successful(InternalServerError(err.message))
        case Right(msgList) => getHtmlResponse(id, msgList, replyType)

      }

    def getHtmlResponse(
      id: String,
      msgList: List[ConversationItem],
      replyType: RenderType.ReplyType
    ): Future[Result] =
      htmlCreatorService.createConversation(id, msgList, replyType).map {
        case Right(html) => Ok(html)
        case Left(error) =>
          logger.warn(s"HtmlCreatorService conversion error: $error")
          InternalServerError(error)
      }

    msgType match {
      case "Customer" => renderMessage(RenderType.CustomerLink)
      case "Adviser"  => renderMessage(RenderType.Adviser)
      case _          => Future.successful(BadRequest)
    }
  }

  def renderMessageUnencryptedUrl(
    utr: String,
    messageId: String,
    step: Option[JourneyStep]
  ): Action[AnyContent] =
    Action.async { implicit request =>
      implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(
        request
      )
      val renderResult: Letter => Future[Result] = { m =>
        saMessageRendererService
          .apply(message = m, journeyStep = step, utr = utr)
          .map(html => Ok(html).withHeaders("X-Title" -> UriEncoding.encodePathSegment(m.subject, "UTF-8")))
      }
      authorised(Enrolment("IR-SA").withIdentifier("UTR", utr)) {
        for {
          letter <- messageService.getLetter(new ObjectId(messageId))
          render <- letter.map(renderResult).getOrElse(Future.successful(NoContent))
        } yield render
      }.recover { case _: InsufficientEnrolments =>
        // Temporary solution
        val message =
          """
                  <h1>You need to activate your Self Assessment to view your message content.</h1>
                  <p> You will need the activation code you received in the post to do this. </p>
                  <p>Go to your personal tax home page and select the Activate your Self Assessment link.</p>
          """
        Ok(message)
      }
    }
}
