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
import reactivemongo.api.commands.WriteResult
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.securemessage.controllers.models.generic.{ CustomerEnrolment, Tag }
import uk.gov.hmrc.securemessage.helpers.ConversationUtil
import uk.gov.hmrc.securemessage.models.core.Message

import scala.concurrent.ExecutionContext.Implicits.global

@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
class ConversationRepositorySpec extends PlaySpec with MongoSpecSupport with BeforeAndAfterEach {

  private val repository = new ConversationRepository()

  "A full conversation" should {
    "be inserted into the repository successfully" in {
      val conversation = ConversationUtil.getFullConversation("123", "HMRC-CUS-ORG", "EORINumber", "GB1234567890")
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

  "A list of filtered conversations" should {

    val conversation1 = ConversationUtil.getFullConversation("123", "HMRC-CUS-ORG", "EORINumber", "GB1234567890")
    val conversation2 = ConversationUtil.getFullConversation("234", "HMRC-CUS-ORG", "EORINumber", "GB1234567890")
    val conversation3 =
      ConversationUtil
        .getFullConversation("345", "IR-SA", "UTR", "123456789", Some(Map("sourceId" -> "self-assessment")))
    val conversation4 =
      ConversationUtil.getFullConversation("456", "IR-CT", "UTR", "345678901", Some(Map("caseId" -> "CT-11345")))

    def repoSetup(): WriteResult = {
      await(repository.insert(conversation1))
      await(repository.insert(conversation2))
      await(repository.insert(conversation3))
      await(repository.insert(conversation4))
    }

    "be returned for a single specific enrolment filter and no tag filter" in {
      repoSetup()
      val result = await(
        repository.getConversationsFiltered(Set(CustomerEnrolment("HMRC-CUS-ORG", "EORINumber", "GB1234567890")), None))
      result.map(_.conversationId) mustBe List("234", "123")
    }

    "be returned for a single specific enrolment filter and an empty tag filter" in {
      repoSetup()
      val result = await(
        repository
          .getConversationsFiltered(Set(CustomerEnrolment("HMRC-CUS-ORG", "EORINumber", "GB1234567890")), Some(List())))
      result.map(_.conversationId) mustBe List("234", "123")
    }

    "be returned for more than one enrolment filter and no tag filter" in {
      repoSetup()
      val result = await(
        repository.getConversationsFiltered(
          Set(
            CustomerEnrolment("HMRC-CUS-ORG", "EORINumber", "GB1234567890"),
            CustomerEnrolment("IR-SA", "UTR", "123456789")),
          None))
      result.map(_.conversationId) mustBe List("345", "234", "123")
    }

    "be returned for more than one enrolment filter and an empty tag filter" in {
      repoSetup()
      val result = await(
        repository.getConversationsFiltered(
          Set(
            CustomerEnrolment("HMRC-CUS-ORG", "EORINumber", "GB1234567890"),
            CustomerEnrolment("IR-SA", "UTR", "123456789")),
          Some(List())))
      result.map(_.conversationId) mustBe List("345", "234", "123")
    }

    "not be returned and instead a database exception raised when a tag filter without an enrolment counterpart is provided" in {
      repoSetup()
      an[reactivemongo.core.errors.DetailedDatabaseException] should be thrownBy {
        await(repository.getConversationsFiltered(Set.empty, Some(List(Tag("notificationType", "CDS Exports")))))
      }
    }

    "not be returned and instead a database exception raised when more than one tag filter without an enrolment counterpart is provided" in {
      repoSetup()
      an[reactivemongo.core.errors.DetailedDatabaseException] should be thrownBy {
        await(
          repository
            .getConversationsFiltered(
              Set.empty,
              Some(List(Tag("sourceId", "self-assessment"), Tag("caseId", "CT-11345")))))
      }
    }

    "none returned for one enrolment and one tag filter constraint that do not match against a record" in {
      repoSetup()
      val result = await(
        repository.getConversationsFiltered(
          Set(CustomerEnrolment("IR-CT", "UTR", "345678901")),
          Some(List(Tag("sourceId", "self-assessment")))))
      result.size mustBe 0
    }

    "be returned for one enrolment and one tag filter constraint that match a single record" in {
      repoSetup()
      val result = await(
        repository.getConversationsFiltered(
          Set(CustomerEnrolment("IR-SA", "UTR", "123456789")),
          Some(List(Tag("sourceId", "self-assessment")))))
      result.size mustBe 1
      result(0).conversationId mustBe "345"
    }

    "be returned for more than one enrolment and one tag filter constraint that match a single record" in {
      repoSetup()
      val result = await(
        repository.getConversationsFiltered(
          Set(
            CustomerEnrolment("IR-SA", "UTR", "123456789"),
            CustomerEnrolment("IR-CT", "UTR", "345678901"),
            CustomerEnrolment("HMRC-CUS-ORG", "EORINumber", "GB1234567890")
          ),
          Some(List(Tag("caseId", "CT-11345")))
        ))
      result.size mustBe 1
      result(0).conversationId mustBe "456"
    }

    "be returned for one enrolment and more than one tag filter constraint that match a single record" in {
      repoSetup()
      val result = await(
        repository.getConversationsFiltered(
          Set(CustomerEnrolment("IR-SA", "UTR", "123456789")),
          Some(List(Tag("sourceId", "self-assessment"), Tag("caseId", "CT-11345")))))
      result.size mustBe 1
      result(0).conversationId mustBe "345"
    }

    "be returned for more than one enrolment and more than one tag filter constraint that match a multiple records" in {
      repoSetup()
      val result = await(
        repository.getConversationsFiltered(
          Set(
            CustomerEnrolment("IR-SA", "UTR", "123456789"),
            CustomerEnrolment("IR-CT", "UTR", "345678901")
          ),
          Some(List(Tag("sourceId", "self-assessment"), Tag("caseId", "CT-11345")))
        ))
      result.map(_.conversationId) mustBe List("456", "345")
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
      val message = Message(2, new DateTime(), "test", isForwarded = Some(false))
      await(repository.addMessageToConversation("cdcm", aConversationId, message))
      await(repository.addMessageToConversation("cdcm", aConversationId, message))
      val updated = await(
        repository
          .getConversation("cdcm", aConversationId, CustomerEnrolment("HMRC-CUS-ORG", "EORINumber", "GB1234567890")))
      updated match {
        case Some(c) => c.messages.size mustBe 3
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

  "Update conversation with new read time" should {
    "return true when a conversation has been successfully update with a new read time" in {
      val conversation = ConversationUtil.getFullConversation("123", "HMRC-CUS-ORG", "EORINumber", "GB1234567890")
      await(repository.insert(conversation))
      val result = await(repository.updateConversationWithReadTime("cdcm", "D-80542-20201120", 2, DateTime.now))
      result mustBe true
    }
  }

  override protected def afterEach(): Unit = dropTestCollection("conversation")
}
