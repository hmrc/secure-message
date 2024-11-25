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

package uk.gov.hmrc.securemessage.controllers

import cats.data.*
import cats.implicits.*
import org.mongodb.scala.bson.ObjectId
import play.api.Logging
import play.api.i18n.I18nSupport
import play.api.libs.json.*
import play.api.mvc.{ Action, AnyContent, ControllerComponents, Request, Result }
import uk.gov.hmrc.auth.core.*
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.securemessage.*
import uk.gov.hmrc.securemessage.controllers.model.cdcm.write.*
import uk.gov.hmrc.securemessage.controllers.model.common.write.*
import uk.gov.hmrc.securemessage.controllers.model.{ ApiMessage, ClientName }
import uk.gov.hmrc.securemessage.controllers.utils.IdCoder.EncodedId
import uk.gov.hmrc.securemessage.controllers.utils.{ IdCoder, MessageSchemaValidator, QueryStringValidation }
import uk.gov.hmrc.securemessage.handlers.{ MessageBroker, MessageReadRequest }
import uk.gov.hmrc.securemessage.models.core.Language.English
import uk.gov.hmrc.securemessage.models.core.{ CustomerEnrolment, FilterTag, Language, MessageFilter, MessageRequestWrapper, Reference }
import uk.gov.hmrc.securemessage.models.v4.SecureMessage
import uk.gov.hmrc.securemessage.services.{ ImplicitClassesExtensions, SecureMessageServiceImpl }
import uk.gov.hmrc.securemessage.utils.DateTimeUtils

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.control.NonFatal
import scala.util.{ Failure, Success, Try }

class SecureMessageController @Inject() (
  cc: ControllerComponents,
  val authConnector: AuthConnector,
  override val auditConnector: AuditConnector,
  secureMessageService: SecureMessageServiceImpl,
  messageBroker: MessageBroker,
  dataTimeUtils: DateTimeUtils
)(implicit ec: ExecutionContext)
    extends BackendController(cc) with AuthorisedFunctions with QueryStringValidation with I18nSupport
    with ErrorHandling with Auditing with Logging with ImplicitClassesExtensions with MessageSchemaValidator {

  def createConversation(client: ClientName, conversationId: String): Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      val randomId = UUID.randomUUID().toString
      val maybeReference = xRequestIdExists(request)
      withJsonBody[CdcmConversation] { cdcmConversation =>
        val conversation =
          cdcmConversation.asConversationWithCreatedDate(
            client.entryName,
            conversationId,
            dataTimeUtils.now,
            randomId,
            maybeReference
          )
        val res = secureMessageService
          .createConversation(conversation)
          .map {
            case Right(_) =>
              Created
            case Left(error: SecureMessageError) =>
              auditCreateConversation(
                "EmailAddressLookUpFailed",
                conversation,
                "Conversation Created",
                randomId,
                maybeReference
              )
              handleErrors(conversation.id, error, Some(ClientName.withName(conversation.client)))
          }
        auditCreateConversation(
          "CreateQueryConversationSuccess",
          conversation,
          "Conversation Created",
          randomId,
          maybeReference
        )
        res
      }
  }

  def addCaseworkerMessage(client: ClientName, conversationId: String): Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      val randomId = UUID.randomUUID().toString
      val maybeReference = xRequestIdExists(request)
      withJsonBody[CaseworkerMessage] { caseworkerMessageRequest =>
        secureMessageService
          .addCaseWorkerMessageToConversation(
            client.entryName,
            conversationId,
            caseworkerMessageRequest,
            randomId,
            maybeReference
          )
          .map {
            case Right(_) =>
              val _ = auditCaseworkerReply(
                "CaseWorkerReplyToConversationSuccess",
                client,
                conversationId,
                caseworkerMessageRequest,
                randomId,
                maybeReference
              )
              Created(Json.toJson(s"Created case worker message for client $client and conversationId $conversationId"))
            case Left(error) =>
              val _ = auditCaseworkerReply(
                "CaseWorkerReplyToConversationFailed",
                client,
                conversationId,
                caseworkerMessageRequest,
                randomId,
                maybeReference
              )
              handleErrors(conversationId, error, Some(client))
          }
      }
  }

  def addCustomerMessage(encodedId: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    val randomId = UUID.randomUUID().toString
    val maybeReference = xRequestIdExists(request)
    val message = for {
      messageTypeAndId <- EitherT(Future.successful(IdCoder.decodeId(encodedId)))
      enrolments       <- EitherT(getEnrolments())
      message          <- EitherT(Future.successful(parseAs[CustomerMessage]()))
      _ <- EitherT(
             secureMessageService.addCustomerMessage(messageTypeAndId._2, message, enrolments, randomId, maybeReference)
           )
    } yield message

    message.value map {
      case Right(customerMessage) =>
        customerReplyToConversationSuccess(customerMessage, encodedId, randomId, maybeReference)
      case Left(secureMessageError) =>
        customerReplyToConversationFailed(secureMessageError, encodedId, randomId, maybeReference)
    }
  }

  private def customerReplyToConversationSuccess(
    customerMessage: CustomerMessage,
    encodedId: EncodedId,
    randomId: String,
    maybeReference: Option[Reference]
  )(implicit headerCarrier: HeaderCarrier): Result = {
    auditCustomerReply("CustomerReplyToConversationSuccess", encodedId, Some(customerMessage), randomId, maybeReference)
    Created(Json.toJson(s"Created customer message for encodedId: $encodedId"))
  }

  private def customerReplyToConversationFailed(
    secureMessageError: SecureMessageError,
    encodedId: EncodedId,
    randomId: String,
    maybeReference: Option[Reference]
  )(implicit headerCarrier: HeaderCarrier): Result = {
    auditCustomerReply("CustomerReplyToConversationFailed", encodedId, None, randomId, maybeReference)
    handleErrors(encodedId, secureMessageError)
  }

  private def parseAs[T]()(implicit
    request: Request[JsValue],
    m: Manifest[T],
    reads: Reads[T]
  ): Either[InvalidRequest, T] =
    Try(request.body.validate[T]) match {
      case Success(JsSuccess(payload, _)) => Right(payload)
      case Success(JsError(errs)) => Left(InvalidRequest(s"Invalid ${m.runtimeClass.getSimpleName} payload: $errs"))
      case Failure(e)             => Left(InvalidRequest(s"Could not parse body due to ${e.getMessage}", Some(e)))
    }

  def getMessages(
    enrolmentKey: Option[List[String]],
    enrolment: Option[List[CustomerEnrolment]],
    tag: Option[List[FilterTag]],
    messageFilter: Option[MessageFilter] = None,
    language: Option[Language] = None
  ): Action[AnyContent] =
    Action.async { implicit request =>
      logger.warn(s"getMessages for the language $language")
      val requestWrapper =
        MessageRequestWrapper(enrolmentKey, enrolment, tag, messageFilter.getOrElse(new MessageFilter()))
      logger.warn(s"Request Wrapper = $requestWrapper")
      validateQueryParameters(request.queryString) match {
        case Left(e) =>
          logger.warn(s"Invalid Request ${request.queryString}")
          Future.successful(BadRequest(Json.toJson(e.getMessage)))
        case Right(value) =>
          logger.warn(s"Valid Request $value - Params: ${request.queryString}")
          messageBroker.messageRetriever(value).fetch(requestWrapper, language.getOrElse(English)).map(Ok(_))
      }
    }

  def getMessagesCount(
    enrolmentKeys: Option[List[String]],
    customerEnrolments: Option[List[CustomerEnrolment]],
    tags: Option[List[FilterTag]],
    messageFilter: Option[MessageFilter] = None
  ): Action[AnyContent] =
    Action.async { implicit request =>
      val requestWrapper =
        MessageRequestWrapper(enrolmentKeys, customerEnrolments, tags, messageFilter.getOrElse(new MessageFilter()))
      validateQueryParameters(request.queryString) match {
        case Left(e) =>
          logger.warn(s"Invalid Request ${request.queryString}")
          Future.successful(BadRequest(Json.toJson(e.getMessage)))
        case Right(value) =>
          logger.warn(s"Valid Request $value - Params: ${request.queryString}")
          messageBroker.messageRetriever(value).messageCount(requestWrapper).map(Ok(_))
      }
    }

  def getMessage(encodedId: String, language: Option[Language] = None): Action[AnyContent] = Action.async {
    implicit request =>
      implicit val lang: Language = language.getOrElse(English)
      val message: EitherT[Future, SecureMessageError, (ApiMessage, Enrolments)] = for {
        messageRequestTuple <- EitherT(Future.successful(IdCoder.decodeId(encodedId)))
        enrolments          <- EitherT(getEnrolments())
        message <- EitherT(
                     messageBroker
                       .messageRetriever(messageRequestTuple._3)
                       .getMessage(MessageReadRequest(messageRequestTuple._1, enrolments, messageRequestTuple._2))
                   )
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

  private def getEnrolments()(implicit request: HeaderCarrier): Future[Either[UserNotAuthorised, Enrolments]] =
    authorised()
      .retrieve(Retrievals.allEnrolments) { authEnrolments =>
        if (authEnrolments.enrolments.isEmpty) {
          Future.successful(Left(UserNotAuthorised("No enrolment found")))
        } else {
          Future.successful(Right(authEnrolments))
        }
      }

  private def xRequestIdExists(request: Request[_]): Option[Reference] = {
    val xRequest = request.headers.get("X-Request-ID").getOrElse("")
    xRequest match {
      case "" => Some(Reference("no X-Request-ID", ""))
      case xRequest =>
        Some(Reference("X-Request-ID", xRequest))
    }
  }

  def createMessage(): Action[AnyContent] = Action.async { implicit request =>
    request.body.asJson.fold[Future[Result]] {
      val errMsg = "Payload is not JSON"
      logger.error(s"$errMsg. Request: ${request.id}")
      Future.successful(BadRequest(Json.obj("error" -> errMsg)))
    } { json =>
      isValidJson(json) match {
        case Right(error) =>
          logger.warn(s"Could not validate or parse the request: $error")
          Future.successful(BadRequest(Json.obj("error" -> error)))
        case _ =>
          logger.debug(s"Request received for V4 ${json.toString} ")
          secureMessageService.createSecureMessage(json.as[SecureMessage]).recover {
            case e: MessageValidationException =>
              InternalServerError(Json.obj("reason" -> s"${e.getMessage}"))
            case e: Throwable =>
              InternalServerError(Json.obj("reason" -> s"Unable to create the message: ${e.getMessage}"))
          }
      }
    }
  }

  def getContentBy(id: ObjectId): Action[AnyContent] = Action.async { implicit request =>
    secureMessageService.getContentBy(id).map {
      case Some(content) => Ok(content)
      case None =>
        logger.warn(s"""Content for message with id: ${id.toString} is empty""")
        NotFound
    }
  }

  def setReadTime(id: ObjectId): Action[AnyContent] = Action.async { implicit request =>
    messageBroker.default
      .findAndSetReadTime(id)
      .flatMap {
        case Left(e) =>
          logger.error(s"Unable to set secure message read time ${e.message}")
          Future.successful(InternalServerError)
        case Right(Some(message)) =>
          logger.debug(s"Secure Message is Read $id")
          auditMessageReadStatus(message)
          Future.successful(Ok)
        case Right(None) => Future.successful(InternalServerError(s"failed to set read time for: $id"))
      }
      .recoverWith { case NonFatal(e) =>
        logger.error(s"Failed to set read time for: $id due to ${e.getMessage}")
        Future.successful(InternalServerError(s"Failed to set read time for: $id due to ${e.getMessage}"))
      }
  }
}
