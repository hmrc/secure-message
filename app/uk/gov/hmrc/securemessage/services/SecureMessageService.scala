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
import uk.gov.hmrc.auth.core.{ AuthorisationException, Enrolments }
import uk.gov.hmrc.securemessage.controllers.models.generic.{ ApiConversation, ConversationMetadata, CustomerEnrolment, CustomerMessageRequest }
import uk.gov.hmrc.securemessage.models.core._
import uk.gov.hmrc.securemessage.repository.ConversationRepository

import scala.concurrent.{ ExecutionContext, Future }

@SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter"))
class SecureMessageService @Inject()(conversationRepository: ConversationRepository) {

  def getConversations(enrolment: CustomerEnrolment)(
    implicit ec: ExecutionContext): Future[List[ConversationMetadata]] = {
    val enrolmentToIdentifier = Identifier(enrolment.name, enrolment.value, Some(enrolment.key))
    conversationRepository.getConversations(enrolment).map { coreConversations =>
      coreConversations.map(conversations =>
        ConversationMetadata.coreToConversationMetadata(conversations, enrolmentToIdentifier))
    }
  }

  def getConversation(client: String, conversationId: String, enrolment: CustomerEnrolment)(
    implicit ec: ExecutionContext): Future[Option[ApiConversation]] = {
    val enrolmentToIdentifier = Identifier(enrolment.name, enrolment.value, Some(enrolment.key))
    conversationRepository.getConversation(client, conversationId, enrolment).map {
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
    conversationRepository.conversationExists(client, conversationId).flatMap { exists =>
      if (exists) {
        getParticipantId(client, conversationId, enrolments).flatMap {
          case Some(id) =>
            val message =
              Message(id, new DateTime(), List.empty[Reader], messagesRequest.content, isForwarded = Some(false))
            conversationRepository.addMessageToConversation(client, conversationId, message)
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
    conversationRepository.getConversationParticipants(client, conversationId).map {
      case Some(participants) =>
        val filteredParticipants = enrolmentMaybeParticpant(enrolments, participants).map(identifier =>
          participants.participants.filter(p => p.identifier.equals(identifier)))
        val firstMatching = filteredParticipants.flatMap(x => x.headOption)
        firstMatching.map(f => f.id)
      case _ => None
    }

}
