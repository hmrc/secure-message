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
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.securemessage.controllers.model.common.read.FilterTag
import uk.gov.hmrc.securemessage.helpers.ConversationUtil
import uk.gov.hmrc.securemessage.models.core.{ Conversation, Identifier, Message }
import uk.gov.hmrc.securemessage.{ ConversationNotFound, StoreError }

import scala.collection.immutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

//TODO: remove PlaySpec from all tests except controllers
//TODO: reuse test data as variables, do not have same string twice anywhere
@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements", "org.wartremover.warts.EitherProjectionPartial"))
class ConversationRepositorySpec extends PlaySpec with MongoSpecSupport with BeforeAndAfterEach {

  val conversation1: Conversation =
    ConversationUtil.getFullConversation(BSONObjectID.generate, "123", "HMRC-CUS-ORG", "EORINumber", "GB1234567890")
  val conversation2: Conversation =
    ConversationUtil.getFullConversation(BSONObjectID.generate, "234", "HMRC-CUS-ORG", "EORINumber", "GB1234567890")
  val conversation3: Conversation =
    ConversationUtil.getFullConversation(
      BSONObjectID.generate,
      "345",
      "IR-SA",
      "UTR",
      "123456789",
      Some(Map("sourceId" -> "self-assessment")))
  val conversation4: Conversation =
    ConversationUtil
      .getFullConversation(BSONObjectID.generate, "456", "IR-CT", "UTR", "345678901", Some(Map("caseId" -> "CT-11345")))
  val allConversations = Seq(conversation1, conversation2, conversation3, conversation4)

  //TODO: group test by their function name
  "A full conversation" should {
    "be inserted into the repository successfully" in new TestContext(
      conversations = Seq()
    ) {
      val conversation: Conversation =
        ConversationUtil.getFullConversation(BSONObjectID.generate, "123", "HMRC-CUS-ORG", "EORINumber", "GB1234567890")
      await(repository.insert(conversation))
      val count: Int = await(repository.count)
      count mustEqual 1
    }
  }

  "A minimal conversation" should {
    "be inserted into the repository successfully" in new TestContext(
      conversations = Seq()
    ) {
      val conversation: Conversation = ConversationUtil.getMinimalConversation(id = "123")
      await(repository.insert(conversation))
      val count: Int = await(repository.count)
      count mustEqual 1

    }
  }

  "A list of filtered conversations" should {

    "be returned for a single specific enrolment filter and no tag filter" in new TestContext(
      conversations = allConversations
    ) {
      val result: immutable.Seq[Conversation] = await(
        repository.getConversationsFiltered(Set(Identifier("EORINumber", "GB1234567890", Some("HMRC-CUS-ORG"))), None))
      result.map(_.id) must contain theSameElementsAs List("234", "123")
    }

    "be returned for a single specific enrolment filter and an empty tag filter" in new TestContext(
      conversations = allConversations
    ) {
      val result: immutable.Seq[Conversation] = await(
        repository
          .getConversationsFiltered(Set(Identifier("EORINumber", "GB1234567890", Some("HMRC-CUS-ORG"))), Some(List())))
      result.map(_.id) must contain theSameElementsAs List("234", "123")
    }

    "be returned for more than one enrolment filter and no tag filter" in new TestContext(
      conversations = allConversations
    ) {
      val result: immutable.Seq[Conversation] = await(
        repository.getConversationsFiltered(
          Set(
            Identifier("EORINumber", "GB1234567890", Some("HMRC-CUS-ORG")),
            Identifier("UTR", "123456789", Some("IR-SA"))),
          None))
      result.map(_.id) must contain theSameElementsAs List("345", "234", "123")
    }

    "be returned for more than one enrolment filter and an empty tag filter" in new TestContext(
      conversations = allConversations
    ) {
      val result: immutable.Seq[Conversation] = await(
        repository.getConversationsFiltered(
          Set(
            Identifier("EORINumber", "GB1234567890", Some("HMRC-CUS-ORG")),
            Identifier("UTR", "123456789", Some("IR-SA"))),
          Some(List())))
      result.map(_.id) must contain theSameElementsAs List("345", "234", "123")
    }

    "none returned when a tag filter without an enrolment counterpart is provided" in new TestContext(
      conversations = allConversations
    ) {
      val result: immutable.Seq[Conversation] =
        await(repository.getConversationsFiltered(Set.empty, Some(List(FilterTag("notificationType", "CDS Exports")))))
      result mustBe Nil
    }

    "none returned when more than one tag filter without an enrolment counterpart is provided" in new TestContext(
      conversations = allConversations
    ) {
      val result: immutable.Seq[Conversation] = await(
        repository
          .getConversationsFiltered(
            Set.empty,
            Some(List(FilterTag("sourceId", "self-assessment"), FilterTag("caseId", "CT-11345")))))
      result mustBe Nil
    }

    "none returned for one enrolment and one tag filter constraint that do not match against a record" in new TestContext(
      conversations = allConversations
    ) {
      val result: immutable.Seq[Conversation] = await(
        repository.getConversationsFiltered(
          Set(Identifier("UTR", "345678901", Some("IR-CT"))),
          Some(List(FilterTag("sourceId", "self-assessment")))))
      result mustBe Nil
    }

    "be returned for one enrolment and one tag filter constraint that match a single record" in new TestContext(
      conversations = allConversations
    ) {
      val result: immutable.Seq[Conversation] = await(
        repository.getConversationsFiltered(
          Set(Identifier("UTR", "123456789", Some("IR-SA"))),
          Some(List(FilterTag("sourceId", "self-assessment")))))
      result.map(_.id) mustBe Seq("345")
    }

    "be returned for more than one enrolment and one tag filter constraint that match a single record" in new TestContext(
      conversations = allConversations
    ) {
      val result: immutable.Seq[Conversation] = await(
        repository.getConversationsFiltered(
          Set(
            Identifier("UTR", "123456789", Some("IR-SA")),
            Identifier("UTR", "345678901", Some("IR-CT")),
            Identifier("EORINumber", "GB1234567890", Some("HMRC-CUS-ORG")),
          ),
          Some(List(FilterTag("caseId", "CT-11345")))
        ))
      result.map(_.id) mustBe Seq("456")
    }

    "be returned for one enrolment and more than one tag filter constraint that match a single record" in new TestContext(
      conversations = allConversations
    ) {
      val result: immutable.Seq[Conversation] = await(
        repository.getConversationsFiltered(
          Set(Identifier("UTR", "123456789", Some("IR-SA"))),
          Some(List(FilterTag("sourceId", "self-assessment"), FilterTag("caseId", "CT-11345")))))
      result.map(_.id) mustBe Seq("345")
    }

    "be returned for more than one enrolment and more than one tag filter constraint that match a multiple records" in new TestContext(
      conversations = allConversations
    ) {
      val result: immutable.Seq[Conversation] = await(
        repository.getConversationsFiltered(
          Set(
            Identifier("UTR", "123456789", Some("IR-SA")),
            Identifier("UTR", "345678901", Some("IR-CT"))
          ),
          Some(List(FilterTag("sourceId", "self-assessment"), FilterTag("caseId", "CT-11345")))
        ))
      result.map(_.id) must contain theSameElementsAs List("456", "345")
    }
  }

  "A conversation with the given conversation ID" should {
    val conversation = ConversationUtil.getMinimalConversation("123")
    "be returned for a participating enrolment" in new TestContext(
      conversations = Seq(conversation)
    ) {
      val result: Either[ConversationNotFound, Conversation] =
        await(
          repository
            .getConversation(conversation.client, conversation.id, conversation.participants.map(_.identifier).toSet))
      result.right.get mustBe conversation
    }
  }

  "No conversation with the given conversation ID" should {
    val conversation = ConversationUtil.getMinimalConversation(id = "123")
    "be returned if the enrolment is not a participant" in new TestContext(
      conversations = Seq(conversation)
    ) {
      private val modifierParticipantEnrolments: Set[Identifier] =
        conversation.participants.map(id => id.identifier.copy(value = id.identifier.value + "1")).toSet
      val result: Either[ConversationNotFound, Conversation] =
        await(repository.getConversation(conversation.client, conversation.id, modifierParticipantEnrolments))
      result mustBe Left(ConversationNotFound(s"Conversation not found for identifier: $modifierParticipantEnrolments"))
    }
  }
  "Adding a message to conversation" must {
    val conversation: Conversation = ConversationUtil.getMinimalConversation(id = "123")
    "increase the message array size" in new TestContext(
      conversations = Seq(conversation)
    ) {
      val message: Message = Message(2, new DateTime(), "test")
      await(repository.addMessageToConversation(conversation.client, conversation.id, message))
      await(repository.addMessageToConversation(conversation.client, conversation.id, message))
      val updated: Either[ConversationNotFound, Conversation] = await(
        repository
          .getConversation(
            conversation.client,
            conversation.id,
            Set(Identifier("EORINumber", "GB1234567890", Some("HMRC-CUS-ORG")))))
      val result: Conversation = updated.right.get
      result.messages.size mustBe 3
    }
  }

  "Update conversation with new read time" should {
    val conversation =
      ConversationUtil.getFullConversation(BSONObjectID.generate, "123", "HMRC-CUS-ORG", "EORINumber", "GB1234567890")
    "return true when a conversation has been successfully update with a new read time" in new TestContext(
      conversations = Seq(conversation)
    ) {
      val result: Either[StoreError, Unit] =
        await(repository.addReadTime(conversation.client, conversation.id, 2, DateTime.now))
      result mustBe Right(())
    }
  }

  class TestContext(conversations: Seq[Conversation]) {
    val repository: ConversationRepository = new ConversationRepository()
    await(Future.sequence(conversations.map(repository.insert)))
  }

  override protected def afterEach(): Unit = dropTestCollection("conversation")
}
