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

package uk.gov.hmrc.securemessage.controllers

import javax.inject.Inject
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.{ Action, AnyContent, ControllerComponents }
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.securemessage.controllers.models.generic.{ AdviserMessageRequest, ConversationRequest }
import uk.gov.hmrc.securemessage.controllers.utils.EnrolmentHandler._
import uk.gov.hmrc.securemessage.repository.ConversationRepository
import uk.gov.hmrc.securemessage.services.SecureMessageService

import scala.concurrent.{ ExecutionContext, Future }

@SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter"))
class SecureMessageController @Inject()(
  cc: ControllerComponents,
  val authConnector: AuthConnector,
  secureMessageService: SecureMessageService,
  repo: ConversationRepository)(implicit ec: ExecutionContext)
    extends BackendController(cc) with AuthorisedFunctions {

  def createConversation(client: String, conversationId: String): Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      withJsonBody[ConversationRequest] { conversationRequest =>
        repo.insertIfUnique(conversationRequest.asConversation(client, conversationId)).map { isUnique =>
          if (isUnique) Created else Conflict("Duplicate of existing conversation")
        }
      }
  }

  def createAdviserMessage(client: String, conversationId: String): Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      withJsonBody[AdviserMessageRequest] { _ =>
        Future.successful(Created(s"Created for client $client and conversationId $conversationId"))
      }
  }

  def getMetadataForConversations(): Action[AnyContent] = Action.async { implicit request =>
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(request.headers)
    authorised()
      .retrieve(Retrievals.allEnrolments) { enrolments =>
        findEoriEnrolment(enrolments) match {
          case Some(eoriEnrolment) =>
            secureMessageService
              .getConversations(eoriEnrolment)
              .flatMap { conversationDetails =>
                Future.successful(Ok(Json.toJson(conversationDetails)))
              }
          case None => Future.successful(Unauthorized(Json.toJson("No EORI enrolment found")))
        }
      }
  }

  def getConversationContent(client: String, conversationId: String): Action[AnyContent] = Action.async {
    implicit request =>
      implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(request.headers)
      authorised()
        .retrieve(Retrievals.allEnrolments) { enrolments =>
          findEoriEnrolment(enrolments) match {
            case Some(eoriEnrolment) =>
              secureMessageService
                .getConversation(client, conversationId, eoriEnrolment)
                .map {
                  case Some(apiConversation) => Ok(Json.toJson(apiConversation))
                  case _                     => BadRequest(Json.toJson("No conversation found"))
                }
            case None => Future.successful(Unauthorized(Json.toJson("No EORI enrolment found")))
          }
        }
  }
}
