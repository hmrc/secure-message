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
import play.api.libs.json.JodaReads.jodaDateReads
import play.api.libs.json.JodaWrites.jodaDateWrites
import play.api.libs.json.{ Format, Json, OFormat }
import uk.gov.hmrc.securemessage.models.core.{ Conversation, Identifier, Message }

final case class ConversationDetails(
  conversationId: String,
  subject: String,
  issueDate: Option[DateTime],
  senderName: Option[String],
  unreadMessages: Boolean,
  count: Int)

object ConversationDetails {

  def coreToConversationDetails(coreConversation: Conversation, identifier: Identifier): ConversationDetails = {
    val messageCount = coreConversation.messages.size
    ConversationDetails(
      coreConversation.conversationId,
      coreConversation.subject,
      findLatestMessageDate(coreConversation),
      findLatestMessageName(coreConversation),
      findAnyUnreadMessages(coreConversation, identifier),
      messageCount
    )
  }

  private def findLatestMessage(coreConversation: Conversation): Option[Message] =
    coreConversation.messages.sortWith(_.created.getMillis > _.created.getMillis).headOption match {
      case Some(message) => Some(message)
      case _             => None
    }

  private def findLatestMessageDate(coreConversation: Conversation): Option[DateTime] =
    findLatestMessage(coreConversation) match {
      case Some(ms) => Some(ms.created)
      case _        => None
    }

  private def findLatestMessageName(coreConversation: Conversation): Option[String] =
    findLatestMessage(coreConversation) match {
      case Some(message) => coreConversation.participants.find(_.id === message.senderId).flatMap(_.name)
      case _             => None
    }

  def findAnyUnreadMessages(coreConversation: Conversation, identifier: Identifier): Boolean = {
    implicit val eqFoo: Eq[Identifier] = Eq.fromUniversalEquals
    coreConversation.participants
      .find(_.identifier === identifier)
      .fold(false)(part => findAnyUnreadMessages(part.id, coreConversation))
  }

  private def findAnyUnreadMessages(id: Int, coreConversation: Conversation): Boolean = {
    val listOfMessages = coreConversation.messages.map(ms => ms)
    val listOfReader = listOfMessages.flatMap(ms => ms.readBy)
    val listOfId = listOfReader.map(reader => reader.id)
    val listOfMatchingIds = listOfId.count(int => int === id)
    listOfMatchingIds =!= listOfMessages.size
  }

  private val dateFormatString = "yyyy-MM-dd'T'HH:mm:ss.SSSZ"

  implicit val dateFormat: Format[DateTime] =
    Format(jodaDateReads(dateFormatString), jodaDateWrites(dateFormatString))

  implicit val conversationDetailsFormat: OFormat[ConversationDetails] =
    Json.format[ConversationDetails]
}
