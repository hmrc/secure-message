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

package uk.gov.hmrc.securemessage.controllers

import akka.stream.Materializer
import javax.naming.CommunicationException
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.{ any, eq => eqTo }
import org.mockito.Mockito.when
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.http.ContentTypes._
import play.api.http.HeaderNames._
import play.api.http.Status._
import play.api.i18n.Messages
import play.api.libs.json.{ JsObject, JsValue, Json }
import play.api.mvc.{ Request, Result }
import play.api.test.Helpers.{ POST, PUT, contentAsJson, contentAsString, defaultAwaitTimeout, status, stubMessages }
import play.api.test.{ FakeHeaders, FakeRequest, Helpers, NoMaterializer }
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.Retrieval
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.securemessage._
import uk.gov.hmrc.securemessage.controllers.models.generic
import uk.gov.hmrc.securemessage.controllers.models.generic._
import uk.gov.hmrc.securemessage.helpers.Resources
import uk.gov.hmrc.securemessage.models.core.Conversation
import uk.gov.hmrc.securemessage.repository.ConversationRepository
import uk.gov.hmrc.securemessage.services.SecureMessageService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ ExecutionContext, Future }

@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements", "org.wartremover.warts.OptionPartial"))
class SecureMessageControllerSpec extends PlaySpec with ScalaFutures with MockitoSugar with OptionValues {

  implicit val mat: Materializer = NoMaterializer
  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit val messages: Messages = stubMessages()
  private val testEnrolment = CustomerEnrolment("HMRC-CUS-ORG", "EORINumber", "GB123456789")

  "createConversation" must {

    "return CREATED (201) when sent a request with all optional fields populated" in new CreateConversationTestCase(
      requestBody = Resources.readJson("model/api/create-conversation-full.json"),
      expectedResult = Future(Right(()))) {
      private val response = controller.createConversation("cdcm", "123")(fakeRequest)
      status(response) mustBe CREATED
    }

    "return CREATED (201) when sent a request with no optional fields populated" in new CreateConversationTestCase(
      requestBody = Resources.readJson("model/api/create-conversation-minimal.json"),
      expectedResult = Future(Right(()))) {
      private val response = controller.createConversation("cdcm", "123")(fakeRequest)
      status(response) mustBe CREATED
    }

    "return BAD REQUEST (400) when sent a request with required fields missing" in new CreateConversationTestCase(
      requestBody = Json.parse("""{"missing":"data"}""".stripMargin),
      expectedResult = Future(Right(()))) {
      private val response = controller.createConversation("cdcm", "123")(fakeRequest)
      status(response) mustBe BAD_REQUEST
    }

    "return BAD REQUEST (400) when an invalid email address is provided" in new CreateConversationTestCase(
      requestBody = Resources.readJson("model/api/create-conversation-invalid-email.json"),
      expectedResult = Future(Right(()))) {
      private val response = controller.createConversation("cdcm", "123")(fakeRequest)
      status(response) mustBe BAD_REQUEST
    }

    "return CONFLICT (409) when the conversation already exists" in new CreateConversationTestCase(
      requestBody = Resources.readJson("model/api/create-conversation-full.json"),
      expectedResult = Future(Left(DuplicateConversationError("conflict error", None)))
    ) {
      private val response = controller.createConversation("cdcm", "123")(fakeRequest)
      status(response) mustBe CONFLICT
      contentAsJson(response) mustBe Json.toJson(
        "Error on conversation with client: cdcm, conversationId: 123, error message: conflict error")
    }

    "return InternalServerError (500) when there is an error storing the conversation" in new CreateConversationTestCase(
      requestBody = Resources.readJson("model/api/create-conversation-full.json"),
      expectedResult = Future(Left(StoreError("mongo error", None)))) {
      private val response = controller.createConversation("cdcm", "123")(fakeRequest)
      status(response) mustBe INTERNAL_SERVER_ERROR
      contentAsJson(response) mustBe Json.toJson(
        """Error on conversation with client: cdcm, conversationId: 123, error message: mongo error""")
    }

    "return CREATED (201) when there is an error sending the email" in new CreateConversationTestCase(
      requestBody = Resources.readJson("model/api/create-conversation-full.json"),
      expectedResult = Future(Left(EmailSendingError("email error")))) {
      private val response = controller.createConversation("cdcm", "123")(fakeRequest)
      status(response) mustBe CREATED
      contentAsJson(response) mustBe Json.toJson(
        "Error on conversation with client: cdcm, conversationId: 123, error message: email error")
    }

    "return CREATED (201) when no email can be found" in new CreateConversationTestCase(
      requestBody = Resources.readJson("model/api/create-conversation-full.json"),
      expectedResult = Future(Left(NoReceiverEmailError("Verified email address could not be found")))
    ) {
      private val response = controller.createConversation("cdcm", "123")(fakeRequest)
      status(response) mustBe CREATED
      contentAsJson(response) mustBe Json.toJson(
        "Error on conversation with client: cdcm, conversationId: 123, error message: Verified email address could not be found")
    }

    "return InternalServerError (500) if unexpected SecureMessageError returned" in new CreateConversationTestCase(
      requestBody = Resources.readJson("model/api/create-conversation-full.json"),
      expectedResult = Future(Left(new SecureMessageError("some unknown err")))) {
      private val response = controller.createConversation("cdcm", "123")(fakeRequest)
      status(response) mustBe INTERNAL_SERVER_ERROR
      contentAsJson(response) mustBe Json.toJson(
        "Error on conversation with client: cdcm, conversationId: 123, error message: some unknown err")
    }

    "return InternalServerError (500) if an unexpected exception is thrown" in new CreateConversationTestCase(
      requestBody = Resources.readJson("model/api/create-conversation-full.json"),
      expectedResult = Future(Right(()))) {
      when(mockSecureMessageService.createConversation(any[Conversation])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.failed(new Exception("some error")))
      private val response = controller.createConversation("cdcm", "123")(fakeRequest)
      status(response) mustBe INTERNAL_SERVER_ERROR
      contentAsJson(response) mustBe Json.toJson(
        "Error on conversation with client: cdcm, conversationId: 123, error message: some error")
    }

    "return BAD_REQUEST (400) when the message content is not base64 encoded" in new CreateConversationTestCase(
      requestBody = Resources.readJson("model/api/create-conversation-full.json"),
      expectedResult = Future(Left(InvalidContent("Not valid base64 content")))
    ) {
      private val response = controller.createConversation("cdcm", "123")(fakeRequest)
      status(response) mustBe BAD_REQUEST
      contentAsJson(response) mustBe Json.toJson(
        "Error on conversation with client: cdcm, conversationId: 123, error message: Not valid base64 content")
    }

    "return BAD_REQUEST (400) when the message content is not valid HTML" in new CreateConversationTestCase(
      requestBody = Resources.readJson("model/api/create-conversation-full.json"),
      expectedResult = Future(Left(InvalidContent("Not valid html content")))
    ) {
      private val response = controller.createConversation("cdcm", "123")(fakeRequest)
      status(response) mustBe BAD_REQUEST
      contentAsJson(response) mustBe Json.toJson(
        "Error on conversation with client: cdcm, conversationId: 123, error message: Not valid html content")
    }
  }

  "getConversationsFiltered" must {
    "return an OK (200) with a JSON body of a list of conversations when provided with a list of valid query parameters" in new GetConversationsTestCase(
      storedConversationsMetadata = Resources.readJson("model/api/conversations-metadata.json")) {
      val response: Future[Result] = controller
        .getMetadataForConversationsFiltered(
          None,
          Some(List(CustomerEnrolment("HMRC-CUS-ORG", "EORINumber", "GB123456789"))),
          None)
        .apply(FakeRequest("GET", "/"))
      status(response) mustBe OK
      contentAsJson(response).as[List[ConversationMetadata]] must be(conversationsMetadata)
    }

    "return Ok (200) with a JSON body of an empty list when no enrolments provided as query parameters match the ones held in the auth retrievals" in new TestCase(
      Set(CustomerEnrolment("SOME_ENROLMENT_KEY", "SOME_IDENTIFIER_KEY", "SOME_IDENTIFIER_VALUE"))) {
      when(
        mockSecureMessageService
          .getConversationsFiltered(eqTo(Set[CustomerEnrolment]().empty), any[Option[List[Tag]]])(
            any[ExecutionContext],
            any[Messages]))
        .thenReturn(Future(List()))
      val response: Future[Result] = controller
        .getMetadataForConversationsFiltered(None, Some(List(testEnrolment)), None)
        .apply(FakeRequest("GET", "/"))
      status(response) mustBe OK
      contentAsString(response) mustBe "[]"
    }

    "return Bad Request (400) error when invalid query parameters are provided" in new TestCase(
      Set(CustomerEnrolment("SOME_ENROLMENT_KEY", "SOME_IDENTIFIER_KEY", "SOME_IDENTIFIER_VALUE"))) {
      val response: Future[Result] = controller
        .getMetadataForConversationsFiltered(None, Some(List(testEnrolment)), None)
        .apply(FakeRequest("GET", "/some?x=123&Z=12&a=abc&test=ABCDEF"))
      status(response) mustBe BAD_REQUEST
      contentAsString(response) mustBe "\"Invalid query parameter(s) found: [Z, a, test, x]\""
    }
  }

  "getConversation" must {
    "return Ok (200) with a JSON body of a ApiConversations" in new GetConversationTestCase(
      storedConversation = Some(Resources.readJson("model/api/api-conversation.json"))) {
      val response: Future[Result] = controller
        .getConversationContent("cdcm", "D-80542-20201120")
        .apply(FakeRequest("GET", "/"))
      status(response) mustBe OK
      contentAsJson(response).as[ApiConversation] must be(conversation.value)
    }

    "return Ok (200) with a JSON body of a ApiConversations when auth enrolments hold multiple identifiers and enrolments" in new GetConversationTestCase(
      storedConversation = Some(Resources.readJson("model/api/api-conversation.json")),
      Set(
        testEnrolment,
        CustomerEnrolment("HMRC-CUS-ORG", "EORINumber", "GB023456800"),
        CustomerEnrolment("IR-SA", "NINO", "0123456789")
      )
    ) {
      val response: Future[Result] = controller
        .getConversationContent("cdcm", "D-80542-20201120")
        .apply(FakeRequest("GET", "/"))
      status(response) mustBe OK
      contentAsJson(response).as[ApiConversation] must be(conversation.value)
    }

    "return Not Found (404) with a JSON body of No conversation found" in new GetConversationTestCase(
      storedConversation = None) {
      val response: Future[Result] = controller
        .getConversationContent("cdcm", "D-80542-20201120")
        .apply(FakeRequest("GET", "/"))
      status(response) mustBe NOT_FOUND
      contentAsString(response) mustBe "\"No conversation found\""
    }

    "return Unauthorized (401) when no enrolment found" in new TestCase(Set.empty[CustomerEnrolment]) {
      private val response = controller
        .getConversationContent("cdcm", "D-80542-20201120")
        .apply(FakeRequest("GET", "/"))
      status(response) mustBe UNAUTHORIZED
      contentAsString(response) mustBe "\"No enrolment found\""
    }
  }

  "createCaseworkerMessage" must {
    "return CREATED (201) when with valid payload" in new CreateCaseWorkerMessageTestCase(
      requestBody = Resources.readJson("model/api/caseworker-message.json"),
      givenResult = Future(Right(()))) {
      private val response = controller.addCaseworkerMessage("cdcm", "123")(fakeRequest)
      status(response) mustBe CREATED
    }
    "return UNAUTHORIZED (401) when the caseworker is not a conversation participant" in new CreateCaseWorkerMessageTestCase(
      requestBody = Resources.readJson("model/api/caseworker-message.json"),
      givenResult = Future(Left(ParticipantNotFound("Caseworker ID not found")))
    ) {
      private val response = controller.addCaseworkerMessage("cdcm", "123")(fakeRequest)
      status(response) mustBe UNAUTHORIZED
    }
    "return BAD_REQUEST (400) when the message content is not base64 encoded" in new CreateCaseWorkerMessageTestCase(
      requestBody = Resources.readJson("model/api/caseworker-message.json"),
      givenResult = Future.successful(Left(InvalidContent("Not valid base64 content")))
    ) {
      private val response = controller.addCaseworkerMessage("cdcm", "123")(fakeRequest)
      status(response) mustBe BAD_REQUEST
      contentAsString(response) mustBe "\"Error on conversation with client: cdcm, conversationId: 123, error message: Not valid base64 content\""
    }
    "return BAD_REQUEST (400) when the message content is not valid HTML" in new CreateCaseWorkerMessageTestCase(
      requestBody = Resources.readJson("model/api/caseworker-message.json"),
      givenResult = Future.successful(Left(InvalidContent("Not valid HTML content")))
    ) {
      private val response = controller.addCaseworkerMessage("cdcm", "123")(fakeRequest)
      status(response) mustBe BAD_REQUEST
      contentAsString(response) mustBe "\"Error on conversation with client: cdcm, conversationId: 123, error message: Not valid HTML content\""
    }
  }

  "createCustomerMessage" must {
    "return CREATED (201) when a message is successfully added to the conversation" in new CreateCustomerMessageTestCase(
      givenResult = Future.successful(Right(()))) {
      private val response = controller.addCustomerMessage("cdcm", "D-80542-20201120")(fakeRequest)
      status(response) mustBe CREATED
    }
    "return UNAUTHORIZED (401) when the customer is not a conversation participant" in new CreateCustomerMessageTestCase(
      givenResult = Future.successful(Left(ParticipantNotFound("InsufficientEnrolments")))) {
      private val response = controller.addCustomerMessage("cdcm", "D-80542-20201120")(fakeRequest)
      status(response) mustBe UNAUTHORIZED
    }
    "return NOT_FOUND (404) when the conversation ID is not recognised" in new CreateCustomerMessageTestCase(
      givenResult = Future.successful(Left(ConversationNotFound("Conversation ID not known")))) {
      private val response = controller.addCustomerMessage("cdcm", "D-80542-20201120")(fakeRequest)
      status(response) mustBe NOT_FOUND
    }
    "return Bad Gateway (502) when the message cannot be forwarded to EIS" in new CreateCustomerMessageTestCase(
      addMessageResult = Future.failed(new CommunicationException("Failed to forward message to EIS"))) {
      private val response = controller.addCustomerMessage("cdcm", "D-80542-20201120")(fakeRequest)
      status(response) mustBe BAD_GATEWAY
    }
  }

  "updateReadTime" must {
    "return CREATED (201) with a JSON body of read time successfully added when a readTime has successfully been created " in new UpdateReadTimeTestCase(
      updateSuccessful = Right(())) {
      val response: Future[Result] = controller.addCustomerReadTime("cdcm", "D-80542-20201120")(fakeRequest)
      status(response) mustBe CREATED
      contentAsString(response) mustBe "\"read time successfully added\""
    }

    "return INTERNAL_SERVER_ERROR (500) with a JSON body of issue with updating read time" in new UpdateReadTimeTestCase(
      updateSuccessful = Left(StoreError("errMsg", None))) {
      val response: Future[Result] = controller.addCustomerReadTime("cdcm", "D-80542-20201120")(fakeRequest)
      status(response) mustBe INTERNAL_SERVER_ERROR
      contentAsString(response) mustBe "\"Error on conversation with client: cdcm, conversationId: D-80542-20201120, error message: errMsg\""
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  class TestCase(authEnrolments: Set[CustomerEnrolment] = Set(testEnrolment)) {
    val mockRepository: ConversationRepository = mock[ConversationRepository]
    val mockAuthConnector: AuthConnector = mock[AuthConnector]
    val mockSecureMessageService: SecureMessageService = mock[SecureMessageService]
    when(mockRepository.insertIfUnique(any[Conversation])(any[ExecutionContext]))
      .thenReturn(Future.successful(Right(())))
    val controller =
      new SecureMessageController(Helpers.stubControllerComponents(), mockAuthConnector, mockSecureMessageService)

    val enrolments: Set[Enrolment] = authEnrolments.map(
      enrolment =>
        Enrolment(
          key = enrolment.key,
          identifiers = Seq(EnrolmentIdentifier(enrolment.name, enrolment.value)),
          state = "",
          None))

    when(
      mockAuthConnector
        .authorise(any[Predicate], any[Retrieval[Enrolments]])(any[HeaderCarrier], any[ExecutionContext]))
      .thenReturn(Future.successful(Enrolments(enrolments)))
  }

    private val fullConversationJson = Resources.readJson("model/api/create-conversation-full.json")
    val fullConversationfakeRequest: FakeRequest[JsValue] = FakeRequest(
      method = PUT,
      uri = routes.SecureMessageController.createConversation("cdcm", "123").url,
      headers = FakeHeaders(Seq(CONTENT_TYPE -> JSON)),
      body = fullConversationJson
    )
  }
  @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
  class CreateConversationTestCase(requestBody: JsValue, expectedResult: Future[Either[SecureMessageError, Unit]])
      extends TestCase {
    val fakeRequest: FakeRequest[JsValue] = FakeRequest(
      method = PUT,
      uri = routes.SecureMessageController.createConversation("cdcm", "123").url,
      headers = FakeHeaders(Seq(CONTENT_TYPE -> JSON)),
      body = requestBody
    )

    when(mockSecureMessageService.createConversation(any[Conversation])(any[HeaderCarrier], any[ExecutionContext]))
      .thenReturn(expectedResult)
  }

  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  class CreateCustomerMessageTestCase(givenResult: Future[Either[SecureMessageError, Unit]]) extends TestCase {
    val fakeRequest: FakeRequest[JsObject] = FakeRequest(
      method = POST,
      uri = routes.SecureMessageController.addCustomerMessage("cdcm", "D-80542-20201120").url,
      headers = FakeHeaders(Seq(CONTENT_TYPE -> JSON)),
      body = Json.obj("content" -> "PGRpdj5IZWxsbzwvZGl2Pg==")
    )
    when(
      mockSecureMessageService
        .addCustomerMessageToConversation(any[String], any[String], any[CustomerMessageRequest], any[Enrolments])(
          any[ExecutionContext])).thenReturn(givenResult)
  }

  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  class GetConversationsTestCase(
    storedConversationsMetadata: JsValue,
    authEnrolments: Set[CustomerEnrolment] = Set(testEnrolment),
    customerEnrolments: Set[CustomerEnrolment] = Set(testEnrolment))
      extends TestCase(authEnrolments) {
    val conversationsMetadata: List[ConversationMetadata] = storedConversationsMetadata.as[List[ConversationMetadata]]
    when(mockSecureMessageService
      .getConversationsFiltered(eqTo(customerEnrolments), any[Option[List[Tag]]])(any[ExecutionContext], any[Messages]))
      .thenReturn(Future(conversationsMetadata))
  }

  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  class GetConversationTestCase(
    storedConversation: Option[JsValue],
    authEnrolments: Set[CustomerEnrolment] = Set(testEnrolment))
      extends TestCase(authEnrolments) {
    val conversation: Option[ApiConversation] = storedConversation.map(_.as[ApiConversation])
    when(
      mockSecureMessageService.getConversation(any[String], any[String], any[Set[generic.CustomerEnrolment]])(
        any[ExecutionContext]))
      .thenReturn(Future(conversation))
  }

  class CreateCaseWorkerMessageTestCase(requestBody: JsValue, givenResult: Future[Either[SecureMessageError, Unit]])
      extends TestCase {
    val fakeRequest: FakeRequest[JsValue] = FakeRequest(
      method = POST,
      uri = routes.SecureMessageController.addCaseworkerMessage("cdcm", "123").url,
      headers = FakeHeaders(Seq(CONTENT_TYPE -> JSON)),
      body = requestBody
    )
    when(
      mockSecureMessageService.addCaseWorkerMessageToConversation(
        any[String],
        any[String],
        any[CaseworkerMessageRequest])(any[ExecutionContext], any[HeaderCarrier])).thenReturn(givenResult)
  }

  class UpdateReadTimeTestCase(updateSuccessful: Either[SecureMessageError, Unit]) extends TestCase {
    when(
      mockSecureMessageService.updateReadTime(any[String], any[String], any[Enrolments], any[DateTime])(
        any[ExecutionContext]))
      .thenReturn(Future(updateSuccessful))
    val fakeRequest: FakeRequest[JsValue] = FakeRequest(
      method = POST,
      uri = routes.SecureMessageController.addCustomerReadTime("cdcm", "123").url,
      headers = FakeHeaders(Seq(CONTENT_TYPE -> JSON)),
      body = Json.toJson(ReadTime(DateTime.now))
    )
  }
}
