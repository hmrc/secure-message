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
import org.mongodb.scala.bson.ObjectId
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{ JsObject, JsSuccess, JsValue, Json }
import uk.gov.hmrc.common.message.model.Language
import uk.gov.hmrc.securemessage.helpers.{ ConversationUtil, Resources }
import uk.gov.hmrc.securemessage.models.core.Conversation.*
import uk.gov.hmrc.securemessage.helpers.DateTimeHelper.*

import java.time.format.DateTimeFormatter
import java.time.{ Instant, ZoneId, ZoneOffset, ZonedDateTime }

class ConversationSpec extends PlaySpec with ConversationTestData with OrderingDefinitions {

  "Validating a conversation" must {
    val objectID = new ObjectId()
    "be successful when optional fields are present" in {
      val conversationJson =
        Resources.readJson("model/core/conversation.json").as[JsObject] + ("_id" -> Json.toJson(objectID))
      conversationJson.validate[Conversation] mustBe JsSuccess(
        ConversationUtil
          .getFullConversation(objectID, "D-80542-20201120", "HMRC-CUS-ORG", "EORINumber", "GB1234567890")
      )
    }

    "be successful when optional fields are not present" in {
      val conversationJson: JsValue = Resources
        .readJson("model/core/conversation-minimal.json")
        .as[JsObject] + ("_id" -> Json.toJson(objectID))
      conversationJson.validate[Conversation] mustBe JsSuccess(
        ConversationUtil.getMinimalConversation("D-80542-20201120", objectID)
      )
    }
  }

  "unreadMessages" must {
    "return all messages if reader didn't read any" in {
      val messages = unreadMessagesWith(2)
      val unreadMessagesCustomer = customerWith().copy(readTimes = None)
      val conversation = conversationWith(reader = unreadMessagesCustomer, messages = messages)
      conversation.unreadMessagesFor(Set(unreadMessagesCustomer.identifier)) mustBe messages
    }

    "return only unread messages if reader read some" in {
      val unreadMessages = unreadMessagesWith(2)
      val readMessages = readMessagesWith(2)
      val conversation = conversationWith(messages = unreadMessages ++ readMessages)
      conversation.unreadMessagesFor(reader = Set(customerWith().identifier)) mustBe unreadMessages
    }

    "return empty if latest read message is after latest message" in {
      val conversation = conversationWith(messages = readMessagesWith(3))
      conversation.unreadMessagesFor(Set(customerWith().identifier)) mustBe empty
    }
  }

  "issueDate" must {
    "be the latest message created date" in {
      val messages = unreadMessagesWith(count = 3).sortBy(_.created)(dateTimeDescending)
      val conversation = conversationWith(messages = messages)
      conversation.issueDate mustBe plusDays(dateTime, 3)
    }
  }

  "latestMessage" must {
    "be the one with the latest created date" in {
      val messages = unreadMessagesWith(count = 3).sortBy(_.created)(dateTimeDescending)
      val conversation = conversationWith(messages = messages)
      conversation.latestMessage mustBe messages.head
    }
  }

  "latestParticipant" must {
    "be the one who sent the last message" in {
      val systemMessages = unreadMessagesWith(count = 2)
      val customer = customerWith()
      val customerMessage = messageWith(sender = customer, created = plusDays(dateTime, 3))
      val conversation = conversationWith(messages = customerMessage :: systemMessages)
      conversation.latestParticipant mustBe Some(customer)
    }
  }

}

trait ConversationTestData {
  val dtf: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS").withZone(ZoneId.from(ZoneOffset.UTC))
  val dateTime: Instant = ZonedDateTime.parse("2020-11-10T15:00:01.000", dtf).toInstant

  val system: Participant =
    Participant(1, ParticipantType.System, Identifier("system", "value", None), None, None, None, None)

  def customerWith(readTime: Instant = dateTime): Participant =
    Participant(
      2,
      ParticipantType.Customer,
      Identifier("customer", "value", None),
      None,
      None,
      None,
      Some(List(readTime))
    )

  def messageWith(sender: Participant = system, created: Instant = dateTime): ConversationMessage =
    ConversationMessage(None, sender.id, created, "", None)

  def readMessagesWith(
    count: Int,
    sender: Participant = system,
    dateTime: Instant = dateTime
  ): List[ConversationMessage] =
    (1 to count).map(i => messageWith(sender = sender, created = minusDays(dateTime, i))).toList

  def unreadMessagesWith(
    count: Int,
    sender: Participant = system,
    dateTime: Instant = dateTime
  ): List[ConversationMessage] =
    (1 to count).map(i => messageWith(sender = sender, created = plusDays(dateTime, i))).toList

  def conversationWith(
    reader: Participant = customerWith(),
    sender: Participant = system,
    messages: List[ConversationMessage] = List(messageWith())
  ): Conversation =
    Conversation(
      new ObjectId(),
      "",
      "",
      ConversationStatus.Open,
      None,
      "",
      Language.English,
      List(sender, reader),
      NonEmptyList.fromList(messages).head,
      uk.gov.hmrc.securemessage.models.core.Alert("", None)
    )
}
