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

import org.apache.commons.codec.binary.Base64
import javax.inject.Inject
import play.api.Logging
import play.api.i18n.I18nSupport
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.{ Action, AnyContent, ControllerComponents }
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.securemessage._
import uk.gov.hmrc.securemessage.controllers.model.MessageType.{ Conversation, Letter }
import uk.gov.hmrc.securemessage.controllers.model.cdcm.write._
import uk.gov.hmrc.securemessage.controllers.model.common.write._
import uk.gov.hmrc.securemessage.controllers.model.{ ClientName, MessageType }
import uk.gov.hmrc.securemessage.controllers.utils.QueryStringValidation
import uk.gov.hmrc.securemessage.models.core.{ ConversationFilters, CustomerEnrolment, FilterTag }
import uk.gov.hmrc.securemessage.services.{ Auditing, ImplicitClassesExtensions, SecureMessageService }
import uk.gov.hmrc.time.DateTimeUtils

import scala.concurrent.{ ExecutionContext, Future }

@SuppressWarnings(Array("org.wartremover.warts.All"))
class SecureMessageController @Inject()(
  cc: ControllerComponents,
  val authConnector: AuthConnector,
  override val auditConnector: AuditConnector,
  secureMessageService: SecureMessageService,
  dataTimeUtils: DateTimeUtils)(implicit ec: ExecutionContext)
    extends BackendController(cc) with AuthorisedFunctions with QueryStringValidation with I18nSupport
    with ErrorHandling with Auditing with Logging with ImplicitClassesExtensions {

  def createConversation(client: ClientName, conversationId: String): Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      withJsonBody[CdcmConversation] { cdcmConversation =>
        val conversation =
          cdcmConversation.asConversationWithCreatedDate(client.entryName, conversationId, dataTimeUtils.now)
        secureMessageService
          .createConversation(conversation)
          .map {
            case Right(_) =>
              auditCreateConversation("CreateQueryConversationSuccess", conversation, "Conversation Created")
              Created
            case Left(error: SecureMessageError) =>
              auditCreateConversation("CreateNewQueryConversationFailed", conversation, "Conversation Created")
              handleErrors(conversation.id, error, Some(ClientName.withName(conversation.client)))
          }
      }
  }

  def addCaseworkerMessage(client: ClientName, conversationId: String): Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      withJsonBody[CaseworkerMessage] { caseworkerMessageRequest =>
        secureMessageService
          .addCaseWorkerMessageToConversation(client.entryName, conversationId, caseworkerMessageRequest)
          .map {
            case Right(_) =>
              val _ = auditCaseworkerReply(
                "CaseWorkerReplyToConversationSuccess",
                client,
                conversationId,
                caseworkerMessageRequest)
              Created(Json.toJson(s"Created case worker message for client $client and conversationId $conversationId"))
            case Left(error) =>
              val _ = auditCaseworkerReply(
                "CaseWorkerReplyToConversationFailed",
                client,
                conversationId,
                caseworkerMessageRequest)
              handleErrors(conversationId, error, Some(client))
          }
      }
  }

  def addCustomerMessage(client: ClientName, conversationId: String): Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      authorised().retrieve(Retrievals.allEnrolments) { enrolments: Enrolments =>
        withJsonBody[CustomerMessage] { customerMessageRequest =>
          secureMessageService
            .addCustomerMessageToConversation(client.entryName, conversationId, customerMessageRequest, enrolments)
            .map {
              case Right(_) =>
                auditCustomerReply("CustomerReplyToConversationSuccess", client, conversationId, customerMessageRequest)
                Created(Json.toJson(s"Created customer message for client $client and conversationId $conversationId"))
              case Left(error) =>
                auditCustomerReply("CustomerReplyToConversationFailed", client, conversationId, customerMessageRequest)
                handleErrors(conversationId, error, Some(client))
            }
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
                val filters = ConversationFilters(enrolmentKeys, customerEnrolments, tags)
                secureMessageService
                  .getConversationsFiltered(authEnrolments, filters)
                  .map(conversationDetails => Ok(Json.toJson(conversationDetails)))
              }
        }
      }
    }

  def getConversationContent(client: ClientName, conversationId: String): Action[AnyContent] = Action.async {
    implicit request =>
      authorised()
        .retrieve(Retrievals.allEnrolments) { authEnrolments =>
          if (authEnrolments.enrolments.isEmpty) {
            Future.successful(Unauthorized(Json.toJson("No enrolment found")))
          } else {
            secureMessageService
              .getConversation(client.entryName, conversationId, authEnrolments.asCustomerEnrolments)
              .map {
                case Right(apiConversation) => Ok(Json.toJson(apiConversation))
                case _                      => NotFound(Json.toJson("No conversation found"))
              }
          }
        }
  }

  def getContentDetail(rawId: String): Action[AnyContent] = Action.async { implicit request =>
    decodePath(rawId) match {
      case Right((messageType, id)) =>
        authorised()
          .retrieve(Retrievals.allEnrolments) { authEnrolments =>
            if (authEnrolments.enrolments.isEmpty) {
              Future.successful(Unauthorized(Json.toJson("No enrolment found")))
            } else {
              MessageType.withNameOption(messageType) match {
                case Some(Conversation) =>
                  secureMessageService
                    .getConversation(id, authEnrolments.asCustomerEnrolments)
                    .map {
                      case Right(apiConversation) => Ok(Json.toJson(apiConversation))
                      case Left(error)            => handleErrors(id, error)
                    }
                case Some(Letter) =>
                  secureMessageService.getLetter(id, authEnrolments.asCustomerEnrolments).map {
                    case Right(apiLetter) => Ok(Json.toJson(apiLetter))
                    case Left(error)      => handleErrors(id, error)
                  }
                case None => Future.successful(BadRequest(Json.toJson("Invalid message type")))
              }
            }
          }
      case Left(error) => Future.successful(BadRequest(Json.toJson(error.message)))
    }
  }

  private[controllers] def decodePath(path: String): Either[SecureMessageError, (String, String)] =
    new String(Base64.decodeBase64(path.getBytes("UTF-8"))).split("/").toList match {
      case messageType :: id :: _ => Right((messageType, id))
      case _                      => Left(InvalidPath("Invalid URL path"))
    }

  def addCustomerReadTime(client: ClientName, conversationId: String): Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      authorised()
        .retrieve(Retrievals.allEnrolments) { enrolments: Enrolments =>
          withJsonBody[ReadTime] { readTime: ReadTime =>
            secureMessageService
              .updateReadTime(client.entryName, conversationId, enrolments, readTime.timestamp)
              .map {
                case Right(_) =>
                  val _ =
                    auditConversationRead(
                      "QueryMessageReadSuccess",
                      client,
                      conversationId,
                      readTime.timestamp,
                      enrolments)
                  Created(Json.toJson("read time successfully added"))
                case Left(error) =>
                  val _ =
                    auditConversationRead(
                      "QueryMessageReadFailed",
                      client,
                      conversationId,
                      readTime.timestamp,
                      enrolments)
                  handleErrors(conversationId, error, Some(client))
              }
          }
        }
  }

}
