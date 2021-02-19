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

package uk.gov.hmrc.securemessage.repository

import org.joda.time.DateTime
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import play.api.test.Helpers._
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.securemessage.controllers.models.generic.CustomerEnrolment
import uk.gov.hmrc.securemessage.helpers.ConversationUtil
import uk.gov.hmrc.securemessage.models.core.{ Message, Reader }

import scala.concurrent.ExecutionContext.Implicits.global

@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
class ConversationRepositorySpec extends PlaySpec with MongoSpecSupport with BeforeAndAfterEach {

  private val repository = new ConversationRepository()

  "A full conversation" should {
    "be inserted into the repository successfully" in {
      val conversation = ConversationUtil.getFullConversation("123")
      await(repository.insert(conversation))
      val count = await(repository.count)
      count mustEqual 1

    }
  }

  "A minimal conversation" should {
    "be inserted into the repository successfully" in {
      val conversation = ConversationUtil.getMinimalConversation("123")
      await(repository.insert(conversation))
      val count = await(repository.count)
      count mustEqual 1

    }
  }

  "A list of two conversations" should {
    "be returned for a participating enrolment" in {
      val conversation1 = ConversationUtil.getMinimalConversation("123")
      await(repository.insert(conversation1))
      val conversation2 = ConversationUtil.getMinimalConversation("234")
      await(repository.insert(conversation2))
      val result = await(repository.getConversations(CustomerEnrolment("HMRC-CUS-ORG", "EORINumber", "GB1234567890")))
      result.size mustBe 2
    }
  }

  "No conversations" should {
    "be returned if the enrolment is not participating in any" in {
      val result = await(repository.getConversations(CustomerEnrolment("HMRC-CUS-ORG", "EORINumber", "GB1234567890")))
      result.size mustBe 0
    }
  }

  "A conversation with the given conversation ID" should {
    "be returned for a participating enrolment" in {
      val conversation = ConversationUtil.getMinimalConversation("123")
      await(repository.insert(conversation))
      val result =
        await(
          repository.getConversation("cdcm", "123", CustomerEnrolment("HMRC-CUS-ORG", "EORINumber", "GB1234567890")))
      result.size mustBe 1
    }
  }

  "No conversation with the given conversation ID" should {
    "be returned if the enrolment is not a participant" in {
      val conversation = ConversationUtil.getMinimalConversation("123")
      await(repository.insert(conversation))
      val result = await(
        repository
          .getConversation("cdcm", "D-80542-20201120", CustomerEnrolment("HMRC-CUS-ORF", "EORINumber", "GB1234567890")))
      result.size mustBe 0
    }
  }

  "Adding a message to conversation" must {
    "increase the message array size" in {
      val aConversationId = "D-80542-20201120"
      val conversation = ConversationUtil.getMinimalConversation(aConversationId)
      await(repository.insert(conversation))
      val message = Message(2, new DateTime(), List.empty[Reader], "test", isForwarded = Some(false))
      await(repository.addMessageToConversation("cdcm", aConversationId, message))
      val updated = await(
        repository
          .getConversation("cdcm", aConversationId, CustomerEnrolment("HMRC-CUS-ORG", "EORINumber", "GB1234567890")))
      updated match {
        case Some(c) => c.messages.size mustBe 2
        case _       => fail("No conversation found")
      }
    }
  }

  "Getting the participants from a conversation" must {
    "return the correct number of participants" in {
      val aConversationId = "D-80542-20201120"
      val conversation = ConversationUtil.getMinimalConversation(aConversationId)
      await(repository.insert(conversation))
      val participants = await(repository.getConversationParticipants("cdcm", aConversationId))
      participants match {
        case Some(p) => p.participants.size mustBe 2
        case _       => fail("No participants found")
      }
    }
  }

  "Checking if a conversation ID exists" must {
    "return true when it does" in {
      val aConversationId = "D-80542-20201120"
      val conversation = ConversationUtil.getMinimalConversation(aConversationId)
      await(repository.insert(conversation))
      val exists = await(repository.conversationExists("cdcm", aConversationId))
      exists mustBe true
    }
    "return false when it does not" in {
      val aConversationId = "D-80542-20201120"
      val exists = await(repository.conversationExists("cdcm", aConversationId))
      exists mustBe false
    }
  }

  override protected def afterEach(): Unit = dropTestCollection("conversation")
}
