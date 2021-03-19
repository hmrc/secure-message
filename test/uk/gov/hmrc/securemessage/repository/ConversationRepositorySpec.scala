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
import uk.gov.hmrc.securemessage.ConversationNotFound
import uk.gov.hmrc.securemessage.controllers.models.generic.Tag
import uk.gov.hmrc.securemessage.helpers.ConversationUtil
import uk.gov.hmrc.securemessage.models.core.{ Identifier, Message }

import scala.concurrent.ExecutionContext.Implicits.global

@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements", "org.wartremover.warts.EitherProjectionPartial"))
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
        repository.getConversationsFiltered(Set(Identifier("EORINumber", "GB1234567890", Some("HMRC-CUS-ORG"))), None))
      result.map(_.conversationId) mustBe List("234", "123")
    }

    "be returned for a single specific enrolment filter and an empty tag filter" in {
      repoSetup()
      val result = await(
        repository
          .getConversationsFiltered(Set(Identifier("EORINumber", "GB1234567890", Some("HMRC-CUS-ORG"))), Some(List())))
      result.map(_.conversationId) mustBe List("234", "123")
    }

    "be returned for more than one enrolment filter and no tag filter" in {
      repoSetup()
      val result = await(
        repository.getConversationsFiltered(
          Set(
            Identifier("EORINumber", "GB1234567890", Some("HMRC-CUS-ORG")),
            Identifier("UTR", "123456789", Some("IR-SA"))),
          None))
      result.map(_.conversationId) mustBe List("345", "234", "123")
    }

    "be returned for more than one enrolment filter and an empty tag filter" in {
      repoSetup()
      val result = await(
        repository.getConversationsFiltered(
          Set(
            Identifier("EORINumber", "GB1234567890", Some("HMRC-CUS-ORG")),
            Identifier("UTR", "123456789", Some("IR-SA"))),
          Some(List())))
      result.map(_.conversationId) mustBe List("345", "234", "123")
    }

    "none returned when a tag filter without an enrolment counterpart is provided" in {
      repoSetup()
      val result =
        await(repository.getConversationsFiltered(Set.empty, Some(List(Tag("notificationType", "CDS Exports")))))
      result mustBe Nil
    }

    "none returned when more than one tag filter without an enrolment counterpart is provided" in {
      repoSetup()

      val result = await(repository
        .getConversationsFiltered(Set.empty, Some(List(Tag("sourceId", "self-assessment"), Tag("caseId", "CT-11345")))))
      result mustBe Nil
    }

    "none returned for one enrolment and one tag filter constraint that do not match against a record" in {
      repoSetup()
      val result = await(
        repository.getConversationsFiltered(
          Set(Identifier("UTR", "345678901", Some("IR-CT"))),
          Some(List(Tag("sourceId", "self-assessment")))))
      result mustBe Nil
    }

    "be returned for one enrolment and one tag filter constraint that match a single record" in {
      repoSetup()
      val result = await(
        repository.getConversationsFiltered(
          Set(Identifier("UTR", "123456789", Some("IR-SA"))),
          Some(List(Tag("sourceId", "self-assessment")))))
      result.size mustBe 1
      result(0).conversationId mustBe "345"
    }

    "be returned for more than one enrolment and one tag filter constraint that match a single record" in {
      repoSetup()
      val result = await(
        repository.getConversationsFiltered(
          Set(
            Identifier("UTR", "123456789", Some("IR-SA")),
            Identifier("UTR", "345678901", Some("IR-CT")),
            Identifier("EORINumber", "GB1234567890", Some("HMRC-CUS-ORG")),
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
          Set(Identifier("UTR", "123456789", Some("IR-SA"))),
          Some(List(Tag("sourceId", "self-assessment"), Tag("caseId", "CT-11345")))))
      result.size mustBe 1
      result(0).conversationId mustBe "345"
    }

    "be returned for more than one enrolment and more than one tag filter constraint that match a multiple records" in {
      repoSetup()
      val result = await(
        repository.getConversationsFiltered(
          Set(
            Identifier("UTR", "123456789", Some("IR-SA")),
            Identifier("UTR", "345678901", Some("IR-CT"))
          ),
          Some(List(Tag("sourceId", "self-assessment"), Tag("caseId", "CT-11345")))
        ))
      result.map(_.conversationId) mustBe List("456", "345")
    }
  }

  "A conversation with the given conversation ID" should {
    "be returned for a participating enrolment" in {
      val conversation = ConversationUtil.getMinimalConversation("123")
      await(repository.insert(conversation))
      val result =
        await(
          repository
            .getConversation("cdcm", "123", Set(Identifier("EORINumber", "GB1234567890", Some("HMRC-CUS-ORG")))))
      result.right.get mustBe conversation
    }
  }

  "No conversation with the given conversation ID" should {
    "be returned if the enrolment is not a participant" in {
      val conversation = ConversationUtil.getMinimalConversation("123")
      await(repository.insert(conversation))
      val result = await(
        repository
          .getConversation(
            "cdcm",
            "D-80542-20201120",
            Set(Identifier("EORINumber", "GB1234567890", Some("HMRC-CUS-ORF")))))
      result mustBe Left(ConversationNotFound(
        "Conversation not found for client: cdcm, conversationId: D-80542-20201120, identifier: Some(Identifier(EORINumber,GB1234567890,Some(HMRC-CUS-ORF)))"))
    }
  }

  "Adding a message to conversation" must {
    "increase the message array size" in {
      val aConversationId = "D-80542-20201120"
      val conversation = ConversationUtil.getMinimalConversation(aConversationId)
      await(repository.insert(conversation))
      val message = Message(2, new DateTime(), "test")
      await(repository.addMessageToConversation("cdcm", aConversationId, message))
      await(repository.addMessageToConversation("cdcm", aConversationId, message))
      val updated = await(repository
        .getConversation("cdcm", aConversationId, Set(Identifier("EORINumber", "GB1234567890", Some("HMRC-CUS-ORG")))))
      val result = updated.right.get
      result.messages.size mustBe 3
    }
  }

  "Update conversation with new read time" should {
    "return true when a conversation has been successfully update with a new read time" in {
      val conversation = ConversationUtil.getFullConversation("123", "HMRC-CUS-ORG", "EORINumber", "GB1234567890")
      await(repository.insert(conversation))
      val result = await(repository.addReadTime("cdcm", "D-80542-20201120", 2, DateTime.now))
      result mustBe Right(())
    }
  }

  override protected def afterEach(): Unit = dropTestCollection("conversation")
}
