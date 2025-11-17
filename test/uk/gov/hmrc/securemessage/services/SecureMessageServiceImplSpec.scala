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

package uk.gov.hmrc.securemessage.services

import com.mongodb.client.result.DeleteResult
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.testkit.NoMaterializer
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.{ any, eq as meq }
import org.mockito.Mockito.{ never, times, verify, when }
import org.mongodb.scala.bson.ObjectId
import org.scalatest.EitherValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.i18n.{ Lang, Messages, MessagesApi }
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{ JsObject, Json }
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.auth.core.{ Enrolment, EnrolmentIdentifier, Enrolments }
import uk.gov.hmrc.common.message.model.{ Language, MessagesCount }
import uk.gov.hmrc.common.message.emailaddress.EmailAddress
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.securemessage.connectors.{ AuthIdentifiersConnector, ChannelPreferencesConnector, EISConnector, EmailConnector }
import uk.gov.hmrc.securemessage.controllers.SecureMessageUtil
import uk.gov.hmrc.securemessage.controllers.model.cdcm.read.ConversationMetadata
import uk.gov.hmrc.securemessage.controllers.model.cdcm.write.CaseworkerMessage
import uk.gov.hmrc.securemessage.controllers.model.common.write.CustomerMessage
import uk.gov.hmrc.securemessage.handlers.MessageBroker
import uk.gov.hmrc.securemessage.helpers.{ ConversationUtil, MessageUtil, Resources }
import uk.gov.hmrc.securemessage.models.core.Conversation.*
import uk.gov.hmrc.securemessage.models.core.*
import uk.gov.hmrc.securemessage.models.v4.{ ExtraAlertConfig, SecureMessage }
import uk.gov.hmrc.securemessage.models.{ EmailRequest, QueryMessageWrapper, Tags }
import uk.gov.hmrc.securemessage.repository.{ ConversationRepository, MessageRepository }
import uk.gov.hmrc.securemessage.{ DuplicateConversationError, EmailLookupError, NoReceiverEmailError, SecureMessageError, * }

import java.time.format.DateTimeFormatter
import java.time.{ Duration, Instant, OffsetDateTime, ZoneId, ZoneOffset }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ ExecutionContext, Future }

//TODO: move test data and mocks to TextContexts
class SecureMessageServiceImplSpec extends PlaySpec with ScalaFutures with TestHelpers with UnitTest with EitherValues {

  val dtf: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS").withZone(ZoneId.from(ZoneOffset.UTC))

  val app = new GuiceApplicationBuilder().build()
  val messagesApi: MessagesApi = app.injector.instanceOf[MessagesApi]

  "createConversation" must {

    "return SecureMessageException when no email address is provided and cannot be found in cds" in new CreateMessageTestContext(
      getEmailResult = Left(EmailLookupError(""))
    ) {
      private val result = service.createConversation(cnvWithNoEmail).futureValue
      result.swap.toOption.get.message must startWith("Email lookup failed for:")
    }

    "return Right when no email address is provided but is found in the CDS lookup" in new CreateMessageTestContext {
      private val result = service.createConversation(cnvWithNoEmail).futureValue
      result mustBe Right(())
    }

    "return an error message when a conversation already exists for this client and conversation ID" in new CreateMessageTestContext(
      dbInsertResult = Left(DuplicateConversationError("errMsg", None))
    ) {
      private val result = service.createConversation(cnvWithNoEmail).futureValue
      result mustBe Left(DuplicateConversationError("errMsg", None))
    }

    "return NoReceiverEmailError if there are no customer participants" in new CreateMessageTestContext {
      private val result =
        service.createConversation(cnvWithNoCustomer).futureValue
      result mustBe Left(NoReceiverEmailError("Email lookup failed for: CustomerParticipants(List(),List())"))
    }

    "return NoReceiverEmailError for just the customer with no email when we have multiple customer participants" in new CreateMessageTestContext(
      getEmailResult = Left(EmailLookupError("Some error"))
    ) {
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
      result.futureValue mustBe Left(
        InvalidContent(
          "Html contains disallowed tags, attributes or protocols within the tags: matt. For allowed elements see class org.jsoup.safety.Safelist.relaxed()"
        )
      )
    }

    "send enrolmentString to email" in new TestHelpers {
      service.createConversation(conversation).futureValue

      verify(mockEmailConnector, times(1)).send(
        EmailRequest(
          List(EmailAddress("test@test.com")),
          "emailTemplateId",
          Map("param1" -> "value1", "param2" -> "value2"),
          Some(Tags(None, None, Some("HMRC-CUS-ORG~EORINumber~GB1234567890")))
        )
      )
    }

    "send enrolmentString None if enrolment is empty" in new TestHelpers {
      val customerP = Participant(
        2,
        ParticipantType.Customer,
        Identifier(identifierName, identifierValue90, None),
        None,
        Some(EmailAddress("test@test.com")),
        None,
        None
      )
      service.createConversation(conversation.copy(participants = List(customerP))).futureValue
      verify(mockEmailConnector, times(1)).send(
        EmailRequest(
          List(EmailAddress("test@test.com")),
          "emailTemplateId",
          Map("param1" -> "value1", "param2" -> "value2"),
          None
        )
      )
    }
  }

  "getConversations" must {

    "return a list of ConversationMetaData when presented with one customer enrolment and no tags for a filter" in {
      when(
        mockConversationRepository.getConversations(
          ArgumentMatchers.eq(Set(Identifier("EORIName", "GB7777777777", Some("HMRC-CUS_ORG")))),
          ArgumentMatchers.eq(None)
        )(any[ExecutionContext])
      )
        .thenReturn(Future.successful(conversations))
      val filters =
        Filters(None, Some(List(CustomerEnrolment("HMRC-CUS_ORG", "EORIName", "GB7777777777"))), None)
      val result = await(service.getConversations(authEnrolmentsFrom(filters.enrolmentsFilter), filters))
      val metadata: ConversationMetadata = ConversationMetadata(
        "CDCM",
        "D-80542-20201120",
        "MRN: 19GB4S24GC3PPFGVR7",
        OffsetDateTime.parse("2020-11-10T15:00:01.000", dtf).toInstant,
        Some("CDS Exports Team"),
        unreadMessages = false,
        1
      )
      result mustBe
        List(metadata)
    }

    "return a list of ConversationMetaData when presented with one customer enrolment and one tag for a filter" in {
      when(
        mockConversationRepository.getConversations(
          ArgumentMatchers.eq(Set(Identifier("EORIName", "GB7777777777", Some("HMRC-CUS_ORG")))),
          ArgumentMatchers.eq(Some(List(FilterTag("notificationType", "CDS Exports"))))
        )(any[ExecutionContext])
      )
        .thenReturn(Future.successful(conversations))
      val filters = Filters(
        None,
        Some(List(CustomerEnrolment("HMRC-CUS_ORG", "EORIName", "GB7777777777"))),
        Some(List(FilterTag("notificationType", "CDS Exports")))
      )
      val result = await(service.getConversations(authEnrolmentsFrom(filters.enrolmentsFilter), filters))
      result mustBe
        List(
          ConversationMetadata(
            "CDCM",
            "D-80542-20201120",
            "MRN: 19GB4S24GC3PPFGVR7",
            OffsetDateTime.parse("2020-11-10T15:00:01.000", dtf).toInstant,
            Some("CDS Exports Team"),
            unreadMessages = false,
            1
          )
        )
    }

    "return a list of ConversationMetaData in the order of latest message in the list" in {
      val listOfCoreConversation =
        List(
          ConversationUtil.getFullConversation(
            new ObjectId(),
            "D-80542-20201120",
            "HMRC-CUS-ORG",
            "EORINumber",
            "GB1234567890",
            messageCreationDate = "2020-11-08T15:00:00.000"
          ),
          ConversationUtil.getFullConversation(
            new ObjectId(),
            "D-80542-20201120",
            "HMRC-CUS-ORG",
            "EORINumber",
            "GB1234567890",
            messageCreationDate = "2020-11-10T15:00:00.000"
          ),
          ConversationUtil.getFullConversation(
            new ObjectId(),
            "D-80542-20201120",
            "HMRC-CUS-ORG",
            "EORINumber",
            "GB1234567890",
            messageCreationDate = "2020-11-09T15:00:00.000"
          )
        )

      when(
        mockConversationRepository.getConversations(
          ArgumentMatchers.eq(Set(Identifier("EORIName", "GB7777777777", Some("HMRC-CUS_ORG")))),
          ArgumentMatchers.eq(Some(List(FilterTag("notificationType", "CDS Exports"))))
        )(any[ExecutionContext])
      )
        .thenReturn(Future.successful(listOfCoreConversation))

      val filters = Filters(
        None,
        Some(List(CustomerEnrolment("HMRC-CUS_ORG", "EORIName", "GB7777777777"))),
        Some(List(FilterTag("notificationType", "CDS Exports")))
      )
      val result = await(service.getConversations(authEnrolmentsFrom(filters.enrolmentsFilter), filters))
      result mustBe
        List(
          ConversationMetadata(
            "CDCM",
            "D-80542-20201120",
            "MRN: 19GB4S24GC3PPFGVR7",
            OffsetDateTime.parse("2020-11-10T15:00:00.000", dtf).toInstant,
            Some("CDS Exports Team"),
            unreadMessages = false,
            1
          ),
          ConversationMetadata(
            "CDCM",
            "D-80542-20201120",
            "MRN: 19GB4S24GC3PPFGVR7",
            OffsetDateTime.parse("2020-11-09T15:00:00.000", dtf).toInstant,
            Some("CDS Exports Team"),
            unreadMessages = false,
            1
          ),
          ConversationMetadata(
            "CDCM",
            "D-80542-20201120",
            "MRN: 19GB4S24GC3PPFGVR7",
            OffsetDateTime.parse("2020-11-08T15:00:00.000", dtf).toInstant,
            Some("CDS Exports Team"),
            unreadMessages = false,
            1
          )
        )
    }
  }

  "getConversation by id" must {
    val hmrcCusOrg = "HMRC-CUS-ORG"
    val conversationId = "D-80542-20201120"
    val eoriName = "EORIName"
    val enrolmentValue = "GB7777777777"
    val id = new ObjectId()
    "return a message with ApiConversation" in new GetConversationByIDTestContext(
      getConversationResult = Right(
        ConversationUtil
          .getFullConversation(id, conversationId, hmrcCusOrg, eoriName, enrolmentValue)
      )
    ) {
      val result =
        await(service.getConversation(id, Set(CustomerEnrolment(hmrcCusOrg, eoriName, enrolmentValue)))).toOption.get

      result.client mustBe "CDCM"
      result.messages.size mustBe 1
      result.subject mustBe "MRN: 19GB4S24GC3PPFGVR7"
    }

    "not return a conversation on add readTime error" in new GetConversationByIDWithReadTimeErrorTestContext(
      getConversationResult = Right(
        ConversationUtil
          .getFullConversation(id, conversationId, hmrcCusOrg, eoriName, enrolmentValue)
      )
    ) {
      val result =
        await(service.getConversation(id, Set(CustomerEnrolment(hmrcCusOrg, eoriName, enrolmentValue))))

      result.left.value.message mustBe "Can not store read time"
    }

    "return a Left(ConversationNotFound)" in {
      when(
        mockConversationRepository
          .getConversation(any[ObjectId], any[Set[Identifier]])(any[ExecutionContext])
      )
        .thenReturn(
          Future(
            Left(
              MessageNotFound(
                "Conversation not found for client: cdcm, conversationId: D-80542-20201120, enrolment: GB1234567890"
              )
            )
          )
        )
      val result = await(
        service
          .getConversation(new ObjectId(), Set(CustomerEnrolment("HMRC-CUS_ORG", "EORIName", "GB7777777777")))
      )
      result mustBe
        Left(
          MessageNotFound(
            s"Conversation not found for client: cdcm, conversationId: D-80542-20201120, enrolment: GB1234567890"
          )
        )
    }
  }

  "getMessage by id" must {
    "return a message with ApiLetter" in {
      when(mockMessageRepository.getLetter(any[ObjectId], any[Set[Identifier]])(any[ExecutionContext]))
        .thenReturn(Future(Right(letter)))
      when(mockMessageRepository.addReadTime(any[ObjectId])(any[ExecutionContext]))
        .thenReturn(Future(Right(letter)))
      val result = await(
        service
          .getLetter(new ObjectId(), Set(CustomerEnrolment("HMRC-CUS_ORG", "EORIName", "GB7777777777")))
      )
      result.toOption.get.subject mustBe "subject"
      result.toOption.get.content mustBe "content"
    }

    "return a Left(LetterNotFound)" in {
      when(mockMessageRepository.getLetter(any[ObjectId], any[Set[Identifier]])(any[ExecutionContext]))
        .thenReturn(Future(Left(MessageNotFound("Letter not found"))))
      when(mockMessageRepository.addReadTime(any[ObjectId])(any[ExecutionContext]))
        .thenReturn(Future(Right(letter)))
      val result = await(
        service
          .getLetter(new ObjectId(), Set(CustomerEnrolment("HMRC-CUS_ORG", "EORIName", "GB7777777777")))
      )
      result mustBe
        Left(MessageNotFound(s"Letter not found"))
    }

    "return a left if update readTime fails" in {
      when(mockMessageRepository.getLetter(any[ObjectId], any[Set[Identifier]])(any[ExecutionContext]))
        .thenReturn(Future(Right(MessageUtil.getMessage("subject", "content"))))
      when(mockMessageRepository.addReadTime(any[ObjectId])(any[ExecutionContext]))
        .thenReturn(Future(Left(StoreError("cant store readTime", None))))
      val result = await(
        service
          .getLetter(new ObjectId(), Set(CustomerEnrolment("HMRC-CUS_ORG", "EORIName", "GB7777777777")))
      )
      result.left.value.message mustBe "cant store readTime"
    }
  }

  "getContentBy when both English/Welsh contents are available" should {
    val v4JsonMessageBoth: JsObject =
      Resources.readJson("model/core/v4/valid_message.json").as[JsObject] + ("_id" -> Json
        .toJson(new ObjectId))
    val v4MessageBoth: SecureMessage = v4JsonMessageBoth.as[SecureMessage]

    "return English content when language set as English" in {
      implicit val messages: Messages = messagesApi.preferred(Seq(Lang("en")))
      when(mockSecureMessageUtil.findById(any[ObjectId])).thenReturn(Future(Some(v4MessageBoth)))
      val result = await(service.getContentBy(new ObjectId())).get
      result must include("""<h1 lang="en" class="govuk-heading-xl">Reminder to file a Self Assessment return</h1>""")
    }

    "return Welsh content when language set as Welsh" in {
      implicit val messages: Messages = messagesApi.preferred(Seq(Lang("cy")))
      when(mockSecureMessageUtil.findById(any[ObjectId])).thenReturn(Future(Some(v4MessageBoth)))
      val result = await(service.getContentBy(new ObjectId())).get
      result must include(
        """<h1 lang="cy" class="govuk-heading-xl">Nodyn atgoffa i ffeilio ffurflen Hunanasesiad</h1>"""
      )
    }
  }

  "getContentBy when only English content is available" should {
    val v4JsonMessageEnglish: JsObject =
      Resources.readJson("model/core/v4/valid_message_english_only_content.json").as[JsObject] + ("_id" -> Json
        .toJson(new ObjectId))
    val v4MessageEnglish: SecureMessage = v4JsonMessageEnglish.as[SecureMessage]

    "return English content when language set as English" in {
      implicit val messages: Messages = messagesApi.preferred(Seq(Lang("en")))
      when(mockSecureMessageUtil.findById(any[ObjectId])).thenReturn(Future(Some(v4MessageEnglish)))
      val result = await(service.getContentBy(new ObjectId())).get
      result must include("""<h1 lang="en" class="govuk-heading-xl">Reminder to file a Self Assessment return</h1>""")
    }

    "return English content even when language is set as Welsh" in {
      implicit val messages: Messages = messagesApi.preferred(Seq(Lang("cy")))
      when(mockSecureMessageUtil.findById(any[ObjectId])).thenReturn(Future(Some(v4MessageEnglish)))
      val result = await(service.getContentBy(new ObjectId())).get
      result must not include """<h1 lang="cy" class="govuk-heading-xl">Reminder to file a Self Assessment return</h1>"""
      result must include("""<h1 lang="en" class="govuk-heading-xl">Reminder to file a Self Assessment return</h1>""")
    }
  }

  "getContentBy when only Welsh content is available" should {
    val v4JsonMessageWelsh: JsObject =
      Resources.readJson("model/core/v4/valid_message_welsh_only_content.json").as[JsObject] + ("_id" -> Json
        .toJson(new ObjectId))
    val v4MessageWelsh: SecureMessage = v4JsonMessageWelsh.as[SecureMessage]

    "return Welsh content when language set as Welsh" in {
      implicit val messages: Messages = messagesApi.preferred(Seq(Lang("cy")))
      when(mockSecureMessageUtil.findById(any[ObjectId])).thenReturn(Future(Some(v4MessageWelsh)))
      val result = await(service.getContentBy(new ObjectId())).get
      result must include(
        """<h1 lang="cy" class="govuk-heading-xl">Nodyn atgoffa i ffeilio ffurflen Hunanasesiad</h1>"""
      )
    }

    "return Welsh content even when language is set as English" in {
      implicit val messages: Messages = messagesApi.preferred(Seq(Lang("en")))
      when(mockSecureMessageUtil.findById(any[ObjectId])).thenReturn(Future(Some(v4MessageWelsh)))
      val result = await(service.getContentBy(new ObjectId())).get
      result must include(
        """<h1 lang="cy" class="govuk-heading-xl">Nodyn atgoffa i ffeilio ffurflen Hunanasesiad</h1>"""
      )
      result must not include """<h1 lang="en" class="govuk-heading-xl">Nodyn atgoffa i ffeilio ffurflen Hunanasesiad</h1>"""
    }
  }

  "getSecureMessage by id with enrolments" must {
    implicit val language: Language = Language.Welsh
    "return a v4 message with ApiLetter for given id & enrolments" in {
      when(mockSecureMessageUtil.getMessage(any[ObjectId], any[Set[Identifier]])(any[ExecutionContext]))
        .thenReturn(Future(Right(v4Message)))
      when(mockSecureMessageUtil.addReadTime(any[ObjectId])(any[ExecutionContext]))
        .thenReturn(Future(Right(v4Message)))
      val result = await(
        service
          .getSecureMessage(new ObjectId(), Set(CustomerEnrolment("HMRC-CUS_ORG", "EORIName", "GB7777777777")))
      ).toOption.get
      result.subject mustBe "Nodyn atgoffa i ffeilio ffurflen Hunanasesiad"
    }

    "return a v4 message with ApiLetter for given id" in {
      when(mockSecureMessageUtil.getMessage(any[ObjectId], any[Set[Identifier]])(any[ExecutionContext]))
        .thenReturn(Future(Right(v4Message)))
      when(mockSecureMessageUtil.addReadTime(any[ObjectId])(any[ExecutionContext]))
        .thenReturn(Future(Right(v4Message)))
      val result = await(
        service
          .getSecureMessage(new ObjectId())
      )
      result.get.content mustBe v4Message.content
    }

    "return a Left(MessageNotFound)" in {
      when(mockSecureMessageUtil.getMessage(any[ObjectId], any[Set[Identifier]])(any[ExecutionContext]))
        .thenReturn(Future(Left(MessageNotFound("Message not found"))))
      when(mockSecureMessageUtil.addReadTime(any[ObjectId])(any[ExecutionContext]))
        .thenReturn(Future(Right(())))
      val result = await(
        service
          .getSecureMessage(new ObjectId(), Set(CustomerEnrolment("HMRC-CUS_ORG", "EORIName", "GB7777777777")))
      )
      result mustBe
        Left(MessageNotFound(s"Message not found"))
    }

    "return a left if update readTime fails" in {
      when(mockSecureMessageUtil.getMessage(any[ObjectId], any[Set[Identifier]])(any[ExecutionContext]))
        .thenReturn(Future(Right(v4Message)))
      when(mockSecureMessageUtil.addReadTime(any[ObjectId])(any[ExecutionContext]))
        .thenReturn(Future(Left(StoreError("cant store readTime", None))))
      val result = await(
        service
          .getSecureMessage(new ObjectId(), Set(CustomerEnrolment("HMRC-CUS_ORG", "EORIName", "GB7777777777")))
      )
      result.left.value.message mustBe "cant store readTime"
    }
  }

  "Adding a customer message to a conversation" must {

    "update the database when the customer has a participating enrolment" in new AddCustomerMessageTestContext(
      getConversationResult = Right(conversations.head)
    ) {
      when(mockEisConnector.forwardMessage(any[QueryMessageWrapper])).thenReturn(Future(Right(())))
      await(service.addCustomerMessage(encodedId, customerMessage, enrolments, randomId, Some(reference)))
      verify(mockConversationRepository, times(1))
        .addMessageToConversation(any[String], any[String], any[ConversationMessage])(any[ExecutionContext])
    }

    "return NoParticipantFound if the customer does not have a participating enrolment" in new AddCustomerMessageTestContext(
      getConversationResult = Right(cnvWithNoEmail)
    ) {
      when(mockEnrolments.enrolments)
        .thenReturn(Set(Enrolment("HMRC-CUS-ORG", Seq(EnrolmentIdentifier("EORINumber", "GB123456789000001")), "")))
      await(
        service.addCustomerMessage(encodedId, customerMessage, mockEnrolments, randomId, Some(reference))
      ) mustBe Left(
        ParticipantNotFound(
          "No participant found for client: CDCM, conversationId: 123, identifiers: Set(Identifier(EORINumber,GB123456789000001,Some(HMRC-CUS-ORG)))"
        )
      )
    }

    "return ConversationIdNotFound if the conversation ID is not found" in new AddCustomerMessageTestContext(
      getConversationResult = Left(MessageNotFound("Conversation ID not known"))
    ) {
      when(mockEnrolments.enrolments)
        .thenReturn(Set(Enrolment("HMRC-CUS-ORG", Seq(EnrolmentIdentifier("EORINumber", "GB123456789000001")), "")))
      await(
        service.addCustomerMessage(encodedId, customerMessage, mockEnrolments, randomId, Some(reference))
      ) mustBe Left(MessageNotFound("Conversation ID not known"))
    }

    "return EisForwardingError and don't store the message if the message cannot be forwarded to EIS" in new AddCustomerMessageTestContext(
      getConversationResult = Right(cnvWithNoEmail),
      addMessageResult = Left(EisForwardingError("There was an issue with forwarding the message to EIS"))
    ) {
      when(mockEnrolments.enrolments)
        .thenReturn(Set(Enrolment("HMRC-CUS-ORG", Seq(EnrolmentIdentifier("EORINumber", "GB123456789000000")), "")))
      await(service.addCustomerMessage(encodedId, customerMessage, mockEnrolments, randomId, Some(reference)))
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
          caseWorkerMessage("PHA+Q2FuIEkgaGF2ZSBteSB0YXggbW9uZXkgcGxlYXNlPzwvcD4="),
          randomId,
          Some(reference)
        )
      ) mustBe Right(())
    }

    "return ParticipantNotFound if the caseworker does not have a participating enrolment" in new AddCaseworkerMessageTestContent {
      await(
        service.addCaseWorkerMessageToConversation(
          "CDCM",
          "D-80542-20201120",
          caseWorkerMessage("PHA+Q2FuIEkgaGF2ZSBteSB0YXggbW9uZXkgcGxlYXNlPzwvcD4="),
          randomId,
          Some(reference)
        )
      ) mustBe Left(
        ParticipantNotFound(
          "No participant found for client: CDCM, conversationId: 123, identifiers: Set(Identifier(CDCM,D-80542-20201120,None))"
        )
      )
    }

    "return ConversationIdNotFound if the conversation ID is not found" in new AddCaseworkerMessageTestContent(
      getConversationResult = Left(MessageNotFound("Conversation ID not known"))
    ) {
      await(
        service.addCaseWorkerMessageToConversation(
          "CDCM",
          "D-80542-20201120",
          caseWorkerMessage("QmxhaCBibGFoIGJsYWg="),
          randomId,
          Some(reference)
        )
      ) mustBe Left(MessageNotFound("Conversation ID not known"))
    }

    "return content validation error if message content is invalid" in new AddCaseworkerMessageTestContent {
      await(
        service.addCaseWorkerMessageToConversation(
          "CDCM",
          "D-80542-20201120",
          caseWorkerMessage("PG1hdHQ+Q2FuIEkgaGF2ZSBteSB0YXggbW9uZXkgcGxlYXNlPzwvbWF0dD4="),
          randomId,
          Some(reference)
        )
      ) mustBe
        Left(
          InvalidContent(
            "Html contains disallowed tags, attributes or protocols within the tags: matt. For allowed elements see class org.jsoup.safety.Safelist.relaxed()"
          )
        )
    }
  }

  "getMessages" should {
    "return both conversations and letters sorted in descending order by issue date" in new GetMessagesTestContext() {
      private val result: List[Message] = service.getMessages(enrolments, filters()).futureValue
      result must not be (conversations ++ letters)
      result mustBe (conversations ++ letters ++ List(v4Message)).sortWith { (a, b) =>
        a.issueDate.isAfter(b.issueDate)
      }
    }
    "return just conversations when there are no letters" in new GetMessagesTestContext(
      dbLetters = List.empty,
      v4Messages = List.empty
    ) {
      service.getMessages(enrolments, filters()).futureValue mustBe conversations
    }
    "return just letters when there are no conversations" in new GetMessagesTestContext(
      dbConversations = List.empty,
      v4Messages = List.empty
    ) {
      service.getMessages(enrolments, filters()).futureValue mustBe letters
    }

    "return an empty list when there are neither conversations nor letters" in new GetMessagesTestContext(
      dbConversations = List.empty,
      dbLetters = List.empty,
      v4Messages = List.empty
    ) {
      service.getMessages(enrolments, filters()).futureValue mustBe empty
    }

    "add readTime when there are new messages after last readTime" in new AddReadTimesTestContext {
      val readTimeStamp = OffsetDateTime.parse("2020-11-09T15:00:00.000", dtf).toInstant
      val conversation = ConversationUtil.getFullConversation(
        new ObjectId(),
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
          .value
      )
      verify(mockConversationRepository, times(1))
        .addReadTime(conversation.client, conversation.id, 2, readTimeStamp)
    }

    "add read time when no messages were read" in new AddReadTimesTestContext {
      val readTimeStamp = OffsetDateTime.parse("2020-11-09T15:00:00.000", dtf).toInstant
      val conversation = ConversationUtil.getFullConversation(
        new ObjectId(),
        "D-80542-20201120",
        "HMRC-CUS-ORG",
        "EORINumber",
        "GB1234567890",
        messageCreationDate = "2020-11-08T15:00:00.000",
        readTimes = None
      )
      await(
        service
          .addReadTime(conversation, Set(Identifier("EORINumber", "GB1234567890", Some("HMRC-CUS-ORG"))), readTimeStamp)
          .value
      )
      verify(mockConversationRepository, times(1))
        .addReadTime(conversation.client, conversation.id, 2, readTimeStamp)
    }

    "not add readTime when there are no new messages after last readtime" in new AddReadTimesTestContext {
      val readTimeStamp = OffsetDateTime.parse("2021-11-09T15:00:00.000", dtf).toInstant
      val conversation = ConversationUtil.getFullConversation(
        new ObjectId(),
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
          .value
      )
      verify(mockConversationRepository, times(0))
        .addReadTime(conversation.client, conversation.id, 2, readTimeStamp)
    }
  }

  "getMessagesCount" must {
    "return count" in new AddReadTimesTestContext {
      val result = service.getMessagesCount(enrolments, filters()).futureValue
      result mustBe MessagesCount(3, 2)
    }
  }

  "removeAlerts" must {
    "delete the extra alerts for given message id" in {
      when(mockSecureMessageUtil.extraAlerts).thenReturn(
        List(ExtraAlertConfig("value 1", "value2", Duration.ofSeconds(2)))
      )
      when(mockSecureMessageUtil.removeAlerts(meq(v4Message._id), meq(v4Message.templateId))(any[ExecutionContext]))
        .thenReturn(Future(DeleteResult.acknowledged(1)))

      val result: DeleteResult = await(service.removeAlerts(v4Message))
      result.getDeletedCount mustBe 1
    }
  }
  class AddReadTimesTestContext {
    val mockEisConnector: EISConnector = mock[EISConnector]
    val mockAuditConnector: AuditConnector = mock[AuditConnector]
    val mockConversationRepository: ConversationRepository = mock[ConversationRepository]
    val mockMessageRepository: MessageRepository = mock[MessageRepository]
    val mockSecureMessageUtil: SecureMessageUtil = mock[SecureMessageUtil]
    val mockEmailConnector: EmailConnector = mock[EmailConnector]
    when(mockEmailConnector.send(any[EmailRequest])(any[HeaderCarrier])).thenReturn(Future.successful(Right(())))
    val mockChannelPreferencesConnector: ChannelPreferencesConnector = mock[ChannelPreferencesConnector]
    when(
      mockConversationRepository.addReadTime(any[String], any[String], any[Int], any[Instant])(any[ExecutionContext])
    )
      .thenReturn(Future.successful(Right(())))
    when(
      mockConversationRepository
        .getConversationsCount(any[Set[Identifier]](), any[Option[List[FilterTag]]]())(any[ExecutionContext])
    )
      .thenReturn(Future.successful(MessagesCount(1, 1)))
    when(
      mockMessageRepository
        .getLettersCount(any[Set[Identifier]](), any[Option[List[FilterTag]]]())(any[ExecutionContext])
    )
      .thenReturn(Future.successful(MessagesCount(1, 0)))
    when(
      mockSecureMessageUtil
        .getSecureMessageCount(any[Set[Identifier]](), any[Option[List[FilterTag]]]())(any[ExecutionContext])
    )
      .thenReturn(Future.successful(MessagesCount(1, 1)))

    val service: SecureMessageServiceImpl =
      new SecureMessageServiceImpl(
        mockConversationRepository,
        mockMessageRepository,
        mockSecureMessageUtil,
        mockEmailConnector,
        mockChannelPreferencesConnector,
        mockEisConnector,
        mockAuthConnector,
        mockAuditConnector
      )
  }
  class CreateMessageTestContext(
    dbInsertResult: Either[SecureMessageError, Unit] = Right(()),
    getEmailResult: Either[EmailLookupError, EmailAddress] = Right(EmailAddress("joeblogs@yahoo.com"))
  ) {
    when(mockConversationRepository.insertIfUnique(any[Conversation])(any[ExecutionContext]))
      .thenReturn(Future(dbInsertResult))
    when(mockChannelPreferencesConnector.getEmailForEnrolment(any[Identifier])(any[HeaderCarrier]))
      .thenReturn(Future(getEmailResult))
  }

  class GetConversationTestContext(getConversationResult: Either[MessageNotFound, Conversation]) {
    when(
      mockConversationRepository.getConversation(any[String], any[String], any[Set[Identifier]])(any[ExecutionContext])
    )
      .thenReturn(Future(getConversationResult))
    when(
      mockConversationRepository.addReadTime(any[String], any[String], any[Int], any[Instant])(any[ExecutionContext])
    )
      .thenReturn(Future.successful(Right(())))
  }

  class GetMessagesTestContext(
    dbConversations: List[Conversation] = conversations,
    dbLetters: List[Letter] = letters,
    v4Messages: List[SecureMessage] = List(v4Message)
  ) {
    when(
      mockConversationRepository.getConversations(any[Set[Identifier]], any[Option[List[FilterTag]]])(
        any[ExecutionContext]
      )
    )
      .thenReturn(Future(dbConversations))
    when(mockMessageRepository.getLetters(any[Set[Identifier]], any[Option[List[FilterTag]]])(any[ExecutionContext]))
      .thenReturn(Future(dbLetters))
    when(mockSecureMessageUtil.getMessages(any[Set[Identifier]], any[Option[List[FilterTag]]])(any[ExecutionContext]))
      .thenReturn(Future(v4Messages))
  }

  class GetConversationByIDTestContext(getConversationResult: Either[MessageNotFound, Conversation]) {
    when(mockConversationRepository.getConversation(any[ObjectId], any[Set[Identifier]])(any[ExecutionContext]))
      .thenReturn(Future(getConversationResult))
    when(
      mockConversationRepository.addReadTime(any[String], any[String], any[Int], any[Instant])(any[ExecutionContext])
    )
      .thenReturn(Future.successful(Right(())))
  }

  class GetConversationByIDWithReadTimeErrorTestContext(getConversationResult: Either[MessageNotFound, Conversation]) {
    when(mockConversationRepository.getConversation(any[ObjectId], any[Set[Identifier]])(any[ExecutionContext]))
      .thenReturn(Future(getConversationResult))
    when(
      mockConversationRepository.addReadTime(any[String], any[String], any[Int], any[Instant])(any[ExecutionContext])
    )
      .thenReturn(Future.successful(Left(StoreError("Can not store read time", None))))
  }

  class AddCustomerMessageTestContext(
    getConversationResult: Either[MessageNotFound, Conversation],
    addMessageResult: Either[SecureMessageError, Unit] = Right(())
  ) extends TestHelpers {
    val encodedId: String = "61f802a8cb050005c58cc3c7"
    when(mockConversationRepository.getConversation(any[ObjectId], any[Set[Identifier]])(any[ExecutionContext]))
      .thenReturn(Future(getConversationResult))
    when(
      mockConversationRepository
        .addMessageToConversation(any[String], any[String], any[ConversationMessage])(any[ExecutionContext])
    )
      .thenReturn(Future(addMessageResult))
  }

  class AddCaseworkerMessageTestContent(
    getConversationResult: Either[MessageNotFound, Conversation] = Right(cnvWithNoEmail),
    addMessageResult: Either[SecureMessageError, Unit] = Right(()),
    sendEmailResult: Either[EmailSendingError, Unit] = Right(())
  ) {
    when(
      mockConversationRepository.getConversation(any[String], any[String], any[Set[Identifier]])(any[ExecutionContext])
    )
      .thenReturn(Future(getConversationResult))
    when(
      mockConversationRepository
        .addMessageToConversation(any[String], any[String], any[ConversationMessage])(any[ExecutionContext])
    )
      .thenReturn(Future(addMessageResult))
    when(mockEmailConnector.send(any[EmailRequest])(any[HeaderCarrier])).thenReturn(Future(sendEmailResult))
  }
}

//TODO: change this with specialized TestCases, reuse values: no strings
trait TestHelpers extends MockitoSugar with UnitTest {
  import uk.gov.hmrc.auth.core.Enrolment
  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit val mat: Materializer = NoMaterializer
  implicit val messages: Messages = stubMessages()
  implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
  val objectID: ObjectId = new ObjectId()
  protected val identifierName = "EORINumber"
  protected val identifierValue90 = "GB1234567890"
  protected val identifierEnrolment = "HMRC-CUS-ORG"
  val identifier: Identifier = Identifier(identifierName, identifierValue90, Some(identifierEnrolment))
  val identifierWithNoEnrolment: Identifier = Identifier(identifierName, identifierValue90, Some(identifierEnrolment))
  val participant: Participant = Participant(1, ParticipantType.Customer, identifier, None, None, None, None)
  val mockEisConnector: EISConnector = mock[EISConnector]
  val mockAuditConnector: AuditConnector = mock[AuditConnector]
  val mockAuthConnector: AuthIdentifiersConnector = mock[AuthIdentifiersConnector]
  val mockConversationRepository: ConversationRepository = mock[ConversationRepository]
  val mockMessageRepository: MessageRepository = mock[MessageRepository]
  val mockSecureMessageUtil: SecureMessageUtil = mock[SecureMessageUtil]
  val mockMessageBroker: MessageBroker = mock[MessageBroker]
  val mockEmailConnector: EmailConnector = mock[EmailConnector]
  when(mockEmailConnector.send(any[EmailRequest])(any[HeaderCarrier])).thenReturn(Future.successful(Right(())))
  val mockChannelPreferencesConnector: ChannelPreferencesConnector = mock[ChannelPreferencesConnector]
  when(mockChannelPreferencesConnector.getEmailForEnrolment(any[Identifier])(any[HeaderCarrier]))
    .thenReturn(Future.successful(Right(EmailAddress("test@test.com"))))
  when(mockConversationRepository.insertIfUnique(any[Conversation])(any[ExecutionContext]))
    .thenReturn(Future.successful(Right(())))
  val service: SecureMessageServiceImpl =
    new SecureMessageServiceImpl(
      mockConversationRepository,
      mockMessageRepository,
      mockSecureMessageUtil,
      mockEmailConnector,
      mockChannelPreferencesConnector,
      mockEisConnector,
      mockAuthConnector,
      mockAuditConnector
    )
  val mockEnrolments: Enrolments = mock[Enrolments]
  val enrolments: Enrolments = Enrolments(
    Set(
      Enrolment(identifierEnrolment, Vector(EnrolmentIdentifier(identifierName, identifierValue90)), "Activated", None)
    )
  )
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
    None
  )
  val xRequestId: String = "adsgr24frfvdc829r87rfsdf=="
  val reference: Reference = Reference("X-Request-ID", xRequestId)
  val randomId: String = "6e78776f-48ff-45bd-9da2-926e35519803"
  val message: ConversationMessage =
    ConversationMessage(
      Some(randomId),
      2,
      Instant.now,
      "PHA+Q2FuIEkgaGF2ZSBteSB0YXggbW9uZXkgcGxlYXNlPzwvcD4==",
      Some(reference)
    )
  val customerMessage: CustomerMessage = CustomerMessage("PGRpdj5IZWxsbzwvZGl2Pg==")

  def caseWorkerMessage(content: String): CaseworkerMessage =
    CaseworkerMessage(content)

  protected val conversation: Conversation = ConversationUtil
    .getFullConversation(
      new ObjectId(),
      "D-80542-20201120",
      identifierEnrolment,
      identifierName,
      identifierValue90,
      email = Some(EmailAddress("test@test.com"))
    )
  val conversations = List(conversation)
  val letter: Letter = MessageUtil.getMessage("subject", "content")
  val letters = List(letter)
  val v4JsonMessage: JsObject = Resources.readJson("model/core/v4/valid_message.json").as[JsObject] + ("_id" -> Json
    .toJson(new ObjectId))
  val v4Message: SecureMessage = v4JsonMessage.as[SecureMessage]
  val conversationJson: JsObject = Resources
    .readJson("model/api/cdcm/write/conversation-request.json")
    .as[JsObject] + ("_id" -> Json.toJson(objectID))
  val cnvWithNoEmail: Conversation =
    conversationJson.as[Conversation]
  val cnvWithNoCustomer: Conversation = cnvWithNoEmail.copy(participants = List(cnvWithNoEmail.participants.head))
  val cnvWithMultipleCustomers: Conversation =
    ConversationUtil.getConversationRequestWithMultipleCustomers
      .asConversationWithCreatedDate("CDCM", "123", now, randomId, Some(reference))
}
