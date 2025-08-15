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

package uk.gov.hmrc.securemessage.controllers.model.cdcm.read

import cats.data.NonEmptyList
import cats.implicits.*

import java.time.Instant
import play.api.libs.json.{ Format, Json, Writes }
import uk.gov.hmrc.common.message.model.Language
import uk.gov.hmrc.securemessage.controllers.model.ApiMessage
import uk.gov.hmrc.securemessage.models.core.{ Identifier, * }
import uk.gov.hmrc.securemessage.models.utils.NonEmptyListOps.nonEmptyListFormat

final case class ApiConversation(
  client: String,
  conversationId: String,
  status: ConversationStatus,
  tags: Option[Map[String, String]],
  subject: String,
  language: Language,
  messages: NonEmptyList[ApiConversationMessage]
) extends ApiMessage

object ApiConversation {

  def fromCore(conversation: Conversation, identifiers: Set[Identifier]): ApiConversation =
    ApiConversation(
      client = conversation.client,
      conversationId = conversation.id,
      status = conversation.status,
      tags = conversation.tags,
      subject = conversation.subject,
      language = conversation.language,
      messages = conversation.messages.map(message => convertToApiMessage(conversation, message, identifiers))
    )

  private def isSender(sender: Option[Participant], reader: Option[Participant]) =
    for {
      s <- sender
      r <- reader
    } yield s.id === r.id

  private def convertToApiMessage(
    coreConversation: Conversation,
    message: ConversationMessage,
    identifiers: Set[Identifier]
  ): ApiConversationMessage = {
    val senderPossibly: Option[Participant] = findParticipantViaId(coreConversation, message.senderId)
    val readerPossibly: Option[Participant] = findParticipantViaIdentifiers(coreConversation, identifiers)
    val firstReaderPossibly = firstReaderInformation(coreConversation, message)
    val self = isSender(senderPossibly, readerPossibly).getOrElse(false)

    (senderPossibly, firstReaderPossibly, self) match {
      case (Some(sender), Some(_), true) =>
        ApiConversationMessage(Some(SenderInformation(sender.name, message.created, self)), None, message.content)
      case (Some(sender), Some(_), false) =>
        ApiConversationMessage(
          Some(SenderInformation(sender.name, message.created, self)),
          firstReaderPossibly,
          message.content
        )
      case (Some(sender), _, _) =>
        ApiConversationMessage(Some(SenderInformation(sender.name, message.created, self)), None, message.content)
      case (_, _, _) => ApiConversationMessage(None, None, message.content)
    }
  }

  private def findFirstReaderDetails(
    message: ConversationMessage,
    coreConversation: Conversation
  ): Option[(Instant, Int)] = {
    val messageCreated = message.created.toEpochMilli
    getReadTimesWithId(coreConversation.participants)
      .filter(_._2 =!= message.senderId)
      .filter(_._1.toEpochMilli > messageCreated)
      .sortBy(_._1.toEpochMilli)
      .headOption
  }

  private def firstReaderInformation(
    coreConversation: Conversation,
    message: ConversationMessage
  ): Option[FirstReaderInformation] =
    findFirstReaderDetails(message, coreConversation).flatMap { details =>
      findParticipantViaId(coreConversation, details._2).map { participantDetails =>
        FirstReaderInformation(participantDetails.name, details._1)
      }
    }

  private def findParticipantViaId(coreConversation: Conversation, id: Int): Option[Participant] =
    coreConversation.participants
      .find(_.id === id)

  private def findParticipantViaIdentifiers(
    coreConversation: Conversation,
    identifiers: Set[Identifier]
  ): Option[Participant] =
    coreConversation.participants
      .find(p => identifiers.contains(p.identifier))

  def getReadTimesWithId(
    participants: List[Participant]
  ): List[(Instant, Int)] =
    participants.flatMap(part =>
      part.readTimes match {
        case Some(times) => times.map(rt => rt -> part.id)
        case _           => List()
      }
    )

  implicit val conversationFormat: Format[ApiConversation] =
    Json.format[ApiConversation]
}
