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

import java.text.ParseException

import akka.stream.Materializer
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.http.ContentTypes._
import play.api.http.HeaderNames._
import play.api.http.HttpEntity
import play.api.http.Status._
import play.api.libs.json.{ JsObject, JsValue, Json }
import play.api.mvc.{ ResponseHeader, Result }
import play.api.test.Helpers.{ POST, PUT, contentAsJson, contentAsString, defaultAwaitTimeout, status }
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
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ ExecutionContext, Future }

@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
class SecureMessageControllerSpec extends PlaySpec with ScalaFutures with MockitoSugar with OptionValues {

  implicit val mat: Materializer = NoMaterializer
  implicit val hc: HeaderCarrier = HeaderCarrier()

  "createConversation" must {

    "return CREATED (201) when sent a request with all optional fields populated" in new CreateConversationTestCase(
      requestBody = Resources.readJson("model/api/create-conversation-full.json")) {
      private val response = controller.createConversation("cdcm", "123")(fakeRequest)
      status(response) mustBe CREATED
    }

    "return CREATED (201) when sent a request with no optional fields populated" in new CreateConversationTestCase(
      requestBody = Resources.readJson("model/api/create-conversation-minimal.json")) {
      private val response = controller.createConversation("cdcm", "123")(fakeRequest)
      status(response) mustBe CREATED
    }

    "return BAD REQUEST (400) when sent a request with required fields missing" in new CreateConversationTestCase(
      requestBody = Json.parse("""{"missing":"data"}""".stripMargin)) {
      private val response = controller.createConversation("cdcm", "123")(fakeRequest)
      status(response) mustBe BAD_REQUEST
    }

    "return BAD REQUEST (400) when an invalid email address is provided" in new CreateConversationTestCase(
      requestBody = Resources.readJson("model/api/create-conversation-invalid-email.json")) {
      private val response = controller.createConversation("cdcm", "123")(fakeRequest)
      status(response) mustBe BAD_REQUEST
    }

    "return BAD_REQUEST (400) when the message content is not base64 encoded" in new TestCase {
      when(
        mockSecureMessageService.createConversation(any[ConversationRequest], any[String], any[String])(
          any[HeaderCarrier],
          any[ExecutionContext]))
        .thenReturn(Future(Result(new ResponseHeader(BAD_REQUEST), HttpEntity.NoEntity)))
      private val response = controller.createConversation("cdcm", "123")(fullConversationfakeRequest)
      status(response) mustBe BAD_REQUEST
    }

    "return BAD_REQUEST (400) when the message content is not valid HTML" in new TestCase {
      when(
        mockSecureMessageService.createConversation(any[ConversationRequest], any[String], any[String])(
          any[HeaderCarrier],
          any[ExecutionContext]))
        .thenReturn(Future(Result(ResponseHeader(BAD_REQUEST), HttpEntity.NoEntity)))
      private val response = controller.createConversation("cdcm", "123")(fullConversationfakeRequest)
      status(response) mustBe BAD_REQUEST
    }
  }

  "getConversations" must {
    "return an OK (200) with a JSON body of a list of conversations" in new GetConversationsTestCase(
      storedConversationsMetadata = Resources.readJson("model/api/conversations-metadata.json")) {
      val response: Future[Result] =
        controller.getMetadataForConversations("HMRC-CUS-ORG", "EORINumber").apply(FakeRequest("GET", "/"))
      status(response) mustBe OK
      contentAsJson(response).as[List[ConversationMetadata]] must be(conversationsMetadata)
    }

    "return a 401 (UNAUTHORISED) error when no EORI enrolment found" in new TestCase(
      "some other key",
      "another enrolment") {
      val response: Future[Result] =
        controller.getMetadataForConversations("HMRC-CUS-ORG", "EORINumber").apply(FakeRequest("GET", "/"))
      status(response) mustBe UNAUTHORIZED
      contentAsString(response) mustBe "\"No enrolment found\""
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

    "return a 401 (UNAUTHORISED) error when no enrolments provided as query paramters match the ones held in the auth retrievals" in new TestCase(
      "some other key",
      "another enrolment") {
      val response: Future[Result] = controller
        .getMetadataForConversationsFiltered(
          None,
          Some(List(CustomerEnrolment("HMRC-CUS-ORG", "EORINumber", "GB123456789"))),
          None)
        .apply(FakeRequest("GET", "/"))
      status(response) mustBe UNAUTHORIZED
      contentAsString(response) mustBe "\"No enrolment found\""
    }
  }

  "getConversation" must {
    "return an OK (200) with a JSON body of a ApiConversations" in new GetConversationTestCase(
      storedConversation = Some(Resources.readJson("model/api/api-conversation.json"))) {
      val response: Future[Result] = controller
        .getConversationContent("cdcm", "D-80542-20201120", "HMRC-CUS-ORG", "EORINumber")
        .apply(FakeRequest("GET", "/"))
      status(response) mustBe OK
      contentAsJson(response).as[ApiConversation] must be(conversation.value)
    }

    "return an BadRequest (400) with a JSON body of No conversation found" in new GetConversationTestCase(
      storedConversation = None) {
      val response: Future[Result] = controller
        .getConversationContent("cdcm", "D-80542-20201120", "HMRC-CUS-ORG", "EORINumber")
        .apply(FakeRequest("GET", "/"))
      status(response) mustBe BAD_REQUEST
      contentAsString(response) mustBe
        "\"No conversation found\""
    }

    "return a 401 (UNAUTHORISED) error when no EORI enrolment found" in new TestCase(
      enrolmentKey = "some other key",
      enrolmentIdentifierKey = "another enrolment") {
      private val response = controller
        .getConversationContent("cdcm", "D-80542-20201120", "HMRC-CUS-ORG", "EORINumber")
        .apply(FakeRequest("GET", "/"))
      status(response) mustBe UNAUTHORIZED
      contentAsString(response) mustBe "\"No EORI enrolment found\""
    }
  }

  "createCaseworkerMessage" must {
    "return CREATED (201) when with valid payload" in new CreateCaseWorkerMessageTestCase(
      requestBody = Resources.readJson("model/api/caseworker-message.json"),
      givenResult = Future(())) {
      private val response = controller.createCaseworkerMessage("cdcm", "123")(fakeRequest)
      status(response) mustBe CREATED
    }
    "return UNAUTHORIZED (401) when the caseworker is not a conversation participant" in new CreateCaseWorkerMessageTestCase(
      requestBody = Resources.readJson("model/api/caseworker-message.json"),
      givenResult = Future.failed(AuthorisationException.fromString("Caseworker ID not found"))
    ) {
      private val response = controller.createCaseworkerMessage("cdcm", "123")(fakeRequest)
      status(response) mustBe UNAUTHORIZED
    }
    "return BAD_REQUEST (400) when the message content is not base64 encoded" in new CreateCaseWorkerMessageTestCase(
      requestBody = Resources.readJson("model/api/caseworker-message.json"),
      givenResult = Future.failed(new ParseException("Not valid base64 content", 0))) {
      private val response = controller.createCaseworkerMessage("cdcm", "123")(fakeRequest)
      status(response) mustBe BAD_REQUEST
      contentAsString(response) mustBe "Not valid base64 content"
    }
    "return BAD_REQUEST (400) when the message content is not valid HTML" in new CreateCaseWorkerMessageTestCase(
      requestBody = Resources.readJson("model/api/caseworker-message.json"),
      givenResult = Future.failed(new ParseException("Not valid HTML content", 0))) {
      private val response = controller.createCaseworkerMessage("cdcm", "123")(fakeRequest)
      status(response) mustBe BAD_REQUEST
      contentAsString(response) mustBe "Not valid HTML content"
    }
  }

  "createCustomerMessage" must {
    "return CREATED (201) when a message is successfully added to the conversation" in new CreateCustomerMessageTestCase(
      addMessageResult = Future(())) {
      private val response = controller.createCustomerMessage("cdcm", "D-80542-20201120")(fakeRequest)
      status(response) mustBe CREATED
    }
    "return UNAUTHORIZED (401) when the customer is not a conversation participant" in new CreateCustomerMessageTestCase(
      addMessageResult = Future.failed(AuthorisationException.fromString("InsufficientEnrolments"))) {
      private val response = controller.createCustomerMessage("cdcm", "D-80542-20201120")(fakeRequest)
      status(response) mustBe UNAUTHORIZED
    }
    "return NOT_FOUND (404) when the conversation ID is not recognised" in new CreateCustomerMessageTestCase(
      addMessageResult = Future.failed(new IllegalArgumentException("Conversation ID not known"))) {
      private val response = controller.createCustomerMessage("cdcm", "D-80542-20201120")(fakeRequest)
      status(response) mustBe NOT_FOUND
    }
    "return BAD_REQUEST (400) when the message content is not base64 encoded" in new CreateCustomerMessageTestCase(
      addMessageResult = Future.failed(new ParseException("Not valid base64 content", 0))) {
      private val response = controller.createCustomerMessage("cdcm", "D-80542-20201120")(fakeRequest)
      status(response) mustBe BAD_REQUEST
      contentAsString(response) mustBe "Not valid base64 content"
    }
    "return BAD_REQUEST (400) when the message content is not valid HTML" in new CreateCustomerMessageTestCase(
      Future.failed(new ParseException("Not valid HTML content", 0))) {
      private val response = controller.createCustomerMessage("cdcm", "D-80542-20201120")(fakeRequest)
      status(response) mustBe BAD_REQUEST
      contentAsString(response) mustBe "Not valid HTML content"
    }
  }

  "updateReadTime" must {
    "return CREATED (201) with a JSON body of read time successfully added when a readTime has successfully been created " in new UpdateReadTimeTestCase(
      updateSuccessful = true) {
      val response: Future[Result] = controller.addCustomerReadTime("cdcm", "D-80542-20201120")(fakeRequest)
      status(response) mustBe CREATED
      contentAsString(response) mustBe "\"read time successfully added\""
    }

    "return BADREQUEST (400) with a JSON body of issue with updating read time" in new UpdateReadTimeTestCase(
      updateSuccessful = false) {
      val response: Future[Result] = controller.addCustomerReadTime("cdcm", "D-80542-20201120")(fakeRequest)
      status(response) mustBe BAD_REQUEST
      contentAsString(response) mustBe "\"issue with updating read time\""
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  class TestCase(enrolmentKey: String = "HMRC-CUS-ORG", enrolmentIdentifierKey: String = "EORINumber") {
    val mockRepository: ConversationRepository = mock[ConversationRepository]
    val mockAuthConnector: AuthConnector = mock[AuthConnector]
    val mockSecureMessageService: SecureMessageService = mock[SecureMessageService]
    when(mockRepository.insertIfUnique(any[Conversation])(any[ExecutionContext])).thenReturn(Future.successful(true))
    val controller =
      new SecureMessageController(Helpers.stubControllerComponents(), mockAuthConnector, mockSecureMessageService)

    private val enrolment: Enrolment = uk.gov.hmrc.auth.core.Enrolment(
      key = enrolmentKey,
      identifiers = Seq(EnrolmentIdentifier(enrolmentIdentifierKey, "GB123456789")),
      state = "",
      None)
    when(
      mockAuthConnector
        .authorise(any[Predicate], any[Retrieval[Enrolments]])(any[HeaderCarrier], any[ExecutionContext]))
      .thenReturn(Future.successful(Enrolments(Set(enrolment))))

    private val fullConversationJson = Resources.readJson("model/api/create-conversation-full.json")
    val fullConversationfakeRequest: FakeRequest[JsValue] = FakeRequest(
      method = PUT,
      uri = routes.SecureMessageController.createConversation("cdcm", "123").url,
      headers = FakeHeaders(Seq(CONTENT_TYPE -> JSON)),
      body = fullConversationJson
    )
  }

  class CreateConversationTestCase(requestBody: JsValue) extends TestCase {
    val fakeRequest: FakeRequest[JsValue] = FakeRequest(
      method = PUT,
      uri = routes.SecureMessageController.createConversation("cdcm", "123").url,
      headers = FakeHeaders(Seq(CONTENT_TYPE -> JSON)),
      body = requestBody
    )

    when(
      mockSecureMessageService.createConversation(any[ConversationRequest], any[String], any[String])(
        any[HeaderCarrier],
        any[ExecutionContext]))
      .thenReturn(Future(Result(new ResponseHeader(CREATED), HttpEntity.NoEntity)))
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
        .addCustomerMessageToConversation(any[String], any[String], any[CustomerMessageRequest], any[Enrolments])(
          any[ExecutionContext])).thenReturn(addMessageResult)
  }

  class GetConversationsTestCase(storedConversationsMetadata: JsValue) extends TestCase {
    val conversationsMetadata: List[ConversationMetadata] = storedConversationsMetadata.as[List[ConversationMetadata]]

    private val eORINumber: CustomerEnrolment = generic.CustomerEnrolment("HMRC-CUS-ORG", "EORINumber", "GB123456789")
    when(mockSecureMessageService.getConversations(eORINumber))
      .thenReturn(Future(conversationsMetadata))

    when(mockSecureMessageService.getConversationsFiltered(Set(eORINumber), None))
      .thenReturn(Future(conversationsMetadata))
  }

  class GetConversationTestCase(storedConversation: Option[JsValue]) extends TestCase {
    val conversation: Option[ApiConversation] = storedConversation.map(_.as[ApiConversation])
    when(
      mockSecureMessageService.getConversation(any[String], any[String], any[generic.CustomerEnrolment])(
        any[ExecutionContext]))
      .thenReturn(Future(conversation))
  }

  class CreateCaseWorkerMessageTestCase(requestBody: JsValue, givenResult: Future[Unit]) extends TestCase {
    val fakeRequest: FakeRequest[JsValue] = FakeRequest(
      method = POST,
      uri = routes.SecureMessageController.createCaseworkerMessage("cdcm", "123").url,
      headers = FakeHeaders(Seq(CONTENT_TYPE -> JSON)),
      body = requestBody
    )
    when(
      mockSecureMessageService.addCaseWorkerMessageToConversation(
        any[String],
        any[String],
        any[CaseworkerMessageRequest])(any[ExecutionContext], any[HeaderCarrier])).thenReturn(givenResult)
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
