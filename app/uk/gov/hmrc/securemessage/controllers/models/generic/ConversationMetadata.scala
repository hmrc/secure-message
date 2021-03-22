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
import play.api.i18n.Messages
import play.api.libs.json.JodaReads.jodaDateReads
import play.api.libs.json.JodaWrites.jodaDateWrites
import play.api.libs.json.{ Format, Json, OFormat }
import uk.gov.hmrc.securemessage.models.core.{ Conversation, Identifier, Message, Participant, ParticipantType }

final case class ConversationMetadata(
  client: String,
  conversationId: String,
  subject: String,
  issueDate: DateTime,
  senderName: Option[String],
  unreadMessages: Boolean,
  count: Int)

object ConversationMetadata {

  @SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter"))
  def coreToConversationMetadata(coreConversation: Conversation, identifier: Identifier)(
    implicit messages: Messages): ConversationMetadata = {
    val messageCount = coreConversation.messages.size
    ConversationMetadata(
      coreConversation.client,
      coreConversation.id,
      coreConversation.subject,
      findLatestMessageDate(coreConversation),
      findLatestMessageName(coreConversation),
      anyUnreadMessages(coreConversation, identifier),
      messageCount
    )
  }

  @SuppressWarnings(Array("org.wartremover.warts.Overloading", "org.wartremover.warts.ImplicitParameter"))
  def coreToConversationMetadata(coreConversation: Conversation, identifiers: Set[Identifier])(
    implicit messages: Messages): ConversationMetadata = {
    val messageCount = coreConversation.messages.size
    ConversationMetadata(
      coreConversation.client,
      coreConversation.id,
      coreConversation.subject,
      findLatestMessageDate(coreConversation),
      findLatestMessageName(coreConversation),
      anyUnreadMessages(coreConversation, identifiers),
      messageCount
    )
  }

  private def findLatestMessage(coreConversation: Conversation): Message =
    coreConversation.messages.sortBy(_.created.getMillis).reverse.head

  private def isLatestMessageBySender(coreConversation: Conversation, participant: Participant) =
    coreConversation.messages.sortBy(_.created.getMillis).reverse.head.senderId === participant.id

  private def findLatestMessageDate(coreConversation: Conversation): DateTime =
    findLatestMessage(coreConversation).created

  @SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter"))
  private def findLatestMessageName(coreConversation: Conversation)(implicit messages: Messages): Option[String] =
    findLatestParticipant(coreConversation).flatMap { participant =>
      participant.name match {
        case Some(name) => Some(name)
        case _ =>
          participant.participantType match {
            case ParticipantType.Customer => Some(messages("conversation.inbox.default.customer.sender"))
            case ParticipantType.System   => Some(messages("conversation.inbox.default.system.sender"))
          }
      }
    }

  private def findLatestParticipant(coreConversation: Conversation): Option[Participant] = {
    val latestMessageSenderId = findLatestMessage(coreConversation).senderId
    coreConversation.participants.find(_.id === latestMessageSenderId)
  }

  private def anyUnreadMessages(coreConversation: Conversation, identifier: Identifier): Boolean = {
    implicit val eqFoo: Eq[Identifier] = Eq.fromUniversalEquals
    coreConversation.participants
      .find(_.identifier === identifier)
      .fold(false)(participant => findUnreadMessagesByParticipant(participant, coreConversation))
  }

  @SuppressWarnings(Array("org.wartremover.warts.Overloading"))
  private[generic] def anyUnreadMessages[generic](
    coreConversation: Conversation,
    identifiers: Set[Identifier]): Boolean =
    coreConversation.participants
      .find(participant => identifiers.contains(participant.identifier))
      .fold(false)(participant => findUnreadMessagesByParticipant(participant, coreConversation))

  private def findUnreadMessagesByParticipant(participant: Participant, coreConversation: Conversation): Boolean =
    participant.readTimes match {
      case _ if isLatestMessageBySender(coreConversation, participant) => false
      case Some(times) =>
        times
          .sortBy(_.getMillis)
          .reverse
          .headOption
          .fold(true)(_.getMillis < findLatestMessage(coreConversation).created.getMillis)
      case _ => true
    }

  private val dateFormatString = "yyyy-MM-dd'T'HH:mm:ss.SSSZ"

  implicit val dateFormat: Format[DateTime] =
    Format(jodaDateReads(dateFormatString), jodaDateWrites(dateFormatString))

  implicit val conversationMetadataFormat: OFormat[ConversationMetadata] =
    Json.format[ConversationMetadata]
}
