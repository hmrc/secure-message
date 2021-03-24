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
import play.api.i18n.I18nSupport
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.{ Action, AnyContent, ControllerComponents, Result }
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.securemessage._
import uk.gov.hmrc.securemessage.controllers.model.cdcm.write._
import uk.gov.hmrc.securemessage.controllers.model.common.CustomerEnrolment
import uk.gov.hmrc.securemessage.controllers.model.common.read._
import uk.gov.hmrc.securemessage.controllers.model.common.write._
import uk.gov.hmrc.securemessage.controllers.utils.EnrolmentHelper._
import uk.gov.hmrc.securemessage.controllers.utils.QueryStringValidation
import uk.gov.hmrc.securemessage.models.core.Conversation
import uk.gov.hmrc.securemessage.services.SecureMessageService
import uk.gov.hmrc.time.DateTimeUtils

import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

@SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter"))
class SecureMessageController @Inject()(
  cc: ControllerComponents,
  val authConnector: AuthConnector,
  secureMessageService: SecureMessageService,
  dataTimeUtils: DateTimeUtils)(implicit ec: ExecutionContext)
    extends BackendController(cc) with AuthorisedFunctions with QueryStringValidation with I18nSupport with Logging {

  def createConversation(client: String, conversationId: String): Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      client match {
        case "cdcm" =>
          withJsonBody[CdcmConversation] { cdcmConversation =>
            saveConversation(cdcmConversation.asConversationWithCreatedDate(client, conversationId, dataTimeUtils.now))
          }
        case _ => Future(handleErrors(client, conversationId, InvalidRequest(s"Not supported client: $client")))
      }

  }

  def addCaseworkerMessage(client: String, conversationId: String): Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      withJsonBody[CaseworkerMessage] { caseworkerMessageRequest =>
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
        withJsonBody[CustomerMessage] { customerMessageRequest =>
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
    tags: Option[List[FilterTag]]): Action[AnyContent] =
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

  private def saveConversation(conversation: Conversation)(implicit hc: HeaderCarrier): Future[Result] =
    secureMessageService
      .createConversation(conversation)
      .map {
        case Right(_)                        => Created
        case Left(error: SecureMessageError) => handleErrors(conversation.client, conversation.id, error)
      }
      .recover {
        case error: Exception => handleErrors(conversation.client, conversation.id, error)
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
    val jsonError = Json.toJson(errMsg)
    error match {
      case EmailSendingError(_)                        => Created(jsonError)
      case NoReceiverEmailError(_)                     => Created(jsonError)
      case DuplicateConversationError(_, _)            => Conflict(jsonError)
      case InvalidContent(_, _) | InvalidRequest(_, _) => BadRequest(jsonError)
      case ParticipantNotFound(_)                      => Unauthorized(jsonError)
      case ConversationNotFound(_)                     => NotFound(jsonError)
      case EisForwardingError(_)                       => BadGateway(jsonError)
      case StoreError(_, _)                            => InternalServerError(jsonError)
      case _                                           => InternalServerError(jsonError)
    }
  }
}
