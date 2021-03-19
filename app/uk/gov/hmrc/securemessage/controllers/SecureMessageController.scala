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
import play.api.Logging
import play.api.i18n.I18nSupport
import javax.naming.CommunicationException
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.{ Action, AnyContent, ControllerComponents, Result }
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.securemessage._
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
          case Left(error: SecureMessageError) => handleErrors(client, conversationId, error)
        }
      }.recover {
        case error: Exception => handleErrors(client, conversationId, error)
      }
  }

  def addCaseworkerMessage(client: String, conversationId: String): Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      withJsonBody[CaseworkerMessageRequest] { caseworkerMessageRequest =>
        secureMessageService.addCaseWorkerMessageToConversation(client, conversationId, caseworkerMessageRequest).map {
          case Right(_) =>
            Created(Json.toJson(s"Created case worker message for client $client and conversationId $conversationId"))
          case Left(error) => handleErrors(client, conversationId, error)
        }
      }.recover {
        case error: Exception => handleErrors(client, conversationId, error)
      }
  }

  def addCustomerMessage(client: String, conversationId: String): Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      authorised().retrieve(Retrievals.allEnrolments) { enrolments: Enrolments =>
        withJsonBody[CustomerMessageRequest] { customerMessageRequest =>
          secureMessageService
            .addCustomerMessageToConversation(client, conversationId, customerMessageRequest, enrolments)
            .map {
              case Right(_) =>
                Created(Json.toJson(s"Created customer message for client $client and conversationId $conversationId"))
              case Left(error) => handleErrors(client, conversationId, error)
            }
        }.recover {
          case error: Exception => handleErrors(client, conversationId, error)
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
                val filteredEnrolments = filterEnrolments(authEnrolments, enrolmentKeys, customerEnrolments)
                secureMessageService.getConversationsFiltered(filteredEnrolments, tags).flatMap { conversationDetails =>
                  Future.successful(Ok(Json.toJson(conversationDetails)))
                }
              }
        }
      }
    }

  def getConversationContent(client: String, conversationId: String): Action[AnyContent] = Action.async {
    implicit request =>
      authorised()
        .retrieve(Retrievals.allEnrolments) { authEnrolments =>
          if (authEnrolments.enrolments.isEmpty) {
            Future.successful(Unauthorized(Json.toJson("No enrolment found")))
          } else {
            val customerEnrolments = mapToCustomerEnrolments(authEnrolments)
            secureMessageService
              .getConversation(client, conversationId, customerEnrolments)
              .map {
                case Right(apiConversation) => Ok(Json.toJson(apiConversation))
                case _                      => NotFound(Json.toJson("No conversation found"))
              }
          }
        }
  }

  private def mapToCustomerEnrolments(authEnrolments: Enrolments): Set[CustomerEnrolment] =
    authEnrolments.enrolments
      .flatMap(e => e.identifiers.map(i => CustomerEnrolment(e.key, i.key, i.value)))

  def addCustomerReadTime(client: String, conversationId: String): Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      authorised()
        .retrieve(Retrievals.allEnrolments) { enrolments: Enrolments =>
          withJsonBody[ReadTime] { readTime: ReadTime =>
            secureMessageService
              .updateReadTime(client, conversationId, enrolments, readTime.timestamp)
              .map {
                case Right(_)    => Created(Json.toJson("read time successfully added"))
                case Left(error) => handleErrors(client, conversationId, error)
              }
          }
        }
  }

  private def handleErrors(client: String, conversationId: String, error: Exception): Result = {
    val errMsg =
      s"Error on conversation with client: $client, conversationId: $conversationId, error message: ${error.getMessage}"
    logger.error(error.getMessage, error.getCause)
    error match {
      case _: EmailSendingError          => Created(Json.toJson(errMsg))
      case _: NoReceiverEmailError       => Created(Json.toJson(errMsg))
      case _: DuplicateConversationError => Conflict(Json.toJson(errMsg))
      case _: InvalidContent             => BadRequest(Json.toJson(errMsg))
      case _: ParticipantNotFound        => Unauthorized(Json.toJson(errMsg))
      case _: ConversationNotFound       => NotFound(Json.toJson(errMsg))
      case cex: CommunicationException   => BadGateway(cex.getMessage)
      case _: StoreError                 => InternalServerError(Json.toJson(errMsg))
      case _                             => InternalServerError(Json.toJson(errMsg))
    }
  }
}
