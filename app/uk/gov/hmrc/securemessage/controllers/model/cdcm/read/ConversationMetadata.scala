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

import java.time.Instant
import play.api.i18n.Messages
import play.api.libs.json.{ Json, OFormat }
import uk.gov.hmrc.securemessage.controllers.model.ApiFormats
import uk.gov.hmrc.securemessage.models.core._

final case class ConversationMetadata(
  client: String,
  conversationId: String,
  subject: String,
  issueDate: Instant,
  senderName: Option[String],
  unreadMessages: Boolean,
  count: Int
)

object ConversationMetadata extends ApiFormats {

  def coreToConversationMetadata(coreConversation: Conversation, reader: Set[Identifier])(implicit
    messages: Messages
  ): ConversationMetadata =
    ConversationMetadata(
      coreConversation.client,
      coreConversation.id,
      coreConversation.subject,
      coreConversation.issueDate,
      findLatestMessageName(coreConversation),
      coreConversation.unreadMessagesFor(reader).nonEmpty,
      coreConversation.messages.size
    )

  private def findLatestMessageName(coreConversation: Conversation)(implicit messages: Messages): Option[String] =
    coreConversation.latestParticipant.flatMap { participant =>
      participant.name match {
        case Some(name) => Some(name)
        case _ =>
          participant.participantType match {
            case ParticipantType.Customer => Some(messages("conversation.inbox.default.customer.sender"))
            case ParticipantType.System   => Some(messages("conversation.inbox.default.system.sender"))
          }
      }
    }

  implicit val conversationMetadataFormat: OFormat[ConversationMetadata] =
    Json.format[ConversationMetadata]
}
