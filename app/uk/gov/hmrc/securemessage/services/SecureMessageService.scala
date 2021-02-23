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

import cats.data.NonEmptyList
import com.google.inject.Inject
import org.joda.time.DateTime
import play.api.mvc.Result
import play.api.mvc.Results.{ BadRequest, Conflict, Created }
import uk.gov.hmrc.auth.core.{ AuthorisationException, Enrolments }
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.securemessage.connectors.EmailConnector
import uk.gov.hmrc.securemessage.controllers.models.generic._
import uk.gov.hmrc.securemessage.models.EmailRequest
import uk.gov.hmrc.securemessage.models.core._
import uk.gov.hmrc.securemessage.repository.ConversationRepository

import scala.concurrent.{ ExecutionContext, Future }

@SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter"))
class SecureMessageService @Inject()(repo: ConversationRepository, emailConnector: EmailConnector) {

  def createConversation(conversationRequest: ConversationRequest, client: String, conversationId: String)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[Result] = {
    val conversation = conversationRequest.asConversation(client, conversationId)
    repo.insertIfUnique(conversation).map { isUnique =>
      if (isUnique) {
        getEmailRequest(conversationRequest) match {
          case Left(err) => BadRequest(err)
          case Right(req) =>
            val _ = emailConnector.send(req)
            Created
        }
      } else {
        Conflict("Duplicate of existing conversation")
      }
    }
  }

  private def getEmailRequest(request: ConversationRequest): Either[String, EmailRequest] = {
    val emailAddresses = request.recipients.flatMap(r => r.customer.email.toList)
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
      coreConversations.map(conversations =>
        ConversationMetadata.coreToConversationMetadata(conversations, enrolmentToIdentifier))
    }
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

  def addMessageToConversation(
    client: String,
    conversationId: String,
    messagesRequest: CustomerMessageRequest,
    enrolments: Enrolments)(implicit ec: ExecutionContext): Future[Unit] =
    repo.conversationExists(client, conversationId).flatMap { exists =>
      if (exists) {
        getParticipantId(client, conversationId, enrolments).flatMap {
          case Some(id) =>
            val message =
              Message(id, new DateTime(), messagesRequest.content, isForwarded = Some(false))
            repo.addMessageToConversation(client, conversationId, message)
          case _ => Future.failed[Unit](AuthorisationException.fromString("InsufficientEnrolments"))
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
    getParticipantId(client, conversationId, enrolments).flatMap {
      case Some(id) =>
        repo.updateConversationWithReadTime(client, conversationId, id, readTime)
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

  private def getParticipantId(client: String, conversationId: String, enrolments: Enrolments)(
    implicit ec: ExecutionContext): Future[Option[Int]] =
    repo.getConversationParticipants(client, conversationId).map {
      case Some(participants) =>
        val filteredParticipants = enrolmentMaybeParticpant(enrolments, participants).map(identifier =>
          participants.participants.filter(p => p.identifier.equals(identifier)))
        val firstMatching = filteredParticipants.flatMap(x => x.headOption)
        firstMatching.map(f => f.id)
      case _ => None
    }

}
