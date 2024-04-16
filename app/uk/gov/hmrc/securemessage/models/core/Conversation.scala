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

package uk.gov.hmrc.securemessage.models.core
import cats.data.NonEmptyList
import java.time.Instant
import org.mongodb.scala.bson.ObjectId
import play.api.libs.json.{ Format, Json, OFormat }
import uk.gov.hmrc.mongo.play.json.formats.MongoFormats
import uk.gov.hmrc.securemessage.ParticipantNotFound
import uk.gov.hmrc.securemessage.models.utils.NonEmptyListOps

import scala.annotation.nowarn

final case class Conversation(
  _id: ObjectId = new ObjectId(),
  client: String,
  id: String,
  status: ConversationStatus,
  tags: Option[Map[String, String]],
  subject: String,
  language: Language,
  participants: List[Participant],
  messages: NonEmptyList[ConversationMessage],
  alert: Alert
) extends Message with OrderingDefinitions {

  override def issueDate: Instant = latestMessage.created
  override def readTime: Option[Instant] = None // Used for other message types

  def latestMessage: ConversationMessage = messages.toList.maxBy(_.created)(dateTimeAscending)

  def latestParticipant: Option[Participant] = participants.find(_.id == latestMessage.senderId)

  def findParticipant(identifiers: Set[Identifier]): Option[Participant] =
    participants.find(p => identifiers.contains(p.identifier))

  @nowarn("msg=parameter value lastRead in anonymous function is never used") // false positive
  def unreadMessagesFor(reader: Set[Identifier]): List[ConversationMessage] = {
    val maybeParticipant = findParticipant(reader)
    val maybeLastRead = maybeParticipant.flatMap(_.lastReadTime.orElse(Some(Instant.ofEpochMilli(0))))
    for {
      participant <- maybeParticipant.toList
      message     <- messages.toList
      lastRead    <- maybeLastRead if participant.id != message.senderId && lastRead.isBefore(message.created)
    } yield message
  }

}
object Conversation extends NonEmptyListOps {
  implicit val objectIdFormat: Format[ObjectId] = MongoFormats.objectIdFormat
  implicit val conversationFormat: OFormat[Conversation] = Json.format[Conversation]

  implicit class ConversationExtensions(conversation: Conversation) {
    def participantWith(identifiers: Set[Identifier]): Either[ParticipantNotFound, Participant] =
      conversation.findParticipant(identifiers) match {
        case Some(participant) => Right(participant)
        case None =>
          Left(
            ParticipantNotFound(
              s"No participant found for client: ${conversation.client}, conversationId: ${conversation.id}, identifiers: $identifiers"
            )
          )
      }
  }
}
