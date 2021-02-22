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

import cats.data.NonEmptyList
import cats.implicits._
import cats.kernel.Eq
import org.joda.time.DateTime
import play.api.libs.json.{ Json, Writes }
import uk.gov.hmrc.securemessage.models.core.{ Identifier, _ }
import uk.gov.hmrc.securemessage.models.utils.NonEmptyListOps.nonEmptyListWrites

final case class ApiConversation(
  client: String,
  conversationId: String,
  status: ConversationStatus,
  tags: Option[Map[String, String]],
  subject: String,
  language: Language,
  messages: NonEmptyList[ApiMessage]
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
      // you're the sender
      case (Some(participantSender), Some(participantReader)) if participantSender.id === participantReader.id =>
        ApiMessage(None, Some(message.created), None, None, message.content)
      // not the sender, but am the first reader
      case (Some(participantSender), Some(participantReader))
          if isFirstReader(participantReader, message, coreConversation) =>
        ApiMessage(
          Some(SenderInformation(participantSender.name, message.created)),
          None,
          readTime(coreConversation, message),
          None,
          message.content
        )
      // not the sender, and not the first reader
      case (Some(participantSender), Some(_)) =>
        ApiMessage(
          Some(SenderInformation(participantSender.name, message.created)),
          None,
          readTime(coreConversation, message),
          getFirstReaderDetails(coreConversation, message),
          message.content
        )
      case (_, _) => ApiMessage(None, None, None, None, message.content)
    }
  }

  private def findFirstReaderDetails(message: Message, coreConversation: Conversation): Option[(DateTime, Int)] = {
    val messageCreated = message.created.getMillis
    getReadTimesWithId(coreConversation.participants)
      .filter(_._2 =!= message.senderId)
      .sortBy(_._1.getMillis)
      .filter(_._1.getMillis > messageCreated)
      .headOption
  }

  def isFirstReader(readerParticipant: Participant, message: Message, coreConversation: Conversation): Boolean =
    findFirstReaderDetails(message, coreConversation) match {
      case Some(details) => details._2 === readerParticipant.id
      case _             => false
    }

  private def getFirstReaderDetails(coreConversation: Conversation, message: Message): Option[FirstReaderInformation] =
    findFirstReaderDetails(message, coreConversation).flatMap { details =>
      findParticipantViaId(coreConversation, details._2).map { participantDetails =>
        FirstReaderInformation(participantDetails.name, details._1)
      }
    }

  private def readTime(coreConversation: Conversation, message: Message): Option[DateTime] =
    findFirstReaderDetails(message, coreConversation).map { details =>
      details._1
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

  def getReadTimesWithId(
    participants: List[Participant]
  ): List[(DateTime, Int)] =
    participants.flatMap(part =>
      part.readTimes match {
        case Some(times) => times.map(rt => rt -> part.id)
        case _           => List()
    })

  implicit val languageWrites: Writes[Language] = Language.languageWrites
  implicit val conversationFormat: Writes[ApiConversation] =
    Json.writes[ApiConversation]
}
