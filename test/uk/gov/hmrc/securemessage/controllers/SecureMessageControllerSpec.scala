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
import uk.gov.hmrc.securemessage.controllers.models.generic
import uk.gov.hmrc.securemessage.controllers.models.generic._
import uk.gov.hmrc.securemessage.helpers.Resources
import uk.gov.hmrc.securemessage.models.core.Conversation
import uk.gov.hmrc.securemessage.repository.ConversationRepository
import uk.gov.hmrc.securemessage.services.SecureMessageService
import uk.gov.hmrc.securemessage._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ ExecutionContext, Future }

@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
class SecureMessageControllerSpec extends PlaySpec with ScalaFutures with MockitoSugar with OptionValues {

  implicit val mat: Materializer = NoMaterializer
  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit val messages: Messages = stubMessages()
  private val testEnrolment = CustomerEnrolment("HMRC-CUS-ORG", "EORINumber", "GB123456789")

  "createConversation" must {

    "return Created (201) when sent a request with all optional fields populated" in new CreateConversationTestCase(
      requestBody = Resources.readJson("model/api/create-conversation-full.json")) {
      private val response = controller.createConversation("cdcm", "123")(fakeRequest)
      status(response) mustBe CREATED
    }

    "return Created (201) when sent a request with no optional fields populated" in new CreateConversationTestCase(
      requestBody = Resources.readJson("model/api/create-conversation-minimal.json")) {
      private val response = controller.createConversation("cdcm", "123")(fakeRequest)
      status(response) mustBe CREATED
    }

    "return Bad Request (400) when sent a request with required fields missing" in new CreateConversationTestCase(
      requestBody = Json.parse("""{"missing":"data"}""".stripMargin)) {
      private val response = controller.createConversation("cdcm", "123")(fakeRequest)
      status(response) mustBe BAD_REQUEST
    }

    "return Bad Request (400) when an invalid email address is provided" in new CreateConversationTestCase(
      requestBody = Resources.readJson("model/api/create-conversation-invalid-email.json")) {
      private val response = controller.createConversation("cdcm", "123")(fakeRequest)
      status(response) mustBe BAD_REQUEST
    }

    "return Conflict (409) when the conversation already exists" in new CreateConversationTestCase(
      requestBody = Resources.readJson("model/api/create-conversation-full.json")) {
      when(mockSecureMessageService.createConversation(any[Conversation])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future(Left(DuplicateConversationError("conflict error", None))))
      private val response = controller.createConversation("cdcm", "123")(fakeRequest)
      status(response) mustBe CONFLICT
      contentAsJson(response) mustBe Json.toJson("conflict error")
    }

    "return Internal Server Error (500) when there is an error storing the conversation" in new CreateConversationTestCase(
      requestBody = Resources.readJson("model/api/create-conversation-full.json")) {
      when(mockSecureMessageService.createConversation(any[Conversation])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future(Left(StoreError("mongo error", None))))
      private val response = controller.createConversation("cdcm", "123")(fakeRequest)
      status(response) mustBe INTERNAL_SERVER_ERROR
      contentAsJson(response) mustBe Json.toJson("mongo error")
    }

    "return Created (201) when there is an error sending the email" in new CreateConversationTestCase(
      requestBody = Resources.readJson("model/api/create-conversation-full.json")) {
      when(mockSecureMessageService.createConversation(any[Conversation])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future(Left(EmailError("email error"))))
      private val response = controller.createConversation("cdcm", "123")(fakeRequest)
      status(response) mustBe CREATED
      contentAsJson(response) mustBe Json.toJson("email error")
    }

    "return Created (201) when no email can be found" in new CreateConversationTestCase(
      requestBody = Resources.readJson("model/api/create-conversation-full.json")) {
      when(mockSecureMessageService.createConversation(any[Conversation])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future(Left(NoReceiverEmailError("Verified email address could not be found"))))
      private val response = controller.createConversation("cdcm", "123")(fakeRequest)
      status(response) mustBe CREATED
      contentAsJson(response) mustBe Json.toJson("Verified email address could not be found")
    }

    "return Internal Service Error (500) if unexpected SecureMessageError returned" in new CreateConversationTestCase(
      requestBody = Resources.readJson("model/api/create-conversation-full.json")) {
      when(mockSecureMessageService.createConversation(any[Conversation])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future(Left(new SecureMessageError("some unknown err"))))
      private val response = controller.createConversation("cdcm", "123")(fakeRequest)
      status(response) mustBe INTERNAL_SERVER_ERROR
      contentAsJson(response) mustBe Json.toJson("Error on conversation with id 123: some unknown err")
    }

    "return Internal Server Error (500) if an unexpected exception is thrown" in new CreateConversationTestCase(
      requestBody = Resources.readJson("model/api/create-conversation-full.json")) {
      when(mockSecureMessageService.createConversation(any[Conversation])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.failed(new Exception("some error")))
      private val response = controller.createConversation("cdcm", "123")(fakeRequest)
      status(response) mustBe INTERNAL_SERVER_ERROR
      contentAsJson(response) mustBe Json.toJson("Error on conversation with id 123: some error")
    }

  }

  "getConversationsFiltered" must {
    "return Ok (200) with a JSON body of a list of conversations when provided with a list of valid query parameters" in new GetConversationsTestCase(
      storedConversationsMetadata = Resources.readJson("model/api/conversations-metadata.json")) {
      val response: Future[Result] = controller
        .getMetadataForConversationsFiltered(None, Some(List(testEnrolment)), None)
        .apply(FakeRequest("GET", "/"))
      status(response) mustBe OK
      contentAsJson(response).as[List[ConversationMetadata]] must be(conversationsMetadata)
    }

    "return Unauthorized (401) error when no enrolments provided as query parameters match the ones held in the auth retrievals" in new TestCase(
      Set(CustomerEnrolment("SOME_ENROLMENT_KEY", "SOME_IDENTIFIER_KEY", "SOME_IDENTIFIER_VALUE"))) {
      val response: Future[Result] = controller
        .getMetadataForConversationsFiltered(None, Some(List(testEnrolment)), None)
        .apply(FakeRequest("GET", "/"))
      status(response) mustBe UNAUTHORIZED
      contentAsString(response) mustBe "\"No enrolment found\""
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

    "return Unauthorized (401) when no EORI enrolment found" in new TestCase(Set.empty[CustomerEnrolment]) {
      private val response = controller
        .getConversationContent("cdcm", "D-80542-20201120")
        .apply(FakeRequest("GET", "/"))
      status(response) mustBe UNAUTHORIZED
      contentAsString(response) mustBe "\"No enrolment found\""
    }
  }

  "createCaseworkerMessage" must {
    "return Created (201) when with valid payload" in new CreateCaseWorkerMessageTestCase(
      requestBody = Resources.readJson("model/api/caseworker-message.json")) {
      private val response = controller.createCaseworkerMessage("cdcm", "123")(fakeRequest)
      status(response) mustBe CREATED
    }
  }

  "createCustomerMessage" must {
    "return Created (201) when a message is successfully added to the conversation" in new CreateCustomerMessageTestCase(
      addMessageResult = Future(())) {
      private val response = controller.createCustomerMessage("cdcm", "D-80542-20201120")(fakeRequest)
      status(response) mustBe CREATED
    }
    "return Unauthorized (401) when the customer is not a conversation participant" in new CreateCustomerMessageTestCase(
      addMessageResult = Future.failed(AuthorisationException.fromString("InsufficientEnrolments"))) {
      private val response = controller.createCustomerMessage("cdcm", "D-80542-20201120")(fakeRequest)
      status(response) mustBe UNAUTHORIZED
    }
    "return Not Found (404) when the conversation ID is not recognised" in new CreateCustomerMessageTestCase(
      addMessageResult = Future.failed(new IllegalArgumentException("Conversation ID not known"))) {
      private val response = controller.createCustomerMessage("cdcm", "D-80542-20201120")(fakeRequest)
      status(response) mustBe NOT_FOUND
    }
    "return Bad Gateway (502) when the message cannot be forwarded to EIS" in new CreateCustomerMessageTestCase(
      addMessageResult = Future.failed(new CommunicationException("Failed to forward message to EIS"))) {
      private val response = controller.createCustomerMessage("cdcm", "D-80542-20201120")(fakeRequest)
      status(response) mustBe BAD_GATEWAY
    }
  }

  "updateReadTime" must {
    "return Created (201) with a JSON body of read time successfully added when a readTime has successfully been created " in new UpdateReadTimeTestCase(
      updateSuccessful = true) {
      val response: Future[Result] = controller.addCustomerReadTime("cdcm", "D-80542-20201120")(fakeRequest)
      status(response) mustBe CREATED
      contentAsString(response) mustBe "\"read time successfully added\""
    }

    "return Bad Request (400) with a JSON body of issue with updating read time" in new UpdateReadTimeTestCase(
      updateSuccessful = false) {
      val response: Future[Result] = controller.addCustomerReadTime("cdcm", "D-80542-20201120")(fakeRequest)
      status(response) mustBe BAD_REQUEST
      contentAsString(response) mustBe "\"issue with updating read time\""
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  class TestCase(authEnrolments: Set[CustomerEnrolment] = Set(testEnrolment)) {
    val mockRepository: ConversationRepository = mock[ConversationRepository]
    val mockAuthConnector: AuthConnector = mock[AuthConnector]
    val mockSecureMessageService: SecureMessageService = mock[SecureMessageService]
    when(mockRepository.insertIfUnique(any[Conversation])(any[ExecutionContext]))
      .thenReturn(Future.successful(Right(true)))
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

  class CreateConversationTestCase(requestBody: JsValue) extends TestCase {
    val fakeRequest: FakeRequest[JsValue] = FakeRequest(
      method = PUT,
      uri = routes.SecureMessageController.createConversation("cdcm", "123").url,
      headers = FakeHeaders(Seq(CONTENT_TYPE -> JSON)),
      body = requestBody
    )

    when(mockSecureMessageService.createConversation(any[Conversation])(any[HeaderCarrier], any[ExecutionContext]))
      .thenReturn(Future(Right(true)))
  }

  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  class CreateCustomerMessageTestCase(addMessageResult: Future[Unit]) extends TestCase {
    val fakeRequest: FakeRequest[JsObject] = FakeRequest(
      method = POST,
      uri = routes.SecureMessageController.createCustomerMessage("cdcm", "D-80542-20201120").url,
      headers = FakeHeaders(Seq(CONTENT_TYPE -> JSON)),
      body = Json.obj("content" -> "PGRpdj5IZWxsbzwvZGl2Pg==")
    )
    when(
      mockSecureMessageService
        .addMessageToConversation(any[String], any[String], any[CustomerMessageRequest], any[Enrolments])(
          any[ExecutionContext],
          any[Request[_]])).thenReturn(addMessageResult)
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

  class CreateCaseWorkerMessageTestCase(requestBody: JsValue) extends TestCase {
    val fakeRequest: FakeRequest[JsValue] = FakeRequest(
      method = POST,
      uri = routes.SecureMessageController.createCaseworkerMessage("cdcm", "123").url,
      headers = FakeHeaders(Seq(CONTENT_TYPE -> JSON)),
      body = requestBody
    )
  }

  class UpdateReadTimeTestCase(updateSuccessful: Boolean) extends TestCase {
    when(
      mockSecureMessageService.updateReadTime(any[String], any[String], any[Enrolments], any[DateTime])(
        any[ExecutionContext]))
      .thenReturn(Future.successful(updateSuccessful))
    val fakeRequest: FakeRequest[JsValue] = FakeRequest(
      method = POST,
      uri = routes.SecureMessageController.addCustomerReadTime("cdcm", "123").url,
      headers = FakeHeaders(Seq(CONTENT_TYPE -> JSON)),
      body = Json.toJson(ReadTime(DateTime.now))
    )
  }

}
