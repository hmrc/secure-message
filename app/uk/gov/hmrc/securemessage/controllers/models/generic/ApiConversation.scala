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

package uk.gov.hmrc.securemessage.controllers.models.generic

import cats.implicits._
import cats.kernel.Eq
import org.joda.time.DateTime
import play.api.libs.json.{ Json, Writes }
import uk.gov.hmrc.securemessage.models.core.{ Identifier, _ }

final case class ApiConversation(
  client: String,
  conversationId: String,
  status: ConversationStatus,
  tags: Option[Map[String, String]],
  subject: String,
  language: Language,
  messages: List[ApiMessage]
)

object ApiConversation {

  def coreConversationToApiConversation(conversation: Conversation, identifier: Identifier): ApiConversation =
    ApiConversation(
      client = conversation.client,
      conversationId = conversation.conversationId,
      status = conversation.status,
      tags = conversation.tags,
      subject = conversation.subject,
      language = conversation.language,
      messages = conversation.messages.map(message => convertToApiMessage(conversation, message, identifier))
    )

  private def convertToApiMessage(
    coreConversation: Conversation,
    message: Message,
    identifier: Identifier): ApiMessage = {
    val sender: Option[Participant] = findParticipantViaId(coreConversation, message.senderId)
    val reader: Option[Participant] = findParticipantViaIdentifier(coreConversation, identifier)
    (sender, reader) match {
      case (Some(participantSender), Some(participantReader)) if participantSender.id === participantReader.id =>
        ApiMessage(None, Some(message.created), None, None, message.content)
      case (Some(participantSender), Some(participantReader)) if firstReader(message, participantReader) =>
        ApiMessage(
          Some(SenderInformation(participantSender.name, message.created)),
          None,
          isMessageRead(coreConversation, message, identifier),
          None,
          message.content
        )
      case (Some(participantSender), Some(_)) =>
        ApiMessage(
          Some(SenderInformation(participantSender.name, message.created)),
          None,
          isMessageRead(coreConversation, message, identifier),
          notFirstReader(coreConversation, message),
          message.content
        )
      case (_, _) => ApiMessage(None, None, None, None, message.content)
    }
  }

  private def firstReader(message: Message, participant: Participant): Boolean =
    message.readBy.sortWith(_.readDate.getMillis > _.readDate.getMillis).headOption match {
      case Some(reader) => reader.id === participant.id
      case None         => true
    }

  private def notFirstReader(coreConversation: Conversation, message: Message): Option[FirstReaderInformation] =
    message.readBy.sortWith(_.readDate.getMillis > _.readDate.getMillis).headOption.flatMap { reader =>
      findParticipantViaId(coreConversation, reader.id).map { participant =>
        FirstReaderInformation(participant.name, reader.readDate)
      }
    }

  private def isMessageRead(
    coreConversation: Conversation,
    message: Message,
    identifier: Identifier): Option[DateTime] =
    checkAlreadyReadMessage(coreConversation, message, identifier).map { reader =>
      reader.readDate
    }

  private def checkAlreadyReadMessage(
    coreConversation: Conversation,
    message: Message,
    identifier: Identifier): Option[Reader] =
    findParticipantViaIdentifier(coreConversation, identifier).flatMap { participant =>
      message.readBy.find(_.id === participant.id)
    }

  private def findParticipantViaId(coreConversation: Conversation, id: Int): Option[Participant] =
    coreConversation.participants
      .find(_.id === id)

  private def findParticipantViaIdentifier(
    coreConversation: Conversation,
    identifier: Identifier): Option[Participant] = {
    implicit val eqFoo: Eq[Identifier] = Eq.fromUniversalEquals
    coreConversation.participants
      .find(_.identifier === identifier)
  }

  implicit val languageWrites: Writes[Language] = Language.languageWrites

  implicit val conversationFormat: Writes[ApiConversation] =
    Json.writes[ApiConversation]
}