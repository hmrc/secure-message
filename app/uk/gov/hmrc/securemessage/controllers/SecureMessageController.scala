/*
 * Copyright 2022 HM Revenue & Customs
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

import cats.data._
import cats.implicits._
import org.mongodb.scala.bson.ObjectId
import play.api.i18n.I18nSupport
import play.api.libs.json._
import play.api.mvc.{ Action, AnyContent, ControllerComponents, Request }
import play.api.{ Logger, Logging }
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.securemessage._
import uk.gov.hmrc.securemessage.controllers.model.MessageType.{ Conversation, Letter }
import uk.gov.hmrc.securemessage.controllers.model.cdcm.write._
import uk.gov.hmrc.securemessage.controllers.model.common.read.MessageMetadata
import uk.gov.hmrc.securemessage.controllers.model.common.write._
import uk.gov.hmrc.securemessage.controllers.model.{ ApiMessage, ClientName, MessageType }
import uk.gov.hmrc.securemessage.controllers.utils.IdCoder.DecodedId
import uk.gov.hmrc.securemessage.controllers.utils.{ IdCoder, QueryStringValidation }
import uk.gov.hmrc.securemessage.models.core.{ CustomerEnrolment, FilterTag, Filters }
import uk.gov.hmrc.securemessage.services.{ CustomerMessageCacheService, ImplicitClassesExtensions, SecureMessageService }
import uk.gov.hmrc.time.DateTimeUtils

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

class SecureMessageController @Inject()(
  cc: ControllerComponents,
  val authConnector: AuthConnector,
  override val auditConnector: AuditConnector,
  secureMessageService: SecureMessageService,
  dataTimeUtils: DateTimeUtils,
  customerMessageCacheService: CustomerMessageCacheService)(implicit ec: ExecutionContext)
    extends BackendController(cc) with AuthorisedFunctions with QueryStringValidation with I18nSupport
    with ErrorHandling with Auditing with Logging with ImplicitClassesExtensions {

  override val logger = Logger(getClass.getName)
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

  def addCustomerMessage(encodedId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    val randomId: DecodedId = UUID.randomUUID().toString
    val originalRequestId: String = request.headers.get("X-Request-ID").getOrElse(s"govuk-tax-$randomId")
    val result = for {
      messageTypeAndId <- EitherT(Future.successful(IdCoder.decodeId(encodedId))).leftWiden[SecureMessageError]
      enrolments       <- EitherT(getEnrolments()).leftWiden[SecureMessageError]
      message          <- EitherT(Future.successful(parseAs[CustomerMessage]())).leftWiden[SecureMessageError]
      newRequestId     <- EitherT.liftF(customerMessageCacheService.findOrCreate(originalRequestId))
      _ <- EitherT(secureMessageService.addCustomerMessage(messageTypeAndId._2, message, enrolments, newRequestId))
            .leftWiden[SecureMessageError]
    } yield (message, newRequestId)
    result.value.map {
      case Right(res) =>
        auditCustomerReply(
          "CustomerReplyToConversationSuccess",
          encodedId,
          Some(res._1),
          originalRequestId,
          Some(res._2))
        Created(Json.toJson(s"Created customer message for encodedId: $encodedId"))
      case Left(error) =>
        auditCustomerReply("CustomerReplyToConversationFailed", encodedId, None, originalRequestId, None)
        handleErrors(encodedId, error)
    }
  }

  private def parseAs[T]()(
    implicit request: Request[JsValue],
    m: Manifest[T],
    reads: Reads[T]): Either[InvalidRequest, T] =
    Try(request.body.validate[T]) match {
      case Success(JsSuccess(payload, _)) => Right(payload)
      case Success(JsError(errs))         => Left(InvalidRequest(s"Invalid ${m.runtimeClass.getSimpleName} payload: $errs"))
      case Failure(e)                     => Left(InvalidRequest(s"Could not parse body due to ${e.getMessage}", Some(e)))
    }

  def getMessages(
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
                logger.info(
                  s"[getMessages] - authEnrolments: $authEnrolments enrolmentKeys: $enrolmentKeys customerEnrolments: $customerEnrolments")
                val filters = Filters(enrolmentKeys, customerEnrolments, tags)
                secureMessageService
                  .getMessages(authEnrolments, filters)
                  .map { messagesList =>
                    val messageMetadataList: List[MessageMetadata] =
                      messagesList.map(m => MessageMetadata(m, authEnrolments))
                    Ok(Json.toJson(messageMetadataList))
                  }
              }
        }
      }
    }

  def getMessagesCount(
    enrolmentKeys: Option[List[String]],
    customerEnrolments: Option[List[CustomerEnrolment]],
    tags: Option[List[FilterTag]]): Action[AnyContent] =
    Action.async { implicit request =>
      validateQueryParameters(request.queryString, "enrolment", "enrolmentKey", "tag") match {
        case Left(e) => Future.successful(BadRequest(Json.toJson(e.getMessage)))
        case _ =>
          authorised()
            .retrieve(Retrievals.allEnrolments) { authEnrolments =>
              val filters = Filters(enrolmentKeys, customerEnrolments, tags)
              secureMessageService
                .getMessagesCount(authEnrolments, filters)
                .map { count =>
                  Ok(Json.toJson(count))
                }
            }
      }
    }

  def getMessage(encodedId: String): Action[AnyContent] = Action.async { implicit request =>
    val message: EitherT[Future, SecureMessageError, (ApiMessage, Enrolments)] = for {
      messageTypeAndId <- EitherT(Future.successful(IdCoder.decodeId(encodedId))).leftWiden[SecureMessageError]
      enrolments       <- EitherT(getEnrolments()).leftWiden[SecureMessageError]
      message          <- EitherT(retrieveMessage(messageTypeAndId._1, messageTypeAndId._2, enrolments))
    } yield (message, enrolments)
    message.value map {
      case Right((msg, enrolments)) =>
        auditMessageRead(msg, enrolments)
        Ok(Json.toJson(msg))
      case Left(error) =>
        auditMessageReadFailed(encodedId, error)
        handleErrors(encodedId, error)
    }
  }

  private def retrieveMessage(
    messageType: MessageType,
    id: DecodedId,
    authEnrolments: Enrolments): Future[Either[SecureMessageError, ApiMessage]] =
    messageType match {
      case Conversation => secureMessageService.getConversation(new ObjectId(id), authEnrolments.asCustomerEnrolments)
      case Letter       => secureMessageService.getLetter(new ObjectId(id), authEnrolments.asCustomerEnrolments)
    }

  private def getEnrolments()(implicit request: HeaderCarrier): Future[Either[UserNotAuthorised, Enrolments]] =
    authorised()
      .retrieve(Retrievals.allEnrolments) { authEnrolments =>
        if (authEnrolments.enrolments.isEmpty) {
          Future.successful(Left(UserNotAuthorised("No enrolment found")))
        } else {
          Future.successful(Right(authEnrolments))
        }
      }

}
