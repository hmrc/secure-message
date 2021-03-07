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

package uk.gov.hmrc.securemessage.services

import java.text.ParseException

import cats.data.NonEmptyList
import cats.implicits._
import cats.kernel.Eq
import com.google.inject.Inject
import org.apache.commons.codec.binary.Base64
import org.joda.time.DateTime
import play.api.mvc.Result
import play.api.mvc.Results.{ BadRequest, Conflict, Created }
import uk.gov.hmrc.auth.core.{ AuthorisationException, Enrolments }
import uk.gov.hmrc.emailaddress.EmailAddress
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.securemessage.connectors.EmailConnector
import uk.gov.hmrc.securemessage.controllers.models.generic.CaseworkerMessageRequest.SystemIdentifier
import uk.gov.hmrc.securemessage.controllers.models.generic._
import uk.gov.hmrc.securemessage.models.EmailRequest
import uk.gov.hmrc.securemessage.models.core._
import uk.gov.hmrc.securemessage.repository.ConversationRepository
import uk.gov.hmrc.securemessage.services.utils.HtmlValidator

import scala.concurrent.{ ExecutionContext, Future }
/*
TODO: refactor so that service has no play dependencies, only core classes.
 */

@SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter"))
class SecureMessageService @Inject()(repo: ConversationRepository, emailConnector: EmailConnector) {

  def createConversation(conversationRequest: ConversationRequest, client: String, conversationId: String)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[Result] = {
    val conversation = conversationRequest.asConversation(client, conversationId)
    repo.insertIfUnique(conversation).map { isUnique =>
      if (isUnique) {
        messageContentCheck(conversationRequest.message) match {
          case Right(true) =>
            getEmailRequestForConversation(conversationRequest) match {
              case Left(err) => BadRequest(err)
              case Right(req) =>
                val _ = emailConnector.send(req)
                Created
            }
          case Left(value) => BadRequest(value.getMessage)
          case _           => BadRequest("Message not created")
        }
      } else {
        Conflict("Duplicate of existing conversation")
      }
    }
  }

  private def getEmailRequestForConversation(request: ConversationRequest): Either[String, EmailRequest] = {
    val emailAddresses: List[EmailAddress] = request.recipients.flatMap(r => r.customer.email.toList)
    if (emailAddresses.nonEmpty) {
      Right[String, EmailRequest](EmailRequest(emailAddresses, request.alert.templateId))
    } else {
      Left[String, EmailRequest]("No recipient email addresses provided")
    }
  }

  def getConversations(enrolment: CustomerEnrolment)(
    implicit ec: ExecutionContext): Future[List[ConversationMetadata]] = {
    val enrolmentToIdentifier = Identifier(enrolment.name, enrolment.value, Some(enrolment.key))
    repo.getConversations(enrolment).map { coreConversations =>
      coreConversations.map(conversation =>
        ConversationMetadata.coreToConversationMetadata(conversation, enrolmentToIdentifier))
    }
  }

  def getConversationsFiltered(customerEnrolments: Set[CustomerEnrolment], tags: Option[List[Tag]])(
    implicit ec: ExecutionContext): Future[List[ConversationMetadata]] =
    repo.getConversationsFiltered(customerEnrolments, tags).map { coreConversations =>
      coreConversations.map(conversation => {
        val enrolmentToIdentifiers = customerEnrolments.map(customerEnrolment =>
          Identifier(customerEnrolment.name, customerEnrolment.value, Some(customerEnrolment.key)))
        ConversationMetadata.coreToConversationMetadata(conversation, enrolmentToIdentifiers)
      })
    }

  def getConversation(client: String, conversationId: String, enrolment: CustomerEnrolment)(
    implicit ec: ExecutionContext): Future[Option[ApiConversation]] = {
    val enrolmentToIdentifier = Identifier(enrolment.name, enrolment.value, Some(enrolment.key))
    repo.getConversation(client, conversationId, enrolment).map {
      case Some(conversation) =>
        Some(ApiConversation.coreConversationToApiConversation(conversation, enrolmentToIdentifier))
      case _ => None
    }
  }

  def addCaseWorkerMessageToConversation(
    client: String,
    conversationId: String,
    messagesRequest: CaseworkerMessageRequest)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Unit] =
    repo.conversationExists(client, conversationId).flatMap { exists =>
      if (exists) {
        messageContentCheck(messagesRequest.content) match {
          case Right(true) =>
            getCaseworkerParticipant(client, conversationId, messagesRequest.sender.system.identifier).flatMap {
              case Some(participant) =>
                val message =
                  Message(participant.id, new DateTime(), messagesRequest.content, isForwarded = Some(false))
                createMessageAndSendEmail(client, conversationId, message, participant)
              case _ => Future.failed[Unit](AuthorisationException.fromString("Caseworker ID not found"))
            }
          case Left(value) => Future.failed[Unit](value)
          case _           => Future.failed[Unit](new ParseException("Message not created", 0))
        }
      } else {
        Future.failed[Unit](new IllegalArgumentException("Conversation ID not known"))
      }
    }

  def addCustomerMessageToConversation(
    client: String,
    conversationId: String,
    messagesRequest: CustomerMessageRequest,
    enrolments: Enrolments)(implicit ec: ExecutionContext): Future[Unit] =
    repo.conversationExists(client, conversationId).flatMap { exists =>
      if (exists) {
        messageContentCheck(messagesRequest.content) match {
          case Right(true) =>
            getParticipant(client, conversationId, enrolments).flatMap {
              case Some(participant) =>
                val message =
                  Message(participant.id, new DateTime(), messagesRequest.content, isForwarded = Some(false))
                repo.addMessageToConversation(client, conversationId, message).flatMap {
                  case true => Future(())
                  case _    => Future.failed[Unit](new ParseException("Message not created", 0))
                }
              case _ => Future.failed[Unit](AuthorisationException.fromString("InsufficientEnrolments"))
            }
          case Left(value) => Future.failed[Unit](value)
          case _           => Future.failed[Unit](new ParseException("Message not created", 0))
        }
      } else {
        Future.failed[Unit](new IllegalArgumentException("Conversation ID not known"))
      }
    }

  private def enrolmentMaybeParticpant(enrolments: Enrolments, participants: Participants): Option[Identifier] = {
    val participantIdentifiers = getIdentifiersFromParticipants(participants).toList
    getIdentifiersFromEnrolments(enrolments).intersect(participantIdentifiers).headOption
  }

  def updateReadTime(client: String, conversationId: String, enrolments: Enrolments, readTime: DateTime)(
    implicit ec: ExecutionContext): Future[Boolean] =
    getParticipant(client, conversationId, enrolments).flatMap {
      case Some(participant) =>
        repo.updateConversationWithReadTime(client, conversationId, participant.id, readTime)
      case _ => Future.successful(false)
    }

  private def getIdentifiersFromEnrolments(enrolments: Enrolments): List[Identifier] =
    enrolments.enrolments
      .flatMap(enr => {
        enr.identifiers.map { id =>
          Identifier(id.key, id.value, Some(enr.key))
        }
      })
      .toList

  private def getIdentifiersFromParticipants(participants: Participants): NonEmptyList[Identifier] =
    participants.participants.map(p1 => p1.identifier)

  private def getParticipant(client: String, conversationId: String, enrolments: Enrolments)(
    implicit ec: ExecutionContext): Future[Option[Participant]] =
    repo.getConversationParticipants(client, conversationId).map {
      case Some(participants) =>
        val filteredParticipants = enrolmentMaybeParticpant(enrolments, participants).map(identifier =>
          participants.participants.filter(p => p.identifier.equals(identifier)))
        filteredParticipants.flatMap(x => x.headOption)
      case _ => None
    }

  private def getCaseworkerParticipant(client: String, conversationId: String, caseworkerIdentifier: SystemIdentifier)(
    implicit ec: ExecutionContext): Future[Option[Participant]] = {
    val identifier = Identifier(caseworkerIdentifier.name, caseworkerIdentifier.value, None)
    repo.getConversationParticipants(client, conversationId).map {
      case Some(participants) =>
        participants.participants.filter(p => p.identifier.equals(identifier)).headOption.map { participant =>
          participant
        }
      case _ => None
    }
  }

  private def messageContentCheck(content: String): Either[ParseException, Boolean] =
    if (Base64.isBase64(content)) {
      if (HtmlValidator.isValidHtml(content)) {
        Right[ParseException, Boolean](true)
      } else {
        Left[ParseException, Boolean](new ParseException("Not valid HTML content", 0))
      }
    } else {
      Left[ParseException, Boolean](new ParseException("Not valid base64 content", 0))
    }

  private def createMessageAndSendEmail(
    client: String,
    conversationId: String,
    messagesRequest: Message,
    participant: Participant)(implicit ec: ExecutionContext, headerCarrier: HeaderCarrier): Future[Unit] =
    repo.getConversationParticipants(client, conversationId).flatMap {
      case Some(participants) =>
        repo.addMessageToConversation(client, conversationId, messagesRequest).flatMap {
          case true =>
            getEmailRequestForMessage(participants, participant) match {
              case Left(error)         => Future.failed[Unit](new ParseException(error, 0))
              case Right(emailRequest) => emailConnector.send(emailRequest)
            }
          case _ => Future.failed[Unit](new ParseException("Message not created", 0))
        }
      case _ => Future.failed[Unit](new ParseException("Message not created", 0))
    }

  private def getEmailRequestForMessage(
    participants: Participants,
    participant: Participant): Either[String, EmailRequest] = {
    implicit val eqParticipant: Eq[Participant] = Eq.fromUniversalEquals
    val emailAddresses: List[EmailAddress] =
      participants.participants.toList
        .filter(_ =!= participant)
        .flatMap(p => p.email.toList)
    if (emailAddresses.nonEmpty) {
      Right[String, EmailRequest](EmailRequest(emailAddresses, "nudge_email_template"))
    } else {
      Left[String, EmailRequest]("No recipient email addresses provided")
    }
  }
}
