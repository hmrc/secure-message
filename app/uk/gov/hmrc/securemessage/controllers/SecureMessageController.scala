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
import play.api.i18n.I18nSupport
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.{ Action, AnyContent, ControllerComponents }
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.EventTypes
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.securemessage._
import uk.gov.hmrc.securemessage.controllers.model.ClientName
import uk.gov.hmrc.securemessage.controllers.model.cdcm.write._
import uk.gov.hmrc.securemessage.controllers.model.common.CustomerEnrolment
import uk.gov.hmrc.securemessage.controllers.model.common.read._
import uk.gov.hmrc.securemessage.controllers.model.common.write._
import uk.gov.hmrc.securemessage.controllers.utils.EnrolmentHelper._
import uk.gov.hmrc.securemessage.controllers.utils.QueryStringValidation
import uk.gov.hmrc.securemessage.services.{ Auditing, ErrorHandling, SecureMessageService }
import uk.gov.hmrc.time.DateTimeUtils

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.control.NonFatal

@SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter"))
class SecureMessageController @Inject()(
  cc: ControllerComponents,
  val authConnector: AuthConnector,
  override val auditConnector: AuditConnector,
  secureMessageService: SecureMessageService,
  dataTimeUtils: DateTimeUtils)(implicit ec: ExecutionContext)
    extends BackendController(cc) with AuthorisedFunctions with QueryStringValidation with I18nSupport
    with ErrorHandling with Auditing {

  def createConversation(client: ClientName, conversationId: String): Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      client match {
        case ClientName.CDCM =>
          withJsonBody[CdcmConversation] { cdcmConversation =>
            val conversation =
              cdcmConversation.asConversationWithCreatedDate(client.entryName, conversationId, dataTimeUtils.now)
            secureMessageService
              .createConversation(conversation)
              .map {
                case Right(_) =>
                  val _ = auditCreateConversation(EventTypes.Succeeded, conversation, "Conversation Created")
                  Created
                case Left(error: SecureMessageError) =>
                  val _ = auditCreateConversation(EventTypes.Failed, conversation, error.message)
                  handleErrors(ClientName.withName(conversation.client), conversation.id, error)
              }
              .recover {
                case NonFatal(error) => handleErrors(ClientName.withName(conversation.client), conversation.id, error)
              }
          }
        case _ => Future(handleErrors(client, conversationId, InvalidRequest(s"Not supported client: $client")))
      }

  }

  def addCaseworkerMessage(client: ClientName, conversationId: String): Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      withJsonBody[CaseworkerMessage] { caseworkerMessageRequest =>
        secureMessageService
          .addCaseWorkerMessageToConversation(client.entryName, conversationId, caseworkerMessageRequest)
          .map {
            case Right(_) =>
              val _ = auditCaseworkerReply(EventTypes.Succeeded, client, conversationId, caseworkerMessageRequest)
              Created(Json.toJson(s"Created case worker message for client $client and conversationId $conversationId"))
            case Left(error) =>
              val _ = auditCaseworkerReply(EventTypes.Failed, client, conversationId, caseworkerMessageRequest)
              handleErrors(client, conversationId, error)
          }
      }.recover {
        case NonFatal(error) => handleErrors(client, conversationId, error)
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
                val _ = auditCustomerReply(EventTypes.Succeeded, client, conversationId, customerMessageRequest)
                Created(Json.toJson(s"Created customer message for client $client and conversationId $conversationId"))
              case Left(error) =>
                val _ = auditCustomerReply(EventTypes.Failed, client, conversationId, customerMessageRequest)
                handleErrors(client, conversationId, error)
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

  def getConversationContent(client: ClientName, conversationId: String): Action[AnyContent] = Action.async {
    implicit request =>
      authorised()
        .retrieve(Retrievals.allEnrolments) { authEnrolments =>
          if (authEnrolments.enrolments.isEmpty) {
            Future.successful(Unauthorized(Json.toJson("No enrolment found")))
          } else {
            val customerEnrolments = mapToCustomerEnrolments(authEnrolments)
            secureMessageService
              .getConversation(client.entryName, conversationId, customerEnrolments)
              .map {
                case Right(apiConversation) => Ok(Json.toJson(apiConversation))
                case _                      => NotFound(Json.toJson("No conversation found"))
              }
          }
        }
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
                    auditConversationRead(EventTypes.Succeeded, client, conversationId, readTime.timestamp, enrolments)
                  Created(Json.toJson("read time successfully added"))
                case Left(error) =>
                  val _ =
                    auditConversationRead(EventTypes.Failed, client, conversationId, readTime.timestamp, enrolments)
                  handleErrors(client, conversationId, error)
              }
          }
        }
  }

  private def mapToCustomerEnrolments(authEnrolments: Enrolments): Set[CustomerEnrolment] =
    authEnrolments.enrolments
      .flatMap(e => e.identifiers.map(i => CustomerEnrolment(e.key, i.key, i.value)))

}
