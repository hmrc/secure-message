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
import play.api.mvc.AnyContentAsEmpty
import play.api.test.Helpers._
import play.api.test.{ FakeRequest, NoMaterializer }
import uk.gov.hmrc.auth.core.{ Enrolment, EnrolmentIdentifier, Enrolments }
import uk.gov.hmrc.emailaddress.EmailAddress
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.securemessage.connectors.{ ChannelPreferencesConnector, EISConnector, EmailConnector }
import uk.gov.hmrc.securemessage.controllers.model.cdcm.read.ConversationMetadata
import uk.gov.hmrc.securemessage.controllers.model.cdcm.write.CaseworkerMessage
import uk.gov.hmrc.securemessage.controllers.model.common.CustomerEnrolment
import uk.gov.hmrc.securemessage.controllers.model.common.read.FilterTag
import uk.gov.hmrc.securemessage.controllers.model.common.write.CustomerMessage
import uk.gov.hmrc.securemessage.helpers.{ ConversationUtil, Resources }
import uk.gov.hmrc.securemessage.models.core._
import uk.gov.hmrc.securemessage.models.{ EmailRequest, QueryResponseWrapper }
import uk.gov.hmrc.securemessage.repository.ConversationRepository
import uk.gov.hmrc.securemessage.{ DuplicateConversationError, EmailLookupError, NoReceiverEmailError, SecureMessageError, _ }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ ExecutionContext, Future }

//TODO: move test data and mocks to TextContexts
@SuppressWarnings(Array("org.wartremover.warts.All"))
class SecureMessageServiceSpec extends PlaySpec with ScalaFutures with TestHelpers {

  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit val mat: Materializer = NoMaterializer
  implicit val messages: Messages = stubMessages()
  implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

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
      private val invalidBaseHtmlConversation: Conversation =
        Resources.readJson("model/api/cdcm/write/conversation-request-invalid-html.json").as[Conversation]
      private val result = service.createConversation(invalidBaseHtmlConversation)
      result.futureValue mustBe Left(InvalidContent(
        "Html contains disallowed tags, attributes or protocols within the tags: matt. For allowed elements see class org.jsoup.safety.Whitelist.relaxed()"))
    }
  }

  "getConversationsFiltered" must {

    "return a list of ConversationMetaData when presented with one customer enrolment and no tags for a filter" in {
      when(
        mockRepository.getConversationsFiltered(
          ArgumentMatchers.eq(Set(Identifier("EORIName", "GB7777777777", Some("HMRC-CUS_ORG")))),
          ArgumentMatchers.eq(None))(any[ExecutionContext]))
        .thenReturn(Future.successful(listOfCoreConversation))
      val result = await(
        service.getConversationsFiltered(Set(CustomerEnrolment("HMRC-CUS_ORG", "EORIName", "GB7777777777")), None))
      val metadata: ConversationMetadata = ConversationMetadata(
        "cdcm",
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
        mockRepository.getConversationsFiltered(
          ArgumentMatchers.eq(Set(Identifier("EORIName", "GB7777777777", Some("HMRC-CUS_ORG")))),
          ArgumentMatchers.eq(Some(List(FilterTag("notificationType", "CDS Exports"))))
        )(any[ExecutionContext]))
        .thenReturn(Future.successful(listOfCoreConversation))
      val result = await(
        service.getConversationsFiltered(
          Set(CustomerEnrolment("HMRC-CUS_ORG", "EORIName", "GB7777777777")),
          Some(List(FilterTag("notificationType", "CDS Exports")))))
      result mustBe
        List(
          ConversationMetadata(
            "cdcm",
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
            "D-80542-20201120",
            "HMRC-CUS-ORG",
            "EORINumber",
            "GB1234567890",
            messageCreationDate = "2020-11-08T15:00:00.000"),
          ConversationUtil.getFullConversation(
            "D-80542-20201120",
            "HMRC-CUS-ORG",
            "EORINumber",
            "GB1234567890",
            messageCreationDate = "2020-11-10T15:00:00.000"),
          ConversationUtil.getFullConversation(
            "D-80542-20201120",
            "HMRC-CUS-ORG",
            "EORINumber",
            "GB1234567890",
            messageCreationDate = "2020-11-09T15:00:00.000")
        )

      when(
        mockRepository.getConversationsFiltered(
          ArgumentMatchers.eq(Set(Identifier("EORIName", "GB7777777777", Some("HMRC-CUS_ORG")))),
          ArgumentMatchers.eq(Some(List(FilterTag("notificationType", "CDS Exports"))))
        )(any[ExecutionContext]))
        .thenReturn(Future.successful(listOfCoreConversation))

      val result = await(
        service.getConversationsFiltered(
          Set(CustomerEnrolment("HMRC-CUS_ORG", "EORIName", "GB7777777777")),
          Some(List(FilterTag("notificationType", "CDS Exports")))))
      result mustBe
        List(
          ConversationMetadata(
            "cdcm",
            "D-80542-20201120",
            "MRN: 19GB4S24GC3PPFGVR7",
            DateTime.parse("2020-11-10T15:00:00.000"),
            Some("CDS Exports Team"),
            unreadMessages = false,
            1),
          ConversationMetadata(
            "cdcm",
            "D-80542-20201120",
            "MRN: 19GB4S24GC3PPFGVR7",
            DateTime.parse("2020-11-09T15:00:00.000"),
            Some("CDS Exports Team"),
            unreadMessages = false,
            1),
          ConversationMetadata(
            "cdcm",
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

    "return a message with ApiConversation" in {
      when(mockRepository.getConversation(any[String], any[String], any[Set[Identifier]])(any[ExecutionContext]))
        .thenReturn(Future.successful(Right(
          ConversationUtil.getFullConversation("D-80542-20201120", "HMRC-CUS-ORG", "EORINumber", "GB1234567890"))))
      val result = await(
        service
          .getConversation(
            "cdcm",
            "D-80542-20201120",
            Set(CustomerEnrolment("HMRC-CUS_ORG", "EORIName", "GB7777777777"))))
      result.right.get.client mustBe "cdcm"
      result.right.get.messages.size mustBe 1
      result.right.get.subject mustBe "MRN: 19GB4S24GC3PPFGVR7"
    }

    "return a Left(ConversationNotFound)" in {
      when(
        mockRepository
          .getConversation(any[String], any[String], any[Set[Identifier]])(any[ExecutionContext]))
        .thenReturn(Future(Left(ConversationNotFound(
          "Conversation not found for client: cdcm, conversationId: D-80542-20201120, enrolment: GB1234567890"))))
      val result = await(
        service
          .getConversation(
            "cdcm",
            "D-80542-20201120",
            Set(CustomerEnrolment("HMRC-CUS_ORG", "EORIName", "GB7777777777"))))
      result mustBe
        Left(
          ConversationNotFound(
            s"Conversation not found for client: cdcm, conversationId: D-80542-20201120, enrolment: GB1234567890"))
    }
  }

  "Adding a customer message to a conversation" must {

    "update the database when the customer has a participating enrolment" in new AddCustomerMessageTestContext(
      getConversationResult = Right(listOfCoreConversation.head)) {
      when(mockEisConnector.forwardMessage(any[QueryResponseWrapper])).thenReturn(Future(Right(())))
      await(service.addCustomerMessageToConversation("cdcm", "D-80542-20201120", customerMessage, customerEnrolment))
      verify(mockRepository, times(1))
        .addMessageToConversation(any[String], any[String], any[Message])(any[ExecutionContext])
    }

    "return NoParticipantFound if the customer does not have a participating enrolment" in new AddCustomerMessageTestContext(
      getConversationResult = Right(cnvWithNoEmail)) {
      when(mockEnrolments.enrolments)
        .thenReturn(Set(Enrolment("HMRC-CUS-ORG", Seq(EnrolmentIdentifier("EORINumber", "GB123456789000001")), "")))
      await(service.addCustomerMessageToConversation("cdcm", "D-80542-20201120", customerMessage, mockEnrolments)) mustBe Left(
        ParticipantNotFound(
          "No participant found for client: cdcm, conversationId: 123, indentifiers: Set(Identifier(EORINumber,GB123456789000001,Some(HMRC-CUS-ORG)))"))
    }

    "return ConversationIdNotFound if the conversation ID is not found" in new AddCustomerMessageTestContext(
      getConversationResult = Left(ConversationNotFound("Conversation ID not known"))) {
      when(mockEnrolments.enrolments)
        .thenReturn(Set(Enrolment("HMRC-CUS-ORG", Seq(EnrolmentIdentifier("EORINumber", "GB123456789000001")), "")))
      await(service.addCustomerMessageToConversation("cdcm", "D-80542-20201120", customerMessage, mockEnrolments)) mustBe Left(
        ConversationNotFound("Conversation ID not known"))
    }

    "return EisForwardingError and don't store the message if the message cannot be forwarded to EIS" in new AddCustomerMessageTestContext(
      getConversationResult = Right(cnvWithNoEmail),
      addMessageResult = Left(EisForwardingError("There was an issue with forwarding the message to EIS"))) {
      when(mockEnrolments.enrolments)
        .thenReturn(Set(Enrolment("HMRC-CUS-ORG", Seq(EnrolmentIdentifier("EORINumber", "GB123456789000000")), "")))
      await(service.addCustomerMessageToConversation("cdcm", "D-80542-20201120", customerMessage, mockEnrolments))
      verify(mockRepository, never())
        .addMessageToConversation(any[String], any[String], any[Message])(any[ExecutionContext])
    }
  }

  "Adding a caseworker message to a conversation" must {

    "update the database and send a nudge email when a caseworker message has successfully been added to the db" in new AddCaseworkerMessageTestContent {
      await(
        service.addCaseWorkerMessageToConversation(
          "cdcm",
          "123",
          caseWorkerMessage("PHA+Q2FuIEkgaGF2ZSBteSB0YXggbW9uZXkgcGxlYXNlPzwvcD4="))) mustBe Right(())
    }

    "return ParticipantNotFound if the caseworker does not have a participating enrolment" in new AddCaseworkerMessageTestContent {
      await(
        service.addCaseWorkerMessageToConversation(
          "cdcm",
          "D-80542-20201120",
          caseWorkerMessage("PHA+Q2FuIEkgaGF2ZSBteSB0YXggbW9uZXkgcGxlYXNlPzwvcD4="))) mustBe Right(())
    }

    "return ConversationIdNotFound if the conversation ID is not found" in new AddCaseworkerMessageTestContent(
      getConversationResult = Left(ConversationNotFound("Conversation ID not known"))) {
      await(
        service.addCaseWorkerMessageToConversation(
          "cdcm",
          "D-80542-20201120",
          caseWorkerMessage("QmxhaCBibGFoIGJsYWg="))) mustBe Left(ConversationNotFound("Conversation ID not known"))
    }

    "return content validation error if message content is invalid" in new AddCaseworkerMessageTestContent {
      await(
        service.addCaseWorkerMessageToConversation(
          "cdcm",
          "D-80542-20201120",
          caseWorkerMessage("PG1hdHQ+Q2FuIEkgaGF2ZSBteSB0YXggbW9uZXkgcGxlYXNlPzwvbWF0dD4="))) mustBe
        Left(InvalidContent(
          "Html contains disallowed tags, attributes or protocols within the tags: matt. For allowed elements see class org.jsoup.safety.Whitelist.relaxed()"))
    }
  }

  "updateReadTime" must {

    "return Right(()) when a readTime has been added to the db" in new UpdateReadTimeTestContext(
      addReadTime = Right(())) {
      val result: Either[SecureMessageError, Unit] =
        await(
          service.updateReadTime(
            "cdcm",
            "D-80542-20201120",
            customerEnrolment,
            DateTime.parse("2020-11-10T15:00:18.000+0000")
          ))
      result mustBe Right(())
    }

    "return Left(storeError) when something went wrong with adding a readTime to the db" in new UpdateReadTimeTestContext(
      addReadTime = Left(StoreError("errMsg", None))) {
      val result: Either[SecureMessageError, Unit] =
        await(
          service.updateReadTime(
            "cdcm",
            "D-80542-20201120",
            customerEnrolment,
            DateTime.parse("2020-11-10T15:00:18.000+0000")))
      result mustBe Left(StoreError("errMsg", None))
    }
  }
  @SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
  class CreateMessageTestContext(
    dbInsertResult: Either[SecureMessageError, Unit] = Right(()),
    getEmailResult: Either[EmailLookupError, EmailAddress] = Right(EmailAddress("joeblogs@yahoo.com"))) {
    when(mockRepository.insertIfUnique(any[Conversation])(any[ExecutionContext]))
      .thenReturn(Future(dbInsertResult))
    when(mockChannelPreferencesConnector.getEmailForEnrolment(any[Identifier])(any[HeaderCarrier]))
      .thenReturn(Future(getEmailResult))
  }

  @SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
  class GetConversationTestContext(getConversationResult: Either[ConversationNotFound, Conversation]) {
    when(mockRepository.getConversation(any[String], any[String], any[Set[Identifier]])(any[ExecutionContext]))
      .thenReturn(Future(getConversationResult))
  }

  @SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
  class AddCustomerMessageTestContext(
    getConversationResult: Either[ConversationNotFound, Conversation],
    addMessageResult: Either[SecureMessageError, Unit] = Right(()))
      extends TestHelpers {
    when(mockRepository.getConversation(any[String], any[String], any[Set[Identifier]])(any[ExecutionContext]))
      .thenReturn(Future(getConversationResult))
    when(mockRepository.addMessageToConversation(any[String], any[String], any[Message])(any[ExecutionContext]))
      .thenReturn(Future(addMessageResult))
  }

  @SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
  class AddCaseworkerMessageTestContent(
    getConversationResult: Either[ConversationNotFound, Conversation] = Right(cnvWithNoEmail),
    addMessageResult: Either[SecureMessageError, Unit] = Right(()),
    sendEmailResult: Either[EmailSendingError, Unit] = Right(())) {
    when(mockRepository.getConversation(any[String], any[String], any[Set[Identifier]])(any[ExecutionContext]))
      .thenReturn(Future(getConversationResult))
    when(mockRepository.addMessageToConversation(any[String], any[String], any[Message])(any[ExecutionContext]))
      .thenReturn(Future(addMessageResult))
    when(mockEmailConnector.send(any[EmailRequest])(any[HeaderCarrier])).thenReturn(Future(sendEmailResult))
  }

  @SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
  class UpdateReadTimeTestContext(
    getConversationResult: Either[ConversationNotFound, Conversation] = Right(cnvWithNoEmail),
    addReadTime: Either[StoreError, Unit]) {
    when(mockRepository.getConversation(any[String], any[String], any[Set[Identifier]])(any[ExecutionContext]))
      .thenReturn(Future(getConversationResult))
    when(mockRepository.addReadTime(any[String], any[String], any[Int], any[DateTime])(any[ExecutionContext]))
      .thenReturn(Future(addReadTime))
  }
}

@SuppressWarnings(Array("org.wartremover.warts.TraversableOps", "org.wartremover.warts.NonUnitStatements"))
trait TestHelpers extends MockitoSugar {
  import uk.gov.hmrc.auth.core.Enrolment
  val eORINumber: Identifier = Identifier("EORINumber", "GB123456789000000", Some("HMRC-CUS-ORG"))
  val participant: Participant = Participant(1, ParticipantType.Customer, eORINumber, None, None, None, None)
  val mockEisConnector: EISConnector = mock[EISConnector]
  val mockRepository: ConversationRepository = mock[ConversationRepository]
  val mockEmailConnector: EmailConnector = mock[EmailConnector]
  when(mockEmailConnector.send(any[EmailRequest])(any[HeaderCarrier])).thenReturn(Future.successful(Right(())))
  val mockChannelPreferencesConnector: ChannelPreferencesConnector = mock[ChannelPreferencesConnector]
  val service: SecureMessageService =
    new SecureMessageService(mockRepository, mockEmailConnector, mockChannelPreferencesConnector, mockEisConnector)
  val enrolments: Enrolments = Enrolments(
    Set(Enrolment("HMRC-CUS-ORG", Vector(EnrolmentIdentifier("EORINumber", "GB7777777777")), "Activated", None)))
  val identifier: Identifier = Identifier("EORINumber", "GB123456789000000", Some("HMRC-CUS-ORG"))
  val systemIdentifier: Identifier = Identifier("cdcm", "123", None)
  val systemParticipant: Participant =
    Participant(1, ParticipantType.System, systemIdentifier, None, None, None, None)
  val customerParticipant: Participant = Participant(
    2,
    ParticipantType.Customer,
    Identifier("EORINumber", "GB1234567890", Some("HMRC-CUS-ORG")),
    None,
    Some(EmailAddress("test@test.com")),
    None,
    None)
  val message: Message = Message(2, DateTime.now, "PHA+Q2FuIEkgaGF2ZSBteSB0YXggbW9uZXkgcGxlYXNlPzwvcD4==")
  val customerMessage: CustomerMessage = CustomerMessage("PGRpdj5IZWxsbzwvZGl2Pg==")
  val mockEnrolments: Enrolments = mock[Enrolments]
  val customerEnrolment: Enrolments = Enrolments(
    Set(Enrolment("HMRC-CUS-ORG", Vector(EnrolmentIdentifier("EORINumber", "GB1234567890")), "Activated", None)))

  def caseWorkerMessage(content: String): CaseworkerMessage =
    CaseworkerMessage(
      CaseworkerMessage.Sender(CaseworkerMessage.System(CaseworkerMessage.SystemIdentifier("cdcm", "123"))),
      content
    )
  val listOfCoreConversation = List(
    ConversationUtil.getFullConversation("D-80542-20201120", "HMRC-CUS-ORG", "EORINumber", "GB1234567890"))
  val cnvWithNoEmail: Conversation =
    Resources.readJson("model/api/cdcm/write/conversation-request.json").as[Conversation]
  val cnvWithNoCustomer: Conversation = cnvWithNoEmail.copy(participants = List(cnvWithNoEmail.participants.head))
  val cnvWithMultipleCustomers: Conversation =
    ConversationUtil.getConversationRequestWithMultipleCustomers.asConversation("cdcm", "123")
}
