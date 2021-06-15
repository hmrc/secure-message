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
import org.joda.time.{ DateTime, LocalDate }
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
import play.api.mvc.{ AnyContentAsEmpty, Request, Result }
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
import uk.gov.hmrc.securemessage.controllers.model.common.read.MessageMetadata
import uk.gov.hmrc.securemessage.controllers.model.common.write.CustomerMessage
import uk.gov.hmrc.securemessage.controllers.model.{ ClientName, MessageType }
import uk.gov.hmrc.securemessage.helpers.Resources
import uk.gov.hmrc.securemessage.models.core.Letter._
import uk.gov.hmrc.securemessage.models.core._
import uk.gov.hmrc.securemessage.repository.ConversationRepository
import uk.gov.hmrc.securemessage.services.SecureMessageService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ ExecutionContext, ExecutionException, Future }

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
        "Error on message with client: Some(CDCM), message id: 123, error message: conflict error")
    }

    "return InternalServerError (500) when there is an error storing the conversation" in new CreateConversationTestCase(
      requestBody = Resources.readJson("model/api/cdcm/write/create-conversation.json"),
      serviceResponse = Future.successful(Left(StoreError("mongo error", None)))
    ) {
      private val response = controller.createConversation(cdcm, "123")(fakeRequest)
      status(response) mustBe INTERNAL_SERVER_ERROR
      contentAsJson(response) mustBe Json.toJson(
        """Error on message with client: Some(CDCM), message id: 123, error message: mongo error""")
    }

    "return CREATED (201) when there is an error sending the email" in new CreateConversationTestCase(
      requestBody = Resources.readJson("model/api/cdcm/write/create-conversation.json"),
      serviceResponse = Future.successful(Left(EmailSendingError("email error")))
    ) {
      private val response = controller.createConversation(cdcm, "123")(fakeRequest)
      status(response) mustBe CREATED
      contentAsJson(response) mustBe Json.toJson(
        "Error on message with client: Some(CDCM), message id: 123, error message: email error")
    }

    "return CREATED (201) when no email can be found" in new CreateConversationTestCase(
      requestBody = Resources.readJson("model/api/cdcm/write/create-conversation.json"),
      serviceResponse = Future.successful(Left(NoReceiverEmailError("Verified email address could not be found")))
    ) {
      private val response = controller.createConversation(cdcm, "123")(fakeRequest)
      status(response) mustBe CREATED
      contentAsJson(response) mustBe Json.toJson(
        "Error on message with client: Some(CDCM), message id: 123, error message: Verified email address could not be found")
    }

    "return InternalServerError (500) if unexpected SecureMessageError returned" in new CreateConversationTestCase(
      requestBody = Resources.readJson("model/api/cdcm/write/create-conversation.json"),
      serviceResponse = Future.successful(Left(new SecureMessageError("some unknown err")))
    ) {
      private val response = controller.createConversation(cdcm, "123")(fakeRequest)
      status(response) mustBe INTERNAL_SERVER_ERROR
      contentAsJson(response) mustBe Json.toJson(
        "Error on message with client: Some(CDCM), message id: 123, error message: some unknown err")
    }

    "return BAD_REQUEST (400) when the message content is not base64 encoded" in new CreateConversationTestCase(
      requestBody = Resources.readJson("model/api/cdcm/write/create-conversation.json"),
      serviceResponse = Future.successful(Left(InvalidContent("Not valid base64 content")))
    ) {
      private val response = controller.createConversation(cdcm, "123")(fakeRequest)
      status(response) mustBe BAD_REQUEST
      contentAsJson(response) mustBe Json.toJson(
        "Error on message with client: Some(CDCM), message id: 123, error message: Not valid base64 content")
    }

    "return BAD_REQUEST (400) when the message content is not valid HTML" in new CreateConversationTestCase(
      requestBody = Resources.readJson("model/api/cdcm/write/create-conversation.json"),
      serviceResponse = Future.successful(Left(InvalidContent("Not valid html content")))
    ) {
      private val response = controller.createConversation(cdcm, "123")(fakeRequest)
      status(response) mustBe BAD_REQUEST
      contentAsJson(response) mustBe Json.toJson(
        "Error on message with client: Some(CDCM), message id: 123, error message: Not valid html content")
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

  "getContentDetail" must {
    val objectID = BSONObjectID.generate()
    "return a conversation" in new GetConversationByIdTestCase(
      storedConversation = Some(
        Resources.readJson("model/api/cdcm/read/api-conversation.json").as[JsObject] + ("_id" -> Json.toJson(
          objectID)))) {
      val response: Future[Result] = controller
        .getMessage(base64Encode(s"${MessageType.Conversation.entryName}/${objectID.stringify}"))
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
        .getMessage(base64Encode(s"${MessageType.Conversation.entryName}/${objectID.stringify}"))
        .apply(FakeRequest("GET", "/"))
      status(response) mustBe OK
      contentAsJson(response).as[ApiConversation] mustBe apiConversation.right.get

    }

    "return Not Found (404) with a JSON body of No conversation found" in new GetConversationByIdTestCase(
      storedConversation = None) {
      val response: Future[Result] = controller
        .getMessage(base64Encode(s"${MessageType.Conversation.entryName}/${objectID.stringify}"))
        .apply(FakeRequest("GET", "/"))
      status(response) mustBe NOT_FOUND
      contentAsString(response) must include("conversations not found")
    }

    "return Unauthorized (401) when no enrolment found" in new TestCase(Set.empty[CustomerEnrolment]) {
      private val encodedId: String = base64Encode(s"${MessageType.Conversation.entryName}/${objectID.stringify}")
      private val response = controller
        .getMessage(encodedId)
        .apply(FakeRequest("GET", "/"))
      status(response) mustBe UNAUTHORIZED
      contentAsString(response) mustBe s""""Error on message with client: None, message id: $encodedId, error message: No enrolment found""""
    }

    "return a letter" in new GetMessageByIdTestCase(
      storedLetter = Some(Resources.readJson("model/core/letter.json").as[JsObject] + ("_id" -> Json.toJson(objectID))
        + ("lastUpdated"                                                                     -> Json.toJson(DateTime.now())))) {
      val response: Future[Result] = controller
        .getMessage(base64Encode(s"${MessageType.Letter.entryName}/${objectID.stringify}"))
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
        .getMessage(base64Encode(s"${MessageType.Letter.entryName}/${objectID.stringify}"))
        .apply(FakeRequest("GET", "/"))
      status(response) mustBe OK
      contentAsJson(response).as[ApiLetter] mustBe apiLetter.get

    }

    "return Not Found (404) with a JSON body of No letter found" in new TestCase {
      when(mockSecureMessageService.getLetter(any[String], any[Set[CustomerEnrolment]])(any[ExecutionContext]))
        .thenReturn(Future.successful(Left(MessageNotFound("letter not found"))))

      val response: Future[Result] = controller
        .getMessage(base64Encode(s"${MessageType.Letter.entryName}/${objectID.stringify}"))
        .apply(FakeRequest("GET", "/"))
      status(response) mustBe NOT_FOUND
      // contentAsString(response) mustBe "\"No Letter found\""
    }

    "return Unauthorized (401) when no enrolment found for a letter" in new TestCase(Set.empty[CustomerEnrolment]) {
      private val encodedId: String = base64Encode(s"${MessageType.Letter.entryName}/${objectID.stringify}")
      private val response = controller
        .getMessage(encodedId)
        .apply(FakeRequest("GET", "/"))
      status(response) mustBe UNAUTHORIZED
      contentAsString(response) mustBe s""""Error on message with client: None, message id: $encodedId, error message: No enrolment found""""
    }

    "return BadRequest(Invalid message type) if messageType is invalid" in new GetMessageByIdTestCase(
      storedLetter = Some(Resources.readJson("model/core/letter.json").as[JsObject] + ("_id" -> Json.toJson(objectID))
        + ("lastUpdated"                                                                     -> Json.toJson(DateTime.now())))) {
      private val objId: String = objectID.stringify
      private val messageType = "SomeRandomType"
      private val rawId: String = base64Encode(s"$messageType/$objId")
      val response: Future[Result] = controller
        .getMessage(rawId)
        .apply(FakeRequest("GET", "/"))
      status(response) mustBe BAD_REQUEST
      contentAsString(response) must include("Invalid encoded id")
    }

    "return BadRequest if decoding cant find id" in new GetMessageByIdTestCase(
      storedLetter = Some(Resources.readJson("model/core/letter.json").as[JsObject] + ("_id" -> Json.toJson(objectID))
        + ("lastUpdated"                                                                     -> Json.toJson(DateTime.now())))) {
      val response: Future[Result] = controller
        .getMessage(base64Encode(s"${MessageType.Letter.entryName}"))
        .apply(FakeRequest("GET", "/"))
      status(response) mustBe BAD_REQUEST
      contentAsString(response) must include("Invalid encoded id")
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
      contentAsString(response) mustBe "\"Error on message with client: Some(CDCM), message id: 123, error message: Not valid base64 content\""
    }
    "return BAD_REQUEST (400) when the message content is not valid HTML" in new CreateCaseWorkerMessageTestCase(
      requestBody = Resources.readJson("model/api/cdcm/write/caseworker-message.json"),
      serviceResponse = Future.successful(Left(InvalidContent("Not valid HTML content")))
    ) {
      private val response = controller.addCaseworkerMessage(cdcm, "123")(fakeRequest)
      status(response) mustBe BAD_REQUEST
      contentAsString(response) mustBe "\"Error on message with client: Some(CDCM), message id: 123, error message: Not valid HTML content\""
    }
  }

  "createCustomerMessage" must {
    "return CREATED (201) when a message is successfully added to the conversation" in new CreateCustomerMessageTestCase(
      serviceResponse = Future.successful(Right(()))) {
      private val response = controller.addCustomerMessage(encodedId)(fakeRequest)
      status(response) mustBe CREATED
    }
    "return UNAUTHORIZED (401) when the customer is not a conversation participant" in new CreateCustomerMessageTestCase(
      serviceResponse = Future.successful(Left(ParticipantNotFound("InsufficientEnrolments")))) {
      private val response = controller.addCustomerMessage(encodedId)(fakeRequest)
      status(response) mustBe UNAUTHORIZED
    }
    "return NOT_FOUND (404) when the conversation ID is not recognised" in new CreateCustomerMessageTestCase(
      serviceResponse = Future.successful(Left(MessageNotFound("Conversation ID not known")))) {
      private val response = controller.addCustomerMessage(encodedId)(fakeRequest)
      status(response) mustBe NOT_FOUND
    }
    "return Bad Gateway (502) when the message cannot be forwarded to EIS" in new CreateCustomerMessageTestCase(
      serviceResponse = Future.successful(Left(EisForwardingError("Failed to forward message to EIS")))) {
      private val response = controller.addCustomerMessage(encodedId)(fakeRequest)
      status(response) mustBe BAD_GATEWAY
    }
  }

  "getMessages" must {
    "return metadata for both conversations and letters" in new GetMessagesTestCase() {
      val response: Future[Result] = controller.getMessages(None, Some(List(testEnrolment)), None)(fakeRequest)
      status(response) mustBe OK
      contentAsJson(response).as[List[MessageMetadata]] mustBe messagesMetadata
    }
    "return metadata for conversations when no letters" in new GetMessagesTestCase(storedLetters = List()) {
      val response: Future[Result] = controller.getMessages(None, Some(List(testEnrolment)), None)(fakeRequest)
      status(response) mustBe OK
      contentAsJson(response).as[List[MessageMetadata]] mustBe conversationsMetadata
    }
    "return metadata for letters when no conversations" in new GetMessagesTestCase(storedConversations = List()) {
      val response: Future[Result] = controller.getMessages(None, Some(List(testEnrolment)), None)(fakeRequest)
      status(response) mustBe OK
      contentAsJson(response).as[List[MessageMetadata]] mustBe lettersMetadata
    }
    "return empty list when no conversations and no letters" in new GetMessagesTestCase(
      storedConversations = List(),
      storedLetters = List()) {
      val response: Future[Result] = controller.getMessages(None, Some(List(testEnrolment)), None)(fakeRequest)
      status(response) mustBe OK
      contentAsJson(response).as[List[MessageMetadata]] mustBe empty
    }
  }

  "getMessagesCount" must {
    "return count" in new GetMessagesTestCase() {
      val response: Future[Result] = controller.getMessagesCount(None, Some(List(testEnrolment)), None)(fakeRequest)
      status(response) mustBe OK
      contentAsJson(response).as[Count] mustBe Count(1, 1)
    }

  }
  class TestCase(authEnrolments: Set[CustomerEnrolment] = Set(testEnrolment)) {
    val mockRepository: ConversationRepository = mock[ConversationRepository]
    val mockAuthConnector: AuthConnector = mock[AuthConnector]
    val mockAuditConnector: AuditConnector = mock[AuditConnector]
    val mockSecureMessageService: SecureMessageService = mock[SecureMessageService]
    when(mockRepository.insertIfUnique(any[Conversation])(any[ExecutionContext]))
      .thenReturn(Future.successful(Right(())))

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

  class CreateCustomerMessageTestCase(serviceResponse: Future[Either[SecureMessageError, Unit]]) extends TestCase {
    val encodedId: String = base64Encode(MessageType.Conversation.entryName + "/" + "D-80542-20201120")
    val fakeRequest: FakeRequest[JsObject] = FakeRequest(
      method = POST,
      uri = routes.SecureMessageController.addCustomerMessage(encodedId).url,
      headers = FakeHeaders(Seq(CONTENT_TYPE -> JSON)),
      body = Json.obj("content" -> "PGRpdj5IZWxsbzwvZGl2Pg==")
    )
    when(
      mockSecureMessageService
        .addCustomerMessage(any[String], any[CustomerMessage], any[Enrolments])(any[ExecutionContext], any[Request[_]]))
      .thenReturn(serviceResponse)

  }

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
        .getConversations(
          eqTo(authEnrolmentsFrom(authEnrolments)),
          eqTo(Filters(filterEnrolmentKeys, Some(customerEnrolments.toList), filterTags)))(
          any[ExecutionContext],
          any[Messages]))
      .thenReturn(Future.successful(conversationsMetadata))
  }

  class GetMessagesTestCase(
    storedConversations: List[JsValue] = List(Resources.readJson("model/core/full-db-conversation.json")),
    storedLetters: List[JsValue] = List(Resources.readJson("model/core/full-db-letter.json")),
    authEnrolments: Set[CustomerEnrolment] = Set(testEnrolment),
    customerEnrolments: Set[CustomerEnrolment] = Set(testEnrolment),
    filterTags: Option[List[FilterTag]] = None,
    filterEnrolmentKeys: Option[List[String]] = None
  ) extends TestCase(authEnrolments) {
    val conversations: List[Conversation] = storedConversations.map(_.as[Conversation])
    val conversationsMetadata: List[MessageMetadata] = List(
      Resources.readJson("model/core/full-db-conversation-metadata.json").as[MessageMetadata])
    val letters: List[Letter] = storedLetters.map(_.as[Letter])
    val lettersMetadata: List[MessageMetadata] = List(
      Resources.readJson("model/core/full-db-letter-metadata.json").as[MessageMetadata])
    val messages: List[Message] = conversations ++ letters
    val messagesMetadata: List[MessageMetadata] = conversationsMetadata ++ lettersMetadata
    when(
      mockSecureMessageService
        .getMessages(
          eqTo(authEnrolmentsFrom(authEnrolments)),
          eqTo(Filters(filterEnrolmentKeys, Some(customerEnrolments.toList), filterTags)))(any[ExecutionContext]))
      .thenReturn(Future.successful(messages))

    when(
      mockSecureMessageService.getMessagesCount(
        eqTo(authEnrolmentsFrom(authEnrolments)),
        eqTo(Filters(filterEnrolmentKeys, Some(customerEnrolments.toList), filterTags)))(any[ExecutionContext]))
      .thenReturn(Future.successful(Count(1, 1)))

    val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest("GET", "/")
  }

  class GetConversationTestCase(
    storedConversation: Option[JsValue],
    authEnrolments: Set[CustomerEnrolment] = Set(testEnrolment))
      extends TestCase(authEnrolments) {
    val apiConversation: Either[MessageNotFound, ApiConversation] = storedConversation match {
      case Some(conversation) => Right(conversation.as[ApiConversation])
      case _                  => Left(MessageNotFound("conversations not found"))
    }
    when(mockSecureMessageService.getConversation(any[String], any[Set[CustomerEnrolment]])(any[ExecutionContext]))
      .thenReturn(Future.successful(apiConversation))
  }

  class GetConversationByIdTestCase(
    storedConversation: Option[JsValue],
    authEnrolments: Set[CustomerEnrolment] = Set(testEnrolment))
      extends TestCase(authEnrolments) {
    val apiConversation: Either[MessageNotFound, ApiConversation] = storedConversation match {
      case Some(conversation) => Right(conversation.as[ApiConversation])
      case _                  => Left(MessageNotFound("conversations not found"))
    }
    when(mockSecureMessageService.getConversation(any[String], any[Set[CustomerEnrolment]])(any[ExecutionContext]))
      .thenReturn(Future.successful(apiConversation))
  }

  class GetMessageByIdTestCase(
    storedLetter: Option[JsValue],
    authEnrolments: Set[CustomerEnrolment] = Set(testEnrolment))
      extends TestCase(authEnrolments) {
    val letter = storedLetter.map(l => l.validate[Letter]).map(_.get)
    val apiLetter =
      letter.map(
        l =>
          ApiLetter(
            l.subject,
            l.content,
            None,
            SenderInformation("HMRC", LocalDate.now),
            l.recipient.identifier,
            None,
            None))
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
