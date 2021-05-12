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

package uk.gov.hmrc.securemessage.models.core
import cats.data.NonEmptyList
import com.github.ghik.silencer.silent
import org.joda.time.DateTime
import play.api.libs.json.{ Format, Json, OFormat }
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.securemessage.models.utils.NonEmptyListOps

final case class Conversation(
  _id: BSONObjectID = BSONObjectID.generate(),
  client: String,
  id: String,
  status: ConversationStatus,
  tags: Option[Map[String, String]],
  subject: String,
  language: Language,
  participants: List[Participant],
  messages: NonEmptyList[ConversationMessage],
  alert: Alert)
    extends Message with OrderingDefinitions {

  override def issueDate: DateTime = latestMessage.created

  def latestMessage: ConversationMessage = messages.toList.maxBy(_.created)(dateTimeAscending)

  def latestParticipant: Option[Participant] = participants.find(_.id == latestMessage.senderId)

  @silent def unreadMessagesFor(reader: Set[Identifier]): List[ConversationMessage] = {
    val maybeParticipant = participants.find(p => reader.contains(p.identifier))
    val maybeLastRead = maybeParticipant.flatMap(_.lastReadTime.orElse(Some(new DateTime(0))))
    for {
      participant <- maybeParticipant.toList
      message     <- messages.toList
      lastRead    <- maybeLastRead
      if participant.id != message.senderId && lastRead.isBefore(message.created)
    } yield message
  }

}
object Conversation extends NonEmptyListOps {
  implicit val objectIdFormat: Format[BSONObjectID] = ReactiveMongoFormats.objectIdFormats
  implicit val conversationFormat: OFormat[Conversation] = Json.format[Conversation]
}
