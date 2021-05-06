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
import org.apache.commons.codec.binary.Base64
import org.joda.time.{ LocalDate }
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.{ any, eq => eqTo }
import org.mockito.Mockito.{ times, verify, when }
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
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.Retrieval
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.securemessage._
import uk.gov.hmrc.securemessage.controllers.model.cdcm.read.{ ApiConversation, ConversationMetadata }
import uk.gov.hmrc.securemessage.controllers.model.cdcm.write.{ CaseworkerMessage, CdcmConversation }
import uk.gov.hmrc.securemessage.controllers.model.cdsf.read.{ ApiLetter, SenderInformation }
import uk.gov.hmrc.securemessage.controllers.model.common.write.CustomerMessage
import uk.gov.hmrc.securemessage.controllers.model.{ ClientName, MessageType }
import uk.gov.hmrc.securemessage.helpers.Resources
import uk.gov.hmrc.securemessage.models.core.Letter._
import uk.gov.hmrc.securemessage.models.core._
import uk.gov.hmrc.securemessage.repository.ConversationRepository
import uk.gov.hmrc.securemessage.services.SecureMessageService
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ ExecutionContext, ExecutionException, Future }

@SuppressWarnings(
  Array(
    "org.wartremover.warts.NonUnitStatements",
    "org.wartremover.warts.OptionPartial",
    "org.wartremover.warts.EitherProjectionPartial"))
class SecureMessageControllerSpec extends PlaySpec with ScalaFutures with MockitoSugar with OptionValues with UnitTest {

  implicit val mat: Materializer = NoMaterializer
  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit val messages: Messages = stubMessages()
  private val testEnrolment = CustomerEnrolment("HMRC-CUS-ORG", "EORINumber", "GB123456789")
  private val cdcm = ClientName.CDCM
  private val objectID = BSONObjectID.generate()

  "createConversation" must {

    "return CREATED (201) when sent a request with all optional fields populated" in new CreateConversationTestCase(
      requestBody = Resources.readJson("model/api/cdcm/write/create-conversation.json"),
      serviceResponse = Future.successful(Right(()))) {
      private val response = controller.createConversation(cdcm, "123")(fakeRequest)
      status(response) mustBe CREATED
    }

    "return CREATED (201) when sent a request with no optional fields populated" in new CreateConversationTestCase(
      requestBody = Resources.readJson("model/api/cdcm/write/create-conversation.json"),
      serviceResponse = Future.successful(Right(()))) {
      private val response = controller.createConversation(cdcm, "123")(fakeRequest)
      status(response) mustBe CREATED
    }

    "return CREATED (201) when sent a request with no alert parameters are passed" in new CreateConversationTestCase(
      requestBody = Resources.readJson("model/api/cdcm/write/create-conversation-without-alert-parameters.json"),
      serviceResponse = Future.successful(Right(()))) {
      private val response = controller.createConversation(cdcm, "123")(fakeRequest)
      status(response) mustBe CREATED
    }

    "return CREATED (201) when sending email in but ignore it" in new CreateConversationTestCase(
      requestBody = Resources.readJson("model/api/cdcm/write/create-conversation-with-email.json"),
      serviceResponse = Future.successful(Right(())),
      objectID) {
      private val response = controller.createConversation(client, conversationId)(fakeRequest)
      verify(mockSecureMessageService, times(1))
        .createConversation(any[Conversation])(any[HeaderCarrier], any[ExecutionContext])
      status(response) mustBe CREATED
    }

    "return BAD REQUEST (400) when sent a request with required fields missing" in new CreateConversationTestCase(
      requestBody = Json.parse("""{"missing":"data"}""".stripMargin),
      serviceResponse = Future.successful(Right(()))) {
      private val response = controller.createConversation(cdcm, "123")(fakeRequest)
      status(response) mustBe BAD_REQUEST
    }

    "return CONFLICT (409) when the conversation already exists" in new CreateConversationTestCase(
      requestBody = Resources.readJson("model/api/cdcm/write/create-conversation.json"),
      serviceResponse = Future.successful(Left(DuplicateConversationError("conflict error", None)))
    ) {
      private val response = controller.createConversation(cdcm, "123")(fakeRequest)
      status(response) mustBe CONFLICT
      contentAsJson(response) mustBe Json.toJson(
        "Error on conversation with client: Some(CDCM), conversationId: 123, error message: conflict error")
    }

    "return InternalServerError (500) when there is an error storing the conversation" in new CreateConversationTestCase(
      requestBody = Resources.readJson("model/api/cdcm/write/create-conversation.json"),
      serviceResponse = Future.successful(Left(StoreError("mongo error", None)))
    ) {
      private val response = controller.createConversation(cdcm, "123")(fakeRequest)
      status(response) mustBe INTERNAL_SERVER_ERROR
      contentAsJson(response) mustBe Json.toJson(
        """Error on conversation with client: Some(CDCM), conversationId: 123, error message: mongo error""")
    }

    "return CREATED (201) when there is an error sending the email" in new CreateConversationTestCase(
      requestBody = Resources.readJson("model/api/cdcm/write/create-conversation.json"),
      serviceResponse = Future.successful(Left(EmailSendingError("email error")))
    ) {
      private val response = controller.createConversation(cdcm, "123")(fakeRequest)
      status(response) mustBe CREATED
      contentAsJson(response) mustBe Json.toJson(
        "Error on conversation with client: Some(CDCM), conversationId: 123, error message: email error")
    }

    "return CREATED (201) when no email can be found" in new CreateConversationTestCase(
      requestBody = Resources.readJson("model/api/cdcm/write/create-conversation.json"),
      serviceResponse = Future.successful(Left(NoReceiverEmailError("Verified email address could not be found")))
    ) {
      private val response = controller.createConversation(cdcm, "123")(fakeRequest)
      status(response) mustBe CREATED
      contentAsJson(response) mustBe Json.toJson(
        "Error on conversation with client: Some(CDCM), conversationId: 123, error message: Verified email address could not be found")
    }

    "return InternalServerError (500) if unexpected SecureMessageError returned" in new CreateConversationTestCase(
      requestBody = Resources.readJson("model/api/cdcm/write/create-conversation.json"),
      serviceResponse = Future.successful(Left(new SecureMessageError("some unknown err")))
    ) {
      private val response = controller.createConversation(cdcm, "123")(fakeRequest)
      status(response) mustBe INTERNAL_SERVER_ERROR
      contentAsJson(response) mustBe Json.toJson(
        "Error on conversation with client: Some(CDCM), conversationId: 123, error message: some unknown err")
    }

    "return BAD_REQUEST (400) when the message content is not base64 encoded" in new CreateConversationTestCase(
      requestBody = Resources.readJson("model/api/cdcm/write/create-conversation.json"),
      serviceResponse = Future.successful(Left(InvalidContent("Not valid base64 content")))
    ) {
      private val response = controller.createConversation(cdcm, "123")(fakeRequest)
      status(response) mustBe BAD_REQUEST
      contentAsJson(response) mustBe Json.toJson(
        "Error on conversation with client: Some(CDCM), conversationId: 123, error message: Not valid base64 content")
    }

    "return BAD_REQUEST (400) when the message content is not valid HTML" in new CreateConversationTestCase(
      requestBody = Resources.readJson("model/api/cdcm/write/create-conversation.json"),
      serviceResponse = Future.successful(Left(InvalidContent("Not valid html content")))
    ) {
      private val response = controller.createConversation(cdcm, "123")(fakeRequest)
      status(response) mustBe BAD_REQUEST
      contentAsJson(response) mustBe Json.toJson(
        "Error on conversation with client: Some(CDCM), conversationId: 123, error message: Not valid html content")
    }

    "do not handle non SecureMessageError exceptions" in new CreateConversationTestCase(
      requestBody = Resources.readJson("model/api/cdcm/write/create-conversation.json"),
      serviceResponse = Future.failed(new Exception("some error"))) {
      private val response = controller.createConversation(cdcm, "123")(fakeRequest)
      assertThrows[Exception] {
        status(response)
      }
    }

    "do not handle OutOfMemoryError" in new CreateConversationTestCase(
      requestBody = Resources.readJson("model/api/cdcm/write/create-conversation.json"),
      serviceResponse = Future.failed(new OutOfMemoryError("no memory for jvm"))
    ) {
      assertThrows[ExecutionException] {
        val response: Future[Result] = controller.createConversation(cdcm, "123")(fakeRequest)
        status(response)
      }
    }

    "return BAD_REQUEST (400) when mrn is empty" in new CreateConversationTestCase(
      requestBody = Resources.readJson("model/api/cdcm/write/create-conversation-empty-mrn.json"),
      serviceResponse = Future.successful(Right(()))
    ) {
      private val response = controller.createConversation(cdcm, "123")(fakeRequest)
      status(response) mustBe BAD_REQUEST
      contentAsString(response) mustBe "Could not parse body due to requirement failed: empty mrn not allowed"
    }
  }

  "getConversationsFiltered" must {
    "return an OK (200) with a JSON body of a list of conversations when provided with a list of valid query parameters" in new GetConversationsTestCase(
      storedConversationsMetadata = Resources.readJson("model/api/cdcm/read/conversations-metadata.json")) {
      val response: Future[Result] = controller
        .getMetadataForConversationsFiltered(
          None,
          Some(List(CustomerEnrolment("HMRC-CUS-ORG", "EORINumber", "GB123456789"))),
          None)
        .apply(FakeRequest("GET", "/"))
      status(response) mustBe OK
      contentAsJson(response).as[List[ConversationMetadata]] must be(conversationsMetadata)
    }

    //TODO: move mock to GetConversationsTestCase, this functionality needs to be reviewed.
    "return Ok (200) with empty list for query parameters/auth record mismatch" in new TestCase(
      authEnrolments = Set(CustomerEnrolment("SOME_ENROLMENT_KEY", "SOME_IDENTIFIER_KEY", "SOME_IDENTIFIER_VALUE"))) {
      private val someEnrolments: Some[List[CustomerEnrolment]] = Some(List(testEnrolment))
      private val filters: ConversationFilters = ConversationFilters(None, someEnrolments, None)
      when(
        mockSecureMessageService
          .getConversationsFiltered(eqTo(enrolments), eqTo(filters))(any[ExecutionContext], any[Messages]))
        .thenReturn(Future.successful(List()))
      val response: Future[Result] = controller
        .getMetadataForConversationsFiltered(None, someEnrolments, None)
        .apply(FakeRequest("GET", "/"))
      status(response) mustBe OK
      contentAsString(response) mustBe "[]"
    }

    "return Bad Request (400) error when invalid query parameters are provided" in new TestCase(
      authEnrolments = Set(CustomerEnrolment("SOME_ENROLMENT_KEY", "SOME_IDENTIFIER_KEY", "SOME_IDENTIFIER_VALUE"))) {
      val response: Future[Result] = controller
        .getMetadataForConversationsFiltered(None, Some(List(testEnrolment)), None)
        .apply(FakeRequest("GET", "/some?x=123&Z=12&a=abc&test=ABCDEF"))
      status(response) mustBe BAD_REQUEST
      contentAsString(response) mustBe "\"Invalid query parameter(s) found: [Z, a, test, x]\""
    }
  }

  "getConversation" must {
    "return Ok (200) with a JSON body of a ApiConversations" in new GetConversationTestCase(
      storedConversation = Some(Resources.readJson("model/api/cdcm/read/api-conversation.json"))) {
      val response: Future[Result] = controller
        .getConversationContent(cdcm, "D-80542-20201120")
        .apply(FakeRequest("GET", "/"))
      status(response) mustBe OK
      contentAsJson(response).as[ApiConversation] mustBe apiConversation.right.get
    }

    "return Ok (200) with a JSON body of a ApiConversations when auth enrolments hold multiple identifiers and enrolments" in new GetConversationTestCase(
      storedConversation = Some(Resources.readJson("model/api/cdcm/read/api-conversation.json")),
      Set(
        testEnrolment,
        CustomerEnrolment("HMRC-CUS-ORG", "EORINumber", "GB023456800"),
        CustomerEnrolment("IR-SA", "NINO", "0123456789")
      )
    ) {
      val response: Future[Result] = controller
        .getConversationContent(cdcm, "D-80542-20201120")
        .apply(FakeRequest("GET", "/"))
      status(response) mustBe OK
      contentAsJson(response).as[ApiConversation] mustBe apiConversation.right.get
    }

    "return Not Found (404) with a JSON body of No conversation found" in new GetConversationTestCase(
      storedConversation = None) {
      val response: Future[Result] = controller
        .getConversationContent(cdcm, "D-80542-20201120")
        .apply(FakeRequest("GET", "/"))
      status(response) mustBe NOT_FOUND
      contentAsString(response) mustBe "\"No conversation found\""
    }

    "return Unauthorized (401) when no enrolment found" in new TestCase(Set.empty[CustomerEnrolment]) {
      private val response = controller
        .getConversationContent(cdcm, "D-80542-20201120")
        .apply(FakeRequest("GET", "/"))
      status(response) mustBe UNAUTHORIZED
      contentAsString(response) mustBe "\"No enrolment found\""
    }
  }

  "getContentDetail" must {
    val objectID = BSONObjectID.generate()
    "return a conversation" in new GetConversationByIdTestCase(
      storedConversation = Some(
        Resources.readJson("model/api/cdcm/read/api-conversation.json").as[JsObject] + ("_id" -> Json.toJson(
          objectID)))) {
      val response: Future[Result] = controller
        .getContentDetail(encodedPath(s"${MessageType.Conversation.entryName}/${objectID.stringify}"))
        .apply(FakeRequest("GET", "/"))
      status(response) mustBe OK
      contentAsJson(response).as[ApiConversation] mustBe apiConversation.right.get

    }

    "return a conversation when auth enrolments hold multiple identifiers and enrolments" in new GetConversationByIdTestCase(
      storedConversation = Some(
        Resources.readJson("model/api/cdcm/read/api-conversation.json").as[JsObject] + ("_id" -> Json.toJson(
          objectID))),
      Set(
        testEnrolment,
        CustomerEnrolment("HMRC-CUS-ORG", "EORINumber", "GB023456800"),
        CustomerEnrolment("IR-SA", "NINO", "0123456789")
      )
    ) {
      val response: Future[Result] = controller
        .getContentDetail(encodedPath(s"${MessageType.Conversation.entryName}/${objectID.stringify}"))
        .apply(FakeRequest("GET", "/"))
      status(response) mustBe OK
      contentAsJson(response).as[ApiConversation] mustBe apiConversation.right.get

    }

    "return Not Found (404) with a JSON body of No conversation found" in new GetConversationByIdTestCase(
      storedConversation = None) {
      val response: Future[Result] = controller
        .getContentDetail(encodedPath(s"${MessageType.Conversation.entryName}/${objectID.stringify}"))
        .apply(FakeRequest("GET", "/"))
      status(response) mustBe NOT_FOUND
      contentAsString(response) must include("conversations not found")
    }

    "return Unauthorized (401) when no enrolment found" in new TestCase(Set.empty[CustomerEnrolment]) {
      private val response = controller
        .getContentDetail(encodedPath(s"${MessageType.Conversation.entryName}/${objectID.stringify}"))
        .apply(FakeRequest("GET", "/"))
      status(response) mustBe UNAUTHORIZED
      contentAsString(response) mustBe "\"No enrolment found\""
    }

    "return a letter" in new GetMessageByIdTestCase(
      storedLetter = Some(Resources.readJson("model/core/letter.json").as[JsObject] + ("_id" -> Json.toJson(objectID))
        + ("lastUpdated"                                                                     -> Json.toJson(DateTime.now())))) {
      val response: Future[Result] = controller
        .getContentDetail(encodedPath(s"${MessageType.Letter.entryName}/${objectID.stringify}"))
        .apply(FakeRequest("GET", "/"))
      status(response) mustBe OK
      contentAsJson(response).as[ApiLetter] mustBe apiLetter.get

    }

    "return a letter when auth enrolments hold multiple identifiers and enrolments " in new GetMessageByIdTestCase(
      storedLetter = Some(
        Resources.readJson("model/core/letter.json").as[JsObject] + ("_id" -> Json.toJson(objectID))
          + ("lastUpdated"                                                 -> Json.toJson(DateTime.now()))),
      Set(
        testEnrolment,
        CustomerEnrolment("HMRC-CUS-ORG", "EORINumber", "GB023456800"),
        CustomerEnrolment("IR-SA", "NINO", "0123456789")
      )
    ) {
      val response: Future[Result] = controller
        .getContentDetail(encodedPath(s"${MessageType.Letter.entryName}/${objectID.stringify}"))
        .apply(FakeRequest("GET", "/"))
      status(response) mustBe OK
      contentAsJson(response).as[ApiLetter] mustBe apiLetter.get

    }

    "return Not Found (404) with a JSON body of No letter found" in new TestCase {
      when(mockSecureMessageService.getLetter(any[String], any[Set[CustomerEnrolment]])(any[ExecutionContext]))
        .thenReturn(Future.successful(Left(LetterNotFound("letter not found"))))

      val response: Future[Result] = controller
        .getContentDetail(encodedPath(s"${MessageType.Letter.entryName}/${objectID.stringify}"))
        .apply(FakeRequest("GET", "/"))
      status(response) mustBe NOT_FOUND
      // contentAsString(response) mustBe "\"No Letter found\""
    }

    "return Unauthorized (401) when no enrolment found for a letter" in new TestCase(Set.empty[CustomerEnrolment]) {
      private val response = controller
        .getContentDetail(encodedPath(s"${MessageType.Letter.entryName}/${objectID.stringify}"))
        .apply(FakeRequest("GET", "/"))
      status(response) mustBe UNAUTHORIZED
      contentAsString(response) mustBe "\"No enrolment found\""
    }

    "return BadRequest(Invalid message type) if messageType is invalid" in new GetMessageByIdTestCase(
      storedLetter = Some(Resources.readJson("model/core/letter.json").as[JsObject] + ("_id" -> Json.toJson(objectID))
        + ("lastUpdated"                                                                     -> Json.toJson(DateTime.now())))) {
      val response: Future[Result] = controller
        .getContentDetail(encodedPath(s"invalid/${objectID.stringify}"))
        .apply(FakeRequest("GET", "/"))
      status(response) mustBe BAD_REQUEST
      contentAsString(response) mustBe "\"Invalid message type\""
    }

    "return BadRequest if decoding cant find id" in new GetMessageByIdTestCase(
      storedLetter = Some(Resources.readJson("model/core/letter.json").as[JsObject] + ("_id" -> Json.toJson(objectID))
        + ("lastUpdated"                                                                     -> Json.toJson(DateTime.now())))) {
      val response: Future[Result] = controller
        .getContentDetail(encodedPath(s"${MessageType.Letter.entryName}"))
        .apply(FakeRequest("GET", "/"))
      status(response) mustBe BAD_REQUEST
      contentAsString(response) mustBe "\"Invalid URL path\""
    }
  }

  "createCaseworkerMessage" must {
    "return CREATED (201) when with valid payload" in new CreateCaseWorkerMessageTestCase(
      requestBody = Resources.readJson("model/api/cdcm/write/caseworker-message.json"),
      serviceResponse = Future.successful(Right(()))) {
      private val response = controller.addCaseworkerMessage(cdcm, "123")(fakeRequest)
      status(response) mustBe CREATED
    }
    "return UNAUTHORIZED (401) when the caseworker is not a conversation participant" in new CreateCaseWorkerMessageTestCase(
      requestBody = Resources.readJson("model/api/cdcm/write/caseworker-message.json"),
      serviceResponse = Future.successful(Left(ParticipantNotFound("Caseworker ID not found")))
    ) {
      private val response = controller.addCaseworkerMessage(cdcm, "123")(fakeRequest)
      status(response) mustBe UNAUTHORIZED
    }
    "return BAD_REQUEST (400) when the message content is not base64 encoded" in new CreateCaseWorkerMessageTestCase(
      requestBody = Resources.readJson("model/api/cdcm/write/caseworker-message.json"),
      serviceResponse = Future.successful(Left(InvalidContent("Not valid base64 content")))
    ) {
      private val response = controller.addCaseworkerMessage(cdcm, "123")(fakeRequest)
      status(response) mustBe BAD_REQUEST
      contentAsString(response) mustBe "\"Error on conversation with client: Some(CDCM), conversationId: 123, error message: Not valid base64 content\""
    }
    "return BAD_REQUEST (400) when the message content is not valid HTML" in new CreateCaseWorkerMessageTestCase(
      requestBody = Resources.readJson("model/api/cdcm/write/caseworker-message.json"),
      serviceResponse = Future.successful(Left(InvalidContent("Not valid HTML content")))
    ) {
      private val response = controller.addCaseworkerMessage(cdcm, "123")(fakeRequest)
      status(response) mustBe BAD_REQUEST
      contentAsString(response) mustBe "\"Error on conversation with client: Some(CDCM), conversationId: 123, error message: Not valid HTML content\""
    }
  }

  "createCustomerMessage" must {
    "return CREATED (201) when a message is successfully added to the conversation" in new CreateCustomerMessageTestCase(
      serviceResponse = Future.successful(Right(()))) {
      private val response = controller.addCustomerMessage(cdcm, "D-80542-20201120")(fakeRequest)
      status(response) mustBe CREATED
    }
    "return UNAUTHORIZED (401) when the customer is not a conversation participant" in new CreateCustomerMessageTestCase(
      serviceResponse = Future.successful(Left(ParticipantNotFound("InsufficientEnrolments")))) {
      private val response = controller.addCustomerMessage(cdcm, "D-80542-20201120")(fakeRequest)
      status(response) mustBe UNAUTHORIZED
    }
    "return NOT_FOUND (404) when the conversation ID is not recognised" in new CreateCustomerMessageTestCase(
      serviceResponse = Future.successful(Left(ConversationNotFound("Conversation ID not known")))) {
      private val response = controller.addCustomerMessage(cdcm, "D-80542-20201120")(fakeRequest)
      status(response) mustBe NOT_FOUND
    }
    "return Bad Gateway (502) when the message cannot be forwarded to EIS" in new CreateCustomerMessageTestCase(
      serviceResponse = Future.successful(Left(EisForwardingError("Failed to forward message to EIS")))) {
      private val response = controller.addCustomerMessage(cdcm, "D-80542-20201120")(fakeRequest)
      status(response) mustBe BAD_GATEWAY
    }
  }

  "Base64 decoding" must {
    "return messageType letter and id" in new TestCase {
      val nakedPath = "letter/6086dc1f4700009fed2f5745"
      val path = encodedPath(nakedPath)

      controller.decodePath(path).right.get mustBe (("letter", "6086dc1f4700009fed2f5745"))
    }
    "return messageType conversation and id" in new TestCase {
      val nakedPath = "conversation/6086dc1f4700009fed2f5745"
      val path = encodedPath(nakedPath)
      controller.decodePath(path).right.get mustBe (("conversation", "6086dc1f4700009fed2f5745"))
    }
    "return only messageType and Id" in new TestCase {
      val nakedPath = "conversation/6086dc1f4700009fed2f5745/test"
      val path = encodedPath(nakedPath)
      controller.decodePath(path).right.get mustBe (("conversation", "6086dc1f4700009fed2f5745"))
    }
    "return InvalidPath if path is not valid" in new TestCase {
      val nakedPath = "123456"
      val path = encodedPath(nakedPath)
      controller.decodePath(path).left.get.message mustBe ("Invalid URL path")
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  class TestCase(authEnrolments: Set[CustomerEnrolment] = Set(testEnrolment)) {
    val mockRepository: ConversationRepository = mock[ConversationRepository]
    val mockAuthConnector: AuthConnector = mock[AuthConnector]
    val mockAuditConnector: AuditConnector = mock[AuditConnector]
    val mockSecureMessageService: SecureMessageService = mock[SecureMessageService]
    when(mockRepository.insertIfUnique(any[Conversation])(any[ExecutionContext]))
      .thenReturn(Future.successful(Right(())))

    protected def encodedPath(path: String) = Base64.encodeBase64String(path.getBytes("UTF-8"))

    val controller =
      new SecureMessageController(
        Helpers.stubControllerComponents(),
        mockAuthConnector,
        mockAuditConnector,
        mockSecureMessageService,
        zeroTimeProvider)

    val enrolments: Enrolments = authEnrolmentsFrom(authEnrolments)

    when(
      mockAuthConnector
        .authorise(any[Predicate], any[Retrieval[Enrolments]])(any[HeaderCarrier], any[ExecutionContext]))
      .thenReturn(Future.successful(enrolments))
  }

  private val fullConversationJson = Resources.readJson("model/api/cdcm/write/create-conversation.json")
  val fullConversationfakeRequest: FakeRequest[JsValue] = FakeRequest(
    method = PUT,
    uri = routes.SecureMessageController.createConversation(cdcm, "123").url,
    headers = FakeHeaders(Seq(CONTENT_TYPE -> JSON)),
    body = fullConversationJson
  )

  @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
  class CreateConversationTestCase(
    requestBody: JsValue,
    serviceResponse: Future[Either[SecureMessageError, Unit]],
    objectID: BSONObjectID = BSONObjectID.generate())
      extends TestCase {
    val fakeRequest: FakeRequest[JsValue] = FakeRequest(
      method = PUT,
      uri = routes.SecureMessageController.createConversation(cdcm, "123").url,
      headers = FakeHeaders(Seq(CONTENT_TYPE -> JSON)),
      body = requestBody
    )
    val client: ClientName = cdcm
    val conversationId = "123"
    private lazy val conversation: Conversation =
      requestBody
        .as[CdcmConversation]
        .asConversationWithCreatedDate(client.entryName, conversationId, now)
        .copy(_id = objectID)
    private lazy val expectedParticipants = conversation.participants.map(p => p.copy(email = None))
    lazy val expectedConversation: Conversation = conversation.copy(participants = expectedParticipants, _id = objectID)
    when(mockSecureMessageService.createConversation(any[Conversation])(any[HeaderCarrier], any[ExecutionContext]))
      .thenReturn(serviceResponse)
  }

  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  class CreateCustomerMessageTestCase(serviceResponse: Future[Either[SecureMessageError, Unit]]) extends TestCase {
    val fakeRequest: FakeRequest[JsObject] = FakeRequest(
      method = POST,
      uri = routes.SecureMessageController.addCustomerMessage(cdcm, "D-80542-20201120").url,
      headers = FakeHeaders(Seq(CONTENT_TYPE -> JSON)),
      body = Json.obj("content" -> "PGRpdj5IZWxsbzwvZGl2Pg==")
    )
    when(
      mockSecureMessageService
        .addCustomerMessageToConversation(any[String], any[String], any[CustomerMessage], any[Enrolments])(
          any[ExecutionContext],
          any[Request[_]])).thenReturn(serviceResponse)
  }

  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments", "org.wartremover.warts.EitherProjectionPartial"))
  class GetConversationsTestCase(
    storedConversationsMetadata: JsValue,
    authEnrolments: Set[CustomerEnrolment] = Set(testEnrolment),
    customerEnrolments: Set[CustomerEnrolment] = Set(testEnrolment),
    filterTags: Option[List[FilterTag]] = None,
    filterEnrolmentKeys: Option[List[String]] = None
  ) extends TestCase(authEnrolments) {
    val conversationsMetadata: List[ConversationMetadata] = storedConversationsMetadata.as[List[ConversationMetadata]]
    when(
      mockSecureMessageService
        .getConversationsFiltered(
          eqTo(authEnrolmentsFrom(authEnrolments)),
          eqTo(ConversationFilters(filterEnrolmentKeys, Some(customerEnrolments.toList), filterTags)))(
          any[ExecutionContext],
          any[Messages]))
      .thenReturn(Future.successful(conversationsMetadata))
  }

  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  class GetConversationTestCase(
    storedConversation: Option[JsValue],
    authEnrolments: Set[CustomerEnrolment] = Set(testEnrolment))
      extends TestCase(authEnrolments) {
    val apiConversation: Either[ConversationNotFound, ApiConversation] = storedConversation match {
      case Some(conversation) => Right(conversation.as[ApiConversation])
      case _                  => Left(ConversationNotFound("conversations not found"))
    }
    when(
      mockSecureMessageService.getConversation(any[String], any[String], any[Set[CustomerEnrolment]])(
        any[ExecutionContext]))
      .thenReturn(Future.successful(apiConversation))
  }

  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  class GetConversationByIdTestCase(
    storedConversation: Option[JsValue],
    authEnrolments: Set[CustomerEnrolment] = Set(testEnrolment))
      extends TestCase(authEnrolments) {
    val apiConversation: Either[ConversationNotFound, ApiConversation] = storedConversation match {
      case Some(conversation) => Right(conversation.as[ApiConversation])
      case _                  => Left(ConversationNotFound("conversations not found"))
    }
    when(mockSecureMessageService.getConversation(any[String], any[Set[CustomerEnrolment]])(any[ExecutionContext]))
      .thenReturn(Future.successful(apiConversation))
  }

  class GetMessageByIdTestCase(
    storedLetter: Option[JsValue],
    authEnrolments: Set[CustomerEnrolment] = Set(testEnrolment))
      extends TestCase(authEnrolments) {
    val letter = storedLetter.map(l => l.validate[Letter]).map(_.get)
    val apiLetter = letter.map(l => ApiLetter(l.subject, l.content, None, SenderInformation("HMRC", LocalDate.now)))
    val successLetter: Either[Nothing, ApiLetter] = Right(apiLetter.get)
    when(mockSecureMessageService.getLetter(any[String], any[Set[CustomerEnrolment]])(any[ExecutionContext]))
      .thenReturn(Future.successful(successLetter))
  }

  class CreateCaseWorkerMessageTestCase(requestBody: JsValue, serviceResponse: Future[Either[SecureMessageError, Unit]])
      extends TestCase {
    val fakeRequest: FakeRequest[JsValue] = FakeRequest(
      method = POST,
      uri = routes.SecureMessageController.addCaseworkerMessage(cdcm, "123").url,
      headers = FakeHeaders(Seq(CONTENT_TYPE -> JSON)),
      body = requestBody
    )
    when(
      mockSecureMessageService.addCaseWorkerMessageToConversation(any[String], any[String], any[CaseworkerMessage])(
        any[ExecutionContext],
        any[HeaderCarrier])).thenReturn(serviceResponse)
  }
}
