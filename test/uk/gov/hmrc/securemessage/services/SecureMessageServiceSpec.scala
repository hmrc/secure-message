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

package uk.gov.hmrc.securemessage.services

import akka.stream.Materializer
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{ never, times, verify, when }
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.i18n.Messages
import play.api.libs.json.{ JsObject, Json }
import play.api.mvc.AnyContentAsEmpty
import play.api.test.Helpers._
import play.api.test.{ FakeRequest, NoMaterializer }
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.auth.core.{ Enrolment, EnrolmentIdentifier, Enrolments }
import uk.gov.hmrc.emailaddress.EmailAddress
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.securemessage.connectors.{ ChannelPreferencesConnector, EISConnector, EmailConnector }
import uk.gov.hmrc.securemessage.controllers.model.MessageType
import uk.gov.hmrc.securemessage.controllers.model.cdcm.read.ConversationMetadata
import uk.gov.hmrc.securemessage.controllers.model.cdcm.write.CaseworkerMessage
import uk.gov.hmrc.securemessage.controllers.model.common.write.CustomerMessage
import uk.gov.hmrc.securemessage.helpers.{ ConversationUtil, MessageUtil, Resources }
import uk.gov.hmrc.securemessage.models.core.Conversation._
import uk.gov.hmrc.securemessage.models.core._
import uk.gov.hmrc.securemessage.models.{ EmailRequest, QueryMessageWrapper }
import uk.gov.hmrc.securemessage.repository.{ ConversationRepository, MessageRepository }
import uk.gov.hmrc.securemessage.{ DuplicateConversationError, EmailLookupError, NoReceiverEmailError, SecureMessageError, _ }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ ExecutionContext, Future }

//TODO: move test data and mocks to TextContexts
@SuppressWarnings(Array("org.wartremover.warts.All"))
class SecureMessageServiceSpec extends PlaySpec with ScalaFutures with TestHelpers with UnitTest {

  "createConversation" must {

    "return SecureMessageException when no email address is provided and cannot be found in cds" in new CreateMessageTestContext(
      getEmailResult = Left(EmailLookupError(""))) {
      private val result = service.createConversation(cnvWithNoEmail).futureValue
      result.swap.toOption.get.message must startWith("Email lookup failed for:")
    }

    "return Right when no email address is provided but is found in the CDS lookup" in new CreateMessageTestContext {
      private val result = service.createConversation(cnvWithNoEmail).futureValue
      result mustBe Right(())
    }

    "return an error message when a conversation already exists for this client and conversation ID" in new CreateMessageTestContext(
      dbInsertResult = Left(DuplicateConversationError("errMsg", None))) {
      private val result = service.createConversation(cnvWithNoEmail).futureValue
      result mustBe Left(DuplicateConversationError("errMsg", None))
    }

    "return NoReceiverEmailError if there are no customer participants" in new CreateMessageTestContext {
      private val result =
        service.createConversation(cnvWithNoCustomer).futureValue
      result mustBe Left(NoReceiverEmailError("Email lookup failed for: CustomerParticipants(List(),List())"))
    }

    "return NoReceiverEmailError for just the customer with no email when we have multiple customer participants" in new CreateMessageTestContext(
      getEmailResult = Left(EmailLookupError("Some error"))) {
      private val result: Either[SecureMessageError, Unit] =
        service.createConversation(cnvWithMultipleCustomers).futureValue
      result.swap.toOption.get.message must startWith("Email lookup failed for:")
    }

    "return content validation error if message content is invalid" in new CreateMessageTestContext {
      val json = Resources
        .readJson("model/api/cdcm/write/conversation-request-invalid-html.json")
        .as[JsObject] + ("_id" -> Json.toJson(objectID))
      private val invalidBaseHtmlConversation: Conversation =
        json.as[Conversation]
      private val result = service.createConversation(invalidBaseHtmlConversation)
      result.futureValue mustBe Left(InvalidContent(
        "Html contains disallowed tags, attributes or protocols within the tags: matt. For allowed elements see class org.jsoup.safety.Whitelist.relaxed()"))
    }
  }

  "getConversations" must {

    "return a list of ConversationMetaData when presented with one customer enrolment and no tags for a filter" in {
      when(
        mockConversationRepository.getConversations(
          ArgumentMatchers.eq(Set(Identifier("EORIName", "GB7777777777", Some("HMRC-CUS_ORG")))),
          ArgumentMatchers.eq(None))(any[ExecutionContext]))
        .thenReturn(Future.successful(conversations))
      val filters =
        Filters(None, Some(List(CustomerEnrolment("HMRC-CUS_ORG", "EORIName", "GB7777777777"))), None)
      val result = await(service.getConversations(authEnrolmentsFrom(filters.enrolmentsFilter), filters))
      val metadata: ConversationMetadata = ConversationMetadata(
        "CDCM",
        "D-80542-20201120",
        "MRN: 19GB4S24GC3PPFGVR7",
        DateTime.parse("2020-11-10T15:00:01.000"),
        Some("CDS Exports Team"),
        unreadMessages = false,
        1)
      result mustBe
        List(metadata)
    }

    "return a list of ConversationMetaData when presented with one customer enrolment and one tag for a filter" in {
      when(
        mockConversationRepository.getConversations(
          ArgumentMatchers.eq(Set(Identifier("EORIName", "GB7777777777", Some("HMRC-CUS_ORG")))),
          ArgumentMatchers.eq(Some(List(FilterTag("notificationType", "CDS Exports"))))
        )(any[ExecutionContext]))
        .thenReturn(Future.successful(conversations))
      val filters = Filters(
        None,
        Some(List(CustomerEnrolment("HMRC-CUS_ORG", "EORIName", "GB7777777777"))),
        Some(List(FilterTag("notificationType", "CDS Exports"))))
      val result = await(service.getConversations(authEnrolmentsFrom(filters.enrolmentsFilter), filters))
      result mustBe
        List(
          ConversationMetadata(
            "CDCM",
            "D-80542-20201120",
            "MRN: 19GB4S24GC3PPFGVR7",
            DateTime.parse("2020-11-10T15:00:01.000"),
            Some("CDS Exports Team"),
            unreadMessages = false,
            1))
    }

    "return a list of ConversationMetaData in the order of latest message in the list" in {
      val listOfCoreConversation =
        List(
          ConversationUtil.getFullConversation(
            BSONObjectID.generate(),
            "D-80542-20201120",
            "HMRC-CUS-ORG",
            "EORINumber",
            "GB1234567890",
            messageCreationDate = "2020-11-08T15:00:00.000"),
          ConversationUtil.getFullConversation(
            BSONObjectID.generate,
            "D-80542-20201120",
            "HMRC-CUS-ORG",
            "EORINumber",
            "GB1234567890",
            messageCreationDate = "2020-11-10T15:00:00.000"),
          ConversationUtil.getFullConversation(
            BSONObjectID.generate,
            "D-80542-20201120",
            "HMRC-CUS-ORG",
            "EORINumber",
            "GB1234567890",
            messageCreationDate = "2020-11-09T15:00:00.000")
        )

      when(
        mockConversationRepository.getConversations(
          ArgumentMatchers.eq(Set(Identifier("EORIName", "GB7777777777", Some("HMRC-CUS_ORG")))),
          ArgumentMatchers.eq(Some(List(FilterTag("notificationType", "CDS Exports"))))
        )(any[ExecutionContext]))
        .thenReturn(Future.successful(listOfCoreConversation))

      val filters = Filters(
        None,
        Some(List(CustomerEnrolment("HMRC-CUS_ORG", "EORIName", "GB7777777777"))),
        Some(List(FilterTag("notificationType", "CDS Exports"))))
      val result = await(service.getConversations(authEnrolmentsFrom(filters.enrolmentsFilter), filters))
      result mustBe
        List(
          ConversationMetadata(
            "CDCM",
            "D-80542-20201120",
            "MRN: 19GB4S24GC3PPFGVR7",
            DateTime.parse("2020-11-10T15:00:00.000"),
            Some("CDS Exports Team"),
            unreadMessages = false,
            1),
          ConversationMetadata(
            "CDCM",
            "D-80542-20201120",
            "MRN: 19GB4S24GC3PPFGVR7",
            DateTime.parse("2020-11-09T15:00:00.000"),
            Some("CDS Exports Team"),
            unreadMessages = false,
            1),
          ConversationMetadata(
            "CDCM",
            "D-80542-20201120",
            "MRN: 19GB4S24GC3PPFGVR7",
            DateTime.parse("2020-11-08T15:00:00.000"),
            Some("CDS Exports Team"),
            unreadMessages = false,
            1)
        )
    }
  }

  "getConversation" must {
    val hmrcCusOrg = "HMRC-CUS-ORG"
    val conversationId = "D-80542-20201120"
    val eoriName = "EORIName"
    val enrolmentValue = "GB7777777777"
    "return a message with ApiConversation" in new GetConversationTestContext(
      getConversationResult = Right(
        ConversationUtil
          .getFullConversation(BSONObjectID.generate, conversationId, hmrcCusOrg, eoriName, enrolmentValue))
    ) {
      private val result = await(
        service
          .getConversation("CDCM", conversationId, Set(CustomerEnrolment(hmrcCusOrg, eoriName, enrolmentValue))))
      result.right.get.client mustBe "CDCM"
      result.right.get.messages.size mustBe 1
      result.right.get.subject mustBe "MRN: 19GB4S24GC3PPFGVR7"
    }

    "return a Left(ConversationNotFound)" in {
      when(
        mockConversationRepository
          .getConversation(any[String], any[String], any[Set[Identifier]])(any[ExecutionContext]))
        .thenReturn(Future(Left(MessageNotFound(
          "Conversation not found for client: cdcm, conversationId: D-80542-20201120, enrolment: GB1234567890"))))
      val result = await(
        service
          .getConversation(
            "CDCM",
            "D-80542-20201120",
            Set(CustomerEnrolment("HMRC-CUS_ORG", "EORIName", "GB7777777777"))))
      result mustBe
        Left(
          MessageNotFound(
            s"Conversation not found for client: cdcm, conversationId: D-80542-20201120, enrolment: GB1234567890"))
    }
  }

  "getConversation by id" must {
    val hmrcCusOrg = "HMRC-CUS-ORG"
    val conversationId = "D-80542-20201120"
    val eoriName = "EORIName"
    val enrolmentValue = "GB7777777777"
    val id = BSONObjectID.generate
    "return a message with ApiConversation" in new GetConversationByIDTestContext(
      getConversationResult = Right(
        ConversationUtil
          .getFullConversation(id, conversationId, hmrcCusOrg, eoriName, enrolmentValue))
    ) {
      val result = await(
        service.getConversation(id.stringify, Set(CustomerEnrolment(hmrcCusOrg, eoriName, enrolmentValue)))).right.get

      result.client mustBe "CDCM"
      result.messages.size mustBe 1
      result.subject mustBe "MRN: 19GB4S24GC3PPFGVR7"
    }

    "not return a conversation on add readTime error" in new GetConversationByIDWithReadTimeErrorTestContext(
      getConversationResult = Right(
        ConversationUtil
          .getFullConversation(id, conversationId, hmrcCusOrg, eoriName, enrolmentValue))
    ) {
      val result =
        await(service.getConversation(id.stringify, Set(CustomerEnrolment(hmrcCusOrg, eoriName, enrolmentValue))))

      result.left.get.message mustBe "Can not store read time"
    }

    "return a Left(ConversationNotFound)" in {
      when(
        mockConversationRepository
          .getConversation(any[String], any[Set[Identifier]])(any[ExecutionContext]))
        .thenReturn(Future(Left(MessageNotFound(
          "Conversation not found for client: cdcm, conversationId: D-80542-20201120, enrolment: GB1234567890"))))
      val result = await(
        service
          .getConversation(
            BSONObjectID.generate().stringify,
            Set(CustomerEnrolment("HMRC-CUS_ORG", "EORIName", "GB7777777777"))))
      result mustBe
        Left(
          MessageNotFound(
            s"Conversation not found for client: cdcm, conversationId: D-80542-20201120, enrolment: GB1234567890"))
    }
  }

  "getMessage by id" must {
    "return a message with ApiLetter" in {
      when(mockMessageRepository.getLetter(any[String], any[Set[Identifier]])(any[ExecutionContext]))
        .thenReturn(Future(Right(letter)))
      when(mockMessageRepository.addReadTime(any[String])(any[ExecutionContext]))
        .thenReturn(Future(Right(())))
      val result = await(
        service
          .getLetter(
            BSONObjectID.generate().stringify,
            Set(CustomerEnrolment("HMRC-CUS_ORG", "EORIName", "GB7777777777"))))
      result.right.get.subject mustBe "subject"
      result.right.get.content mustBe "content"
    }

    "return a Left(LetterNotFound)" in {
      when(mockMessageRepository.getLetter(any[String], any[Set[Identifier]])(any[ExecutionContext]))
        .thenReturn(Future(Left(MessageNotFound("Letter not found"))))
      when(mockMessageRepository.addReadTime(any[String])(any[ExecutionContext]))
        .thenReturn(Future(Right(())))
      val result = await(
        service
          .getLetter(
            BSONObjectID.generate().stringify,
            Set(CustomerEnrolment("HMRC-CUS_ORG", "EORIName", "GB7777777777"))))
      result mustBe
        Left(MessageNotFound(s"Letter not found"))
    }

    "return a left if update readTime fails" in {
      when(mockMessageRepository.getLetter(any[String], any[Set[Identifier]])(any[ExecutionContext]))
        .thenReturn(Future(Right(MessageUtil.getMessage("subject", "content"))))
      when(mockMessageRepository.addReadTime(any[String])(any[ExecutionContext]))
        .thenReturn(Future(Left(StoreError("cant store readTime", None))))
      val result = await(
        service
          .getLetter(
            BSONObjectID.generate().stringify,
            Set(CustomerEnrolment("HMRC-CUS_ORG", "EORIName", "GB7777777777"))))
      result.left.get.message mustBe "cant store readTime"
    }

  }

  "Adding a customer message to a conversation" must {

    "update the database when the customer has a participating enrolment" in new AddCustomerMessageTestContext(
      getConversationResult = Right(conversations.head)) {
      when(mockEisConnector.forwardMessage(any[QueryMessageWrapper])).thenReturn(Future(Right(())))
      await(service.addCustomerMessage(encodedId, customerMessage, enrolments))
      verify(mockConversationRepository, times(1))
        .addMessageToConversation(any[String], any[String], any[ConversationMessage])(any[ExecutionContext])
    }

    "return NoParticipantFound if the customer does not have a participating enrolment" in new AddCustomerMessageTestContext(
      getConversationResult = Right(cnvWithNoEmail)) {
      when(mockEnrolments.enrolments)
        .thenReturn(Set(Enrolment("HMRC-CUS-ORG", Seq(EnrolmentIdentifier("EORINumber", "GB123456789000001")), "")))
      await(service.addCustomerMessage(encodedId, customerMessage, mockEnrolments)) mustBe Left(ParticipantNotFound(
        "No participant found for client: CDCM, conversationId: 123, indentifiers: Set(Identifier(EORINumber,GB123456789000001,Some(HMRC-CUS-ORG)))"))
    }

    "return ConversationIdNotFound if the conversation ID is not found" in new AddCustomerMessageTestContext(
      getConversationResult = Left(MessageNotFound("Conversation ID not known"))) {
      when(mockEnrolments.enrolments)
        .thenReturn(Set(Enrolment("HMRC-CUS-ORG", Seq(EnrolmentIdentifier("EORINumber", "GB123456789000001")), "")))
      await(service.addCustomerMessage(encodedId, customerMessage, mockEnrolments)) mustBe Left(
        MessageNotFound("Conversation ID not known"))
    }

    "return EisForwardingError and don't store the message if the message cannot be forwarded to EIS" in new AddCustomerMessageTestContext(
      getConversationResult = Right(cnvWithNoEmail),
      addMessageResult = Left(EisForwardingError("There was an issue with forwarding the message to EIS"))) {
      when(mockEnrolments.enrolments)
        .thenReturn(Set(Enrolment("HMRC-CUS-ORG", Seq(EnrolmentIdentifier("EORINumber", "GB123456789000000")), "")))
      await(service.addCustomerMessage(encodedId, customerMessage, mockEnrolments))
      verify(mockConversationRepository, never())
        .addMessageToConversation(any[String], any[String], any[ConversationMessage])(any[ExecutionContext])
    }
  }

  "Adding a caseworker message to a conversation" must {

    "update the database and send a nudge email when a caseworker message has successfully been added to the db" in new AddCaseworkerMessageTestContent {
      await(
        service.addCaseWorkerMessageToConversation(
          "CDCM",
          "123",
          caseWorkerMessage("PHA+Q2FuIEkgaGF2ZSBteSB0YXggbW9uZXkgcGxlYXNlPzwvcD4="))) mustBe Right(())
    }

    "return ParticipantNotFound if the caseworker does not have a participating enrolment" in new AddCaseworkerMessageTestContent {
      await(
        service.addCaseWorkerMessageToConversation(
          "CDCM",
          "D-80542-20201120",
          caseWorkerMessage("PHA+Q2FuIEkgaGF2ZSBteSB0YXggbW9uZXkgcGxlYXNlPzwvcD4="))) mustBe Left(ParticipantNotFound(
        "No participant found for client: CDCM, conversationId: 123, indentifiers: Set(Identifier(CDCM,D-80542-20201120,None))"))
    }

    "return ConversationIdNotFound if the conversation ID is not found" in new AddCaseworkerMessageTestContent(
      getConversationResult = Left(MessageNotFound("Conversation ID not known"))) {
      await(
        service.addCaseWorkerMessageToConversation(
          "CDCM",
          "D-80542-20201120",
          caseWorkerMessage("QmxhaCBibGFoIGJsYWg="))) mustBe Left(MessageNotFound("Conversation ID not known"))
    }

    "return content validation error if message content is invalid" in new AddCaseworkerMessageTestContent {
      await(
        service.addCaseWorkerMessageToConversation(
          "CDCM",
          "D-80542-20201120",
          caseWorkerMessage("PG1hdHQ+Q2FuIEkgaGF2ZSBteSB0YXggbW9uZXkgcGxlYXNlPzwvbWF0dD4="))) mustBe
        Left(InvalidContent(
          "Html contains disallowed tags, attributes or protocols within the tags: matt. For allowed elements see class org.jsoup.safety.Whitelist.relaxed()"))
    }
  }

  "getMessages" should {
    "return both conversations and letters sorted in descending order by issue date" in new GetMessagesTestContext() {
      private val result: List[Message] = service.getMessages(enrolments, filters()).futureValue
      result must not be (conversations ++ letters)
      result mustBe (conversations ++ letters).sortWith { (a, b) =>
        a.issueDate.isAfter(b.issueDate)
      }
    }
    "return just conversations when there are no letters" in new GetMessagesTestContext(dbLetters = List.empty) {
      service.getMessages(enrolments, filters()).futureValue mustBe conversations
    }
    "return just letters when there are no conversations" in new GetMessagesTestContext(dbConversations = List.empty) {
      service.getMessages(enrolments, filters()).futureValue mustBe letters
    }

    "return an empty list when there are neither conversations nor letters" in new GetMessagesTestContext(
      dbConversations = List.empty,
      dbLetters = List.empty) {
      service.getMessages(enrolments, filters()).futureValue mustBe empty
    }

    "add readTime when there are new messages after last readTime" in new AddReadTimesTestContext {
      val readTimeStamp = DateTime.parse("2020-11-09T15:00:00.000")
      val conversation = ConversationUtil.getFullConversation(
        BSONObjectID.generate(),
        "D-80542-20201120",
        "HMRC-CUS-ORG",
        "EORINumber",
        "GB1234567890",
        messageCreationDate = "2021-11-08T15:00:00.000",
        readTimes = Some(List(readTimeStamp))
      )
      await(
        service
          .addReadTime(conversation, Set(Identifier("EORINumber", "GB1234567890", Some("HMRC-CUS-ORG"))), readTimeStamp)
          .value)
      verify(mockConversationRepository, times(1))
        .addReadTime(conversation.client, conversation.id, 2, readTimeStamp)
    }

    "add read time when no messages were read" in new AddReadTimesTestContext {
      val readTimeStamp = DateTime.parse("2020-11-09T15:00:00.000")
      val conversation = ConversationUtil.getFullConversation(
        BSONObjectID.generate(),
        "D-80542-20201120",
        "HMRC-CUS-ORG",
        "EORINumber",
        "GB1234567890",
        messageCreationDate = "2020-11-08T15:00:00.000",
        readTimes = None)
      await(
        service
          .addReadTime(conversation, Set(Identifier("EORINumber", "GB1234567890", Some("HMRC-CUS-ORG"))), readTimeStamp)
          .value)
      verify(mockConversationRepository, times(1))
        .addReadTime(conversation.client, conversation.id, 2, readTimeStamp)
    }

    "not add readTime when there are no new messages after last readtime" in new AddReadTimesTestContext {
      val readTimeStamp = DateTime.parse("2021-11-09T15:00:00.000")
      val conversation = ConversationUtil.getFullConversation(
        BSONObjectID.generate(),
        "D-80542-20201120",
        "HMRC-CUS-ORG",
        "EORINumber",
        "GB1234567890",
        messageCreationDate = "2020-11-08T15:00:00.000",
        readTimes = Some(List(readTimeStamp))
      )
      await(
        service
          .addReadTime(conversation, Set(Identifier("EORINumber", "GB1234567890", Some("HMRC-CUS-ORG"))), readTimeStamp)
          .value)
      verify(mockConversationRepository, times(0))
        .addReadTime(conversation.client, conversation.id, 2, readTimeStamp)
    }

  }

  @SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
  class AddReadTimesTestContext {
    val mockEisConnector: EISConnector = mock[EISConnector]
    val mockAuditConnector: AuditConnector = mock[AuditConnector]
    val mockConversationRepository: ConversationRepository = mock[ConversationRepository]
    val mockMessageRepository: MessageRepository = mock[MessageRepository]
    val mockEmailConnector: EmailConnector = mock[EmailConnector]
    when(mockEmailConnector.send(any[EmailRequest])(any[HeaderCarrier])).thenReturn(Future.successful(Right(())))
    val mockChannelPreferencesConnector: ChannelPreferencesConnector = mock[ChannelPreferencesConnector]
    when(
      mockConversationRepository.addReadTime(any[String], any[String], any[Int], any[DateTime])(any[ExecutionContext]))
      .thenReturn(Future.successful(Right(())))
    val service: SecureMessageService =
      new SecureMessageService(
        mockConversationRepository,
        mockMessageRepository,
        mockEmailConnector,
        mockChannelPreferencesConnector,
        mockEisConnector,
        mockAuditConnector)
  }
  @SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
  class CreateMessageTestContext(
    dbInsertResult: Either[SecureMessageError, Unit] = Right(()),
    getEmailResult: Either[EmailLookupError, EmailAddress] = Right(EmailAddress("joeblogs@yahoo.com"))) {
    when(mockConversationRepository.insertIfUnique(any[Conversation])(any[ExecutionContext]))
      .thenReturn(Future(dbInsertResult))
    when(mockChannelPreferencesConnector.getEmailForEnrolment(any[Identifier])(any[HeaderCarrier]))
      .thenReturn(Future(getEmailResult))
  }

  @SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
  class GetConversationTestContext(getConversationResult: Either[MessageNotFound, Conversation]) {
    when(
      mockConversationRepository.getConversation(any[String], any[String], any[Set[Identifier]])(any[ExecutionContext]))
      .thenReturn(Future(getConversationResult))
    when(
      mockConversationRepository.addReadTime(any[String], any[String], any[Int], any[DateTime])(any[ExecutionContext]))
      .thenReturn(Future.successful(Right(())))
  }

  @SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
  class GetMessagesTestContext(dbConversations: List[Conversation] = conversations, dbLetters: List[Letter] = letters) {
    when(
      mockConversationRepository.getConversations(any[Set[Identifier]], any[Option[List[FilterTag]]])(
        any[ExecutionContext]))
      .thenReturn(Future(dbConversations))
    when(mockMessageRepository.getLetters(any[Set[Identifier]], any[Option[List[FilterTag]]])(any[ExecutionContext]))
      .thenReturn(Future(dbLetters))
  }

  @SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
  class GetConversationByIDTestContext(getConversationResult: Either[MessageNotFound, Conversation]) {
    when(mockConversationRepository.getConversation(any[String], any[Set[Identifier]])(any[ExecutionContext]))
      .thenReturn(Future(getConversationResult))
    when(
      mockConversationRepository.addReadTime(any[String], any[String], any[Int], any[DateTime])(any[ExecutionContext]))
      .thenReturn(Future.successful(Right(())))
  }

  @SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
  class GetConversationByIDWithReadTimeErrorTestContext(getConversationResult: Either[MessageNotFound, Conversation]) {
    when(mockConversationRepository.getConversation(any[String], any[Set[Identifier]])(any[ExecutionContext]))
      .thenReturn(Future(getConversationResult))
    when(
      mockConversationRepository.addReadTime(any[String], any[String], any[Int], any[DateTime])(any[ExecutionContext]))
      .thenReturn(Future.successful(Left(StoreError("Can not store read time", None))))
  }

  @SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
  class AddCustomerMessageTestContext(
    getConversationResult: Either[MessageNotFound, Conversation],
    addMessageResult: Either[SecureMessageError, Unit] = Right(()))
      extends TestHelpers {
    val encodedId: String = base64Encode(MessageType.Conversation + "/" + "D-80542-20201120")
    when(mockConversationRepository.getConversation(any[String], any[Set[Identifier]])(any[ExecutionContext]))
      .thenReturn(Future(getConversationResult))
    when(
      mockConversationRepository.addMessageToConversation(any[String], any[String], any[ConversationMessage])(
        any[ExecutionContext]))
      .thenReturn(Future(addMessageResult))
  }

  @SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
  class AddCaseworkerMessageTestContent(
    getConversationResult: Either[MessageNotFound, Conversation] = Right(cnvWithNoEmail),
    addMessageResult: Either[SecureMessageError, Unit] = Right(()),
    sendEmailResult: Either[EmailSendingError, Unit] = Right(())) {
    when(
      mockConversationRepository.getConversation(any[String], any[String], any[Set[Identifier]])(any[ExecutionContext]))
      .thenReturn(Future(getConversationResult))
    when(
      mockConversationRepository.addMessageToConversation(any[String], any[String], any[ConversationMessage])(
        any[ExecutionContext]))
      .thenReturn(Future(addMessageResult))
    when(mockEmailConnector.send(any[EmailRequest])(any[HeaderCarrier])).thenReturn(Future(sendEmailResult))
  }
}

//TODO: change this with specialized TestCases, reuse values: no strings
@SuppressWarnings(Array("org.wartremover.warts.TraversableOps", "org.wartremover.warts.NonUnitStatements"))
trait TestHelpers extends MockitoSugar with UnitTest {
  import uk.gov.hmrc.auth.core.Enrolment
  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit val mat: Materializer = NoMaterializer
  implicit val messages: Messages = stubMessages()
  implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
  val objectID: BSONObjectID = BSONObjectID.generate()
  private val identifierName = "EORINumber"
  private val identifierValue90 = "GB1234567890"
  private val identifierEnrolment = "HMRC-CUS-ORG"
  val identifier: Identifier = Identifier(identifierName, identifierValue90, Some(identifierEnrolment))
  val participant: Participant = Participant(1, ParticipantType.Customer, identifier, None, None, None, None)
  val mockEisConnector: EISConnector = mock[EISConnector]
  val mockAuditConnector: AuditConnector = mock[AuditConnector]
  val mockConversationRepository: ConversationRepository = mock[ConversationRepository]
  val mockMessageRepository: MessageRepository = mock[MessageRepository]
  val mockEmailConnector: EmailConnector = mock[EmailConnector]
  when(mockEmailConnector.send(any[EmailRequest])(any[HeaderCarrier])).thenReturn(Future.successful(Right(())))
  val mockChannelPreferencesConnector: ChannelPreferencesConnector = mock[ChannelPreferencesConnector]
  val service: SecureMessageService =
    new SecureMessageService(
      mockConversationRepository,
      mockMessageRepository,
      mockEmailConnector,
      mockChannelPreferencesConnector,
      mockEisConnector,
      mockAuditConnector)
  val mockEnrolments: Enrolments = mock[Enrolments]
  val enrolments: Enrolments = Enrolments(Set(
    Enrolment(identifierEnrolment, Vector(EnrolmentIdentifier(identifierName, identifierValue90)), "Activated", None)))
  def filters(identifier: Identifier = identifier): Filters =
    Filters(enrolmentKeys = identifier.enrolment.map(List(_)), None, None)
  val systemIdentifier: Identifier = Identifier("CDCM", "123", None)
  val systemParticipant: Participant =
    Participant(1, ParticipantType.System, systemIdentifier, None, None, None, None)
  val customerParticipant: Participant = Participant(
    2,
    ParticipantType.Customer,
    Identifier(identifierName, identifierValue90, Some(identifierEnrolment)),
    None,
    Some(EmailAddress("test@test.com")),
    None,
    None)
  val message: ConversationMessage =
    ConversationMessage(2, DateTime.now, "PHA+Q2FuIEkgaGF2ZSBteSB0YXggbW9uZXkgcGxlYXNlPzwvcD4==")
  val customerMessage: CustomerMessage = CustomerMessage("PGRpdj5IZWxsbzwvZGl2Pg==")

  def caseWorkerMessage(content: String): CaseworkerMessage =
    CaseworkerMessage(content)

  private val conversation: Conversation = ConversationUtil
    .getFullConversation(
      BSONObjectID.generate,
      "D-80542-20201120",
      identifierEnrolment,
      identifierName,
      identifierValue90)
  val conversations = List(conversation)
  val letter: Letter = MessageUtil.getMessage("subject", "content")
  val letters = List(letter)
  val conversationJson: JsObject = Resources
    .readJson("model/api/cdcm/write/conversation-request.json")
    .as[JsObject] + ("_id" -> Json.toJson(objectID))
  val cnvWithNoEmail: Conversation =
    conversationJson.as[Conversation]
  val cnvWithNoCustomer: Conversation = cnvWithNoEmail.copy(participants = List(cnvWithNoEmail.participants.head))
  val cnvWithMultipleCustomers: Conversation =
    ConversationUtil.getConversationRequestWithMultipleCustomers.asConversationWithCreatedDate("CDCM", "123", now)
}
