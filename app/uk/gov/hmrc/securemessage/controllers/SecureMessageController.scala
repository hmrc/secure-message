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

import play.api.Logging

import javax.inject.Inject
import play.api.i18n.I18nSupport
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.{ Action, AnyContent, ControllerComponents, Result }
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.securemessage.{ DuplicateConversationError, EmailError, NoReceiverEmailError, SecureMessageError, StoreError }
import uk.gov.hmrc.securemessage.controllers.models.generic._
import uk.gov.hmrc.securemessage.controllers.utils.EnrolmentHelper._
import uk.gov.hmrc.securemessage.controllers.utils.QueryStringValidation
import uk.gov.hmrc.securemessage.services.SecureMessageService

import scala.concurrent.{ ExecutionContext, Future }
@SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter"))
class SecureMessageController @Inject()(
  cc: ControllerComponents,
  val authConnector: AuthConnector,
  secureMessageService: SecureMessageService)(implicit ec: ExecutionContext)
    extends BackendController(cc) with AuthorisedFunctions with QueryStringValidation with I18nSupport with Logging {

  def createConversation(client: String, conversationId: String): Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      withJsonBody[ConversationRequest] { conversationRequest =>
        secureMessageService.createConversation(conversationRequest.asConversation(client, conversationId)).map {
          case Right(_)                        => Created
          case Left(error: SecureMessageError) => handleCreateConversationErrors(conversationId, error)
        }
      }.recover {
        case error: Exception => handleCreateConversationErrors(conversationId, error)
      }
  }

  private def handleCreateConversationErrors(conversationId: String, error: Exception): Result = {
    val errMsg = s"Error on conversation with id $conversationId: ${error.getMessage}"
    logger.error(error.getMessage, error.getCause)
    error match {
      case ee: EmailError                 => Created(Json.toJson(ee.message))
      case ee: NoReceiverEmailError       => Created(Json.toJson(ee.message))
      case de: DuplicateConversationError => Conflict(Json.toJson(de.message))
      case se: StoreError                 => InternalServerError(Json.toJson(se.message))
      case _                              => InternalServerError(Json.toJson(errMsg))
    }
  }

  def createCaseworkerMessage(client: String, conversationId: String): Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      withJsonBody[CaseworkerMessageRequest] { _ =>
        Future.successful(Created(s"Created for client $client and conversationId $conversationId"))
      }

  }

  def createCustomerMessage(client: String, conversationId: String): Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      authorised().retrieve(Retrievals.allEnrolments) { enrolments: Enrolments =>
        withJsonBody[CustomerMessageRequest] { message =>
          secureMessageService.addMessageToConversation(client, conversationId, message, enrolments).map { _ =>
            Created(s"Created for client $client and conversationId $conversationId")
          }
        }.recover {
          case ae: AuthorisationException    => Unauthorized(ae.reason)
          case iae: IllegalArgumentException => NotFound(iae.getMessage)
        }
      }
  }

  def getMetadataForConversationsFiltered(
    enrolmentKeys: Option[List[String]],
    customerEnrolments: Option[List[CustomerEnrolment]],
    tags: Option[List[Tag]]): Action[AnyContent] =
    Action.async { implicit request =>
      {
        validateQueryParameters(request.queryString, "enrolment", "enrolmentKey", "tag") match {
          case Left(e) => Future.successful(BadRequest(Json.toJson(e.getMessage)))
          case _ =>
            authorised()
              .retrieve(Retrievals.allEnrolments) { authEnrolments =>
                filterEnrolments(authEnrolments, enrolmentKeys, customerEnrolments) match {
                  case results if results.isEmpty => Future.successful(Unauthorized(Json.toJson("No enrolment found")))
                  case filteredEnrolments =>
                    secureMessageService.getConversationsFiltered(filteredEnrolments, tags).flatMap {
                      conversationDetails =>
                        Future.successful(Ok(Json.toJson(conversationDetails)))
                    }
                }
              }
        }
      }
    }

  def getConversationContent(
    client: String,
    conversationId: String,
    enrolmentKey: String,
    enrolmentName: String): Action[AnyContent] = Action.async { implicit request =>
    authorised()
      .retrieve(Retrievals.allEnrolments) { enrolments =>
        findEnrolment(enrolments, enrolmentKey, enrolmentName) match {
          case Some(enrolment) =>
            secureMessageService
              .getConversation(client, conversationId, enrolment)
              .map {
                case Some(apiConversation) => Ok(Json.toJson(apiConversation))
                case _                     => NotFound(Json.toJson("No conversation found"))
              }
          case None => Future.successful(Unauthorized(Json.toJson("No EORI enrolment found")))
        }
      }
  }

  def addCustomerReadTime(client: String, conversationId: String): Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      authorised()
        .retrieve(Retrievals.allEnrolments) { enrolments: Enrolments =>
          withJsonBody[ReadTime] { readTime: ReadTime =>
            secureMessageService
              .updateReadTime(client, conversationId, enrolments, readTime.timestamp)
              .map {
                case true  => Created(Json.toJson("read time successfully added"))
                case false => BadRequest(Json.toJson("issue with updating read time"))
              }
          }
        }
  }
}
