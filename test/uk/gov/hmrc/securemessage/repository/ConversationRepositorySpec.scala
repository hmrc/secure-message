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

package uk.gov.hmrc.securemessage.repository

import cats.data.NonEmptyList
import org.joda.time.DateTime
import org.mongodb.scala.bson.ObjectId
import org.mongodb.scala.model.Filters
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.PlaySpec
import play.api.test.Helpers._
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.securemessage.helpers.ConversationUtil
import uk.gov.hmrc.securemessage.models.core._
import uk.gov.hmrc.securemessage.{ MessageNotFound, StoreError }

import scala.collection.immutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

//TODO: remove PlaySpec from all tests except controllers
//TODO: reuse test data as variables, do not have same string twice anywhere
class ConversationRepositorySpec
    extends PlaySpec with DefaultPlayMongoRepositorySupport[Conversation] with BeforeAndAfterEach with ScalaFutures {

  override lazy val repository: ConversationRepository = new ConversationRepository(mongoComponent)

  val conversation1: Conversation =
    ConversationUtil.getFullConversation(new ObjectId(), "123", "HMRC-CUS-ORG", "EORINumber", "GB1234567890")

  val conversation2: Conversation =
    ConversationUtil.getFullConversation(new ObjectId(), "234", "HMRC-CUS-ORG", "EORINumber", "GB1234567890")
  val conversation3: Conversation =
    ConversationUtil.getFullConversation(
      new ObjectId(),
      "345",
      "IR-SA",
      "UTR",
      "123456789",
      Some(Map("sourceId" -> "self-assessment")))
  val conversation4: Conversation =
    ConversationUtil
      .getFullConversation(new ObjectId(), "456", "IR-CT", "UTR", "345678901", Some(Map("caseId" -> "CT-11345")))
  val allConversations = Seq(conversation1, conversation2, conversation3, conversation4)

  override def beforeEach(): Unit =
    await(repository.collection.deleteMany(Filters.empty()).toFuture().map(_ => ()))

  //TODO: group test by their function name
  "A full conversation" should {
    "be inserted into the repository successfully" in new TestContext(
      conversations = Seq()
    ) {
      val conversation: Conversation =
        ConversationUtil.getFullConversation(new ObjectId(), "123", "HMRC-CUS-ORG", "EORINumber", "GB1234567890")
      await(repository.insertIfUnique(conversation))
      val count = await(repository.collection.countDocuments().toFuture())
      count mustEqual 1
    }
  }

  "A minimal conversation" should {
    "be inserted into the repository successfully" in new TestContext(
      conversations = Seq()
    ) {
      val conversation: Conversation = ConversationUtil.getMinimalConversation(id = "123")
      await(repository.insertIfUnique(conversation))
      val count = await(repository.collection.countDocuments().toFuture())
      count mustEqual 1
    }
  }

  "A list of filtered conversations" should {

    "be returned for a single specific enrolment filter and no tag filter" in new TestContext(
      conversations = allConversations
    ) {
      val result: immutable.Seq[Conversation] =
        await(repository.getConversations(Set(Identifier("EORINumber", "GB1234567890", Some("HMRC-CUS-ORG"))), None))
      result.map(_.id) must contain theSameElementsAs List("234", "123")
    }

    "be returned for a single specific enrolment filter and an empty tag filter" in new TestContext(
      conversations = allConversations
    ) {
      val result: immutable.Seq[Conversation] = await(
        repository
          .getConversations(Set(Identifier("EORINumber", "GB1234567890", Some("HMRC-CUS-ORG"))), Some(List())))
      result.map(_.id) must contain theSameElementsAs List("234", "123")
    }

    "be returned for more than one enrolment filter and no tag filter" in new TestContext(
      conversations = allConversations
    ) {
      val result: immutable.Seq[Conversation] = await(
        repository.getConversations(
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
        repository.getConversations(
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
        await(repository.getConversations(Set.empty, Some(List(FilterTag("notificationType", "CDS Exports")))))
      result mustBe Nil
    }

    "none returned when more than one tag filter without an enrolment counterpart is provided" in new TestContext(
      conversations = allConversations
    ) {
      val result: immutable.Seq[Conversation] = await(
        repository
          .getConversations(
            Set.empty,
            Some(List(FilterTag("sourceId", "self-assessment"), FilterTag("caseId", "CT-11345")))))
      result mustBe Nil
    }

    "none returned for one enrolment and one tag filter constraint that do not match against a record" in new TestContext(
      conversations = allConversations
    ) {
      val result: immutable.Seq[Conversation] = await(
        repository.getConversations(
          Set(Identifier("UTR", "345678901", Some("IR-CT"))),
          Some(List(FilterTag("sourceId", "self-assessment")))))
      result mustBe Nil
    }

    "be returned for one enrolment and one tag filter constraint that match a single record" in new TestContext(
      conversations = allConversations
    ) {
      val result: immutable.Seq[Conversation] = await(
        repository.getConversations(
          Set(Identifier("UTR", "123456789", Some("IR-SA"))),
          Some(List(FilterTag("sourceId", "self-assessment")))))
      result.map(_.id) mustBe Seq("345")
    }

    "be returned for more than one enrolment and one tag filter constraint that match a single record" in new TestContext(
      conversations = allConversations
    ) {
      val result: immutable.Seq[Conversation] = await(
        repository.getConversations(
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
        repository.getConversations(
          Set(Identifier("UTR", "123456789", Some("IR-SA"))),
          Some(List(FilterTag("sourceId", "self-assessment"), FilterTag("caseId", "CT-11345")))))
      result.map(_.id) mustBe Seq("345")
    }

    "be returned for more than one enrolment and more than one tag filter constraint that match a multiple records" in new TestContext(
      conversations = allConversations
    ) {
      val result: immutable.Seq[Conversation] = await(
        repository.getConversations(
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
      val result: Either[MessageNotFound, Conversation] =
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
      val result: Either[MessageNotFound, Conversation] =
        await(repository.getConversation(conversation.client, conversation.id, modifierParticipantEnrolments))
      result mustBe Left(MessageNotFound(s"Conversation not found for identifiers: $modifierParticipantEnrolments"))
    }
  }

  "Adding a message to conversation" must {
    val conversation: Conversation = ConversationUtil.getMinimalConversation(id = "123")
    "increase the message array size" in new TestContext(
      conversations = Seq(conversation)
    ) {
      val message1: ConversationMessage = ConversationMessage(None, 2, new DateTime(), "test", None)
      val message2: ConversationMessage = ConversationMessage(None, 3, new DateTime(), "test", None)
      await(repository.addMessageToConversation(conversation.client, conversation.id, message1))
      await(repository.addMessageToConversation(conversation.client, conversation.id, message2))
      val updated: Either[MessageNotFound, Conversation] = await(
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
      ConversationUtil.getFullConversation(new ObjectId(), "123", "HMRC-CUS-ORG", "EORINumber", "GB1234567890")
    "return true when a conversation has been successfully update with a new read time" in new TestContext(
      conversations = Seq(conversation)
    ) {
      val result: Either[StoreError, Unit] =
        await(repository.addReadTime(conversation.client, conversation.id, 2, DateTime.now))
      result mustBe Right(())
    }
  }

  "getConversationsCount" should {
    "return 0 total messages and 0 unread" in new TestContext(
      conversations = Seq.empty
    ) {
      val result: Count =
        await(
          repository.getConversationsCount(Set(Identifier("EORINumber", "GB1234567890", Some("HMRC-CUS-ORG"))), None))
      result must be(Count(total = 0, unread = 0))
    }

    "return 2 total messages and 2 unread" in new TestContext(
      conversations = allConversations
    ) {
      val result: Count =
        await(
          repository.getConversationsCount(Set(Identifier("EORINumber", "GB1234567890", Some("HMRC-CUS-ORG"))), None))
      result must be(Count(total = 2, unread = 2))
    }

    "return 2 total messages and 1 unread" in new TestContext(
      conversations = allConversations
    ) {
      await(repository.addReadTime(conversation1.client, conversation1.id, 2, DateTime.now))
      val result: Count =
        await(
          repository.getConversationsCount(Set(Identifier("EORINumber", "GB1234567890", Some("HMRC-CUS-ORG"))), None))
      result must be(Count(total = 2, unread = 1))
    }
  }

  "A conversation with object Id" should {
    val conversation = ConversationUtil.getMinimalConversation("123")
    "be returned for a participating enrolment" in new TestContext(conversations = Seq(conversation)) {
      val result =
        await(
          repository
            .getConversation(conversation._id, conversation.participants.map(_.identifier).toSet))
      result.right.get mustBe conversation
    }

    "not be returned if the enrolment is not a participant" in new TestContext(
      conversations = Seq(conversation)
    ) {
      private val modifierParticipantEnrolments: Set[Identifier] =
        conversation.participants.map(id => id.identifier.copy(value = id.identifier.value + "1")).toSet
      val result =
        await(repository.getConversation(conversation._id, modifierParticipantEnrolments))
      result mustBe Left(MessageNotFound(s"Conversation not found for identifiers: $modifierParticipantEnrolments"))
    }
  }

  "Conversation Unread count" should {
    "render 1 if system Message is after participant readTime" in {
      val systemMessage = ConversationMessage(None, 1, DateTime.now.minusDays(1), "!!!", None)
      val systemParticipant =
        Participant(1, ParticipantType.System, Identifier("CDCM", "SMF123456789", None), None, None, None, None)
      val customerParticipant = Participant(
        2,
        ParticipantType.Customer,
        Identifier("EORINumber", "GB1234567890", Some("HMRC-CUS-ORG")),
        readTimes = Some(List(DateTime.now().minusDays(2))),
        name = None,
        email = None,
        parameters = None
      )
      val conversation = conversation1
        .copy(messages = NonEmptyList.one(systemMessage), participants = List(systemParticipant, customerParticipant))

      val result =
        repository.conversationRead(conversation, Set(Identifier("EORINumber", "GB1234567890", Some("HMRC-CUS-ORG"))))
      result mustBe 1
    }

    "render 0 if system Message is before participant readTime" in {
      val systemMessage = ConversationMessage(None, 1, DateTime.now.minusDays(2), "!!!", None)
      val systemParticipant =
        Participant(1, ParticipantType.System, Identifier("CDCM", "SMF123456789", None), None, None, None, None)
      val customerParticipant = Participant(
        2,
        ParticipantType.Customer,
        Identifier("EORINumber", "GB1234567890", Some("HMRC-CUS-ORG")),
        readTimes = Some(List(DateTime.now().minusDays(1))),
        name = None,
        email = None,
        parameters = None
      )
      val conversation = conversation1
        .copy(messages = NonEmptyList.one(systemMessage), participants = List(systemParticipant, customerParticipant))

      val result =
        repository.conversationRead(conversation, Set(Identifier("EORINumber", "GB1234567890", Some("HMRC-CUS-ORG"))))
      result mustBe 0
    }

    "render 0 if customer Message is after participant readTime" in {
      val customerMessage = ConversationMessage(None, 2, DateTime.now.minusDays(1), "!!!", None)
      val systemParticipant =
        Participant(1, ParticipantType.System, Identifier("CDCM", "SMF123456789", None), None, None, None, None)
      val customerParticipant = Participant(
        2,
        ParticipantType.Customer,
        Identifier("EORINumber", "GB1234567890", Some("HMRC-CUS-ORG")),
        readTimes = Some(List(DateTime.now().minusDays(2))),
        name = None,
        email = None,
        parameters = None
      )
      val conversation = conversation1
        .copy(messages = NonEmptyList.one(customerMessage), participants = List(systemParticipant, customerParticipant))

      val result =
        repository.conversationRead(conversation, Set(Identifier("EORINumber", "GB1234567890", Some("HMRC-CUS-ORG"))))
      result mustBe 0
    }

    "render 1 if system Message is after participant readTime with multiple readTimes" in {
      val systemMessage = ConversationMessage(None, 1, DateTime.now.minusDays(1), "!!!", None)
      val systemParticipant =
        Participant(1, ParticipantType.System, Identifier("CDCM", "SMF123456789", None), None, None, None, None)
      val customerParticipant = Participant(
        2,
        ParticipantType.Customer,
        Identifier("EORINumber", "GB1234567890", Some("HMRC-CUS-ORG")),
        readTimes = Some(List(DateTime.now().minusDays(2), DateTime.now().minusDays(3))),
        name = None,
        email = None,
        parameters = None
      )
      val conversation = conversation1
        .copy(messages = NonEmptyList.one(systemMessage), participants = List(systemParticipant, customerParticipant))

      val result =
        repository.conversationRead(conversation, Set(Identifier("EORINumber", "GB1234567890", Some("HMRC-CUS-ORG"))))
      result mustBe 1
    }

    "count unread only for old messages" in new TextContextWithInsert(Seq(conversation1, conversation2, conversation3)) {
      val result = await(
        repository
          .getConversationsUnreadCount(Set(Identifier("EORINumber", "GB1234567890", Some("HMRC-CUS-ORG"))), None))
      result mustBe 2
    }

  }

  class TextContextWithInsert(conversations: Seq[Conversation]) {
    val systemMessage = ConversationMessage(None, 1, DateTime.now.minusDays(1), "!!!", None)
    val systemMessageOld = ConversationMessage(None, 1, DateTime.now.minusDays(3), "!!!", None)
    val systemParticipant =
      Participant(1, ParticipantType.System, Identifier("CDCM", "SMF123456789", None), None, None, None, None)
    val customerParticipant = Participant(
      2,
      ParticipantType.Customer,
      Identifier("EORINumber", "GB1234567890", Some("HMRC-CUS-ORG")),
      readTimes = Some(List(DateTime.now().minusDays(2))),
      name = None,
      email = None,
      parameters = None
    )
    val conversationOne = conversations.head
      .copy(messages = NonEmptyList.one(systemMessage), participants = List(systemParticipant, customerParticipant))
    val conversationTwo = conversations.last
      .copy(messages = NonEmptyList.one(systemMessage), participants = List(systemParticipant, customerParticipant))
    val conversationThree = conversations.tail.head
      .copy(messages = NonEmptyList.one(systemMessageOld), participants = List(systemParticipant, customerParticipant))
    new TestContext(Seq(conversationOne, conversationTwo, conversationThree))
  }

  class TestContext(conversations: Seq[Conversation]) {
    await(Future.sequence(conversations.map(repository.collection.insertOne(_).toFuture())))
  }
}
