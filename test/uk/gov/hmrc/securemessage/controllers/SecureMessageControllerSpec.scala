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
import cats.data.NonEmptyList
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.http.ContentTypes._
import play.api.http.HeaderNames._
import play.api.http.HttpEntity
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.mvc.{ResponseHeader, Result}
import play.api.test.Helpers.{POST, PUT, contentAsString, defaultAwaitTimeout, status}
import play.api.test.{FakeHeaders, FakeRequest, Helpers, NoMaterializer}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.Retrieval
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.securemessage.controllers.models.generic
import uk.gov.hmrc.securemessage.controllers.models.generic._
import uk.gov.hmrc.securemessage.helpers.{ConversationUtil, Resources}
import uk.gov.hmrc.securemessage.models.core.Conversation
import uk.gov.hmrc.securemessage.models.core.ConversationStatus.Open
import uk.gov.hmrc.securemessage.models.core.Language.English
import uk.gov.hmrc.securemessage.repository.ConversationRepository
import uk.gov.hmrc.securemessage.services.SecureMessageService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
class SecureMessageControllerSpec extends PlaySpec with ScalaFutures with MockitoSugar {

  implicit val mat: Materializer = NoMaterializer
  val listOfCoreConversation = List(ConversationUtil.getFullConversation("D-80542-20201120"))

  implicit val hc: HeaderCarrier = HeaderCarrier()

  "Calling createConversation" must {
    "return CREATED (201) when sent a request with all optional fields populated" in new TestCase {
      private val fullConversationJson = Resources.readJson("model/api/create-conversation-full.json")
      private val fakeRequest = FakeRequest(
        method = PUT,
        uri = routes.SecureMessageController.createConversation("cdcm", "123").url,
        headers = FakeHeaders(Seq(CONTENT_TYPE -> JSON)),
        body = fullConversationJson
      )
      when(mockSecureMessageService.createConversation(any[ConversationRequest],any[String],any[String])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future(Result(ResponseHeader(CREATED),HttpEntity.NoEntity)))
      private val response = controller.createConversation("cdcm", "123")(fakeRequest)
      status(response) mustBe CREATED
    }
    "return CREATED (201) when sent a request with no optional fields populated" in new TestCase {
      private val minimalConversationJson = Resources.readJson("model/api/create-conversation-minimal.json")
      private val fakeRequest = FakeRequest(
        method = PUT,
        uri = routes.SecureMessageController.createConversation("cdcm", "123").url,
        headers = FakeHeaders(Seq(CONTENT_TYPE -> JSON)),
        body = minimalConversationJson
      )
      when(mockSecureMessageService.createConversation(any[ConversationRequest],any[String],any[String])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future(Result(new ResponseHeader(CREATED),HttpEntity.NoEntity)))
      private val response = controller.createConversation("cdcm", "123")(fakeRequest)
      status(response) mustBe CREATED
    }
    "return BAD REQUEST (400) when sent a request with required fields missing" in new TestCase {
      private val fakeRequest = FakeRequest(
        method = PUT,
        uri = routes.SecureMessageController.createConversation("cdcm", "123").url,
        headers = FakeHeaders(Seq(CONTENT_TYPE -> JSON)),
        body = Json.parse("""{"missing":"data"}""".stripMargin)
      )
      private val response = controller.createConversation("cdcm", "123")(fakeRequest)
      status(response) mustBe BAD_REQUEST
    }
    "return BAD REQUEST (400) when an invalid email address is provided" in new TestCase {
      private val conversationWithInvalidEmailJson = Resources.readJson("model/api/create-conversation-invalid-email.json")
      private val fakeRequest = FakeRequest(
        method = PUT,
        uri = routes.SecureMessageController.createConversation("cdcm", "123").url,
        headers = FakeHeaders(Seq(CONTENT_TYPE -> JSON)),
        body = conversationWithInvalidEmailJson
      )
      private val response = controller.createConversation("cdcm", "123")(fakeRequest)
      status(response) mustBe BAD_REQUEST
    }
    "return BAD_REQUEST (400) when the message content is not base64 encoded" in new TestCase {
      private val fullConversationJson = Resources.readJson("model/api/create-conversation-full.json")
      private val fakeRequest = FakeRequest(
        method = PUT,
        uri = routes.SecureMessageController.createConversation("cdcm", "123").url,
        headers = FakeHeaders(Seq(CONTENT_TYPE -> JSON)),
        body = fullConversationJson
      )
      when(mockSecureMessageService.createConversation(any[ConversationRequest],any[String],any[String])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future(Result(new ResponseHeader(BAD_REQUEST),HttpEntity.NoEntity)))
      private val response = controller.createConversation("cdcm", "123")(fakeRequest)
      status(response) mustBe BAD_REQUEST
    }
    "return BAD_REQUEST (400) when the message content is not valid HTML" in new TestCase {
      private val fullConversationJson = Resources.readJson("model/api/create-conversation-full.json")
      private val fakeRequest = FakeRequest(
        method = PUT,
        uri = routes.SecureMessageController.createConversation("cdcm", "123").url,
        headers = FakeHeaders(Seq(CONTENT_TYPE -> JSON)),
        body = fullConversationJson
      )
      when(mockSecureMessageService.createConversation(any[ConversationRequest],any[String],any[String])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future(Result(ResponseHeader(BAD_REQUEST),HttpEntity.NoEntity)))
      private val response = controller.createConversation("cdcm", "123")(fakeRequest)
      status(response) mustBe BAD_REQUEST
    }
  }

  "getConversations" must {
    "return an OK (200) with a JSON body of a list of conversations" in new TestCase {
      when(mockAuthConnector.authorise(any[Predicate], any[Retrieval[Enrolments]])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(
          Future.successful(
            Enrolments(
              Set(
                uk.gov.hmrc.auth.core.Enrolment(
                  key = "HMRC-CUS-ORG",
                  identifiers = Seq(EnrolmentIdentifier("EORINumber", "GB123456789")),
                  state = "",
                  None)))))
      when(mockSecureMessageService.getConversations(generic.CustomerEnrolment("HMRC-CUS-ORG", "EORINumber", "GB123456789")))
        .thenReturn(Future(List(ConversationMetadata(
          client = "cdcm",
          conversationId = "D-80542-20201120",
          subject = "D-80542-20201120",
          issueDate = DateTime.parse("2020-11-10T15:00:18.000+0000"),
          senderName = Some("Joe Bloggs"),
          unreadMessages = true,
          count = 4))))
      val response: Future[Result] = controller.getMetadataForConversations("HMRC-CUS-ORG", "EORINumber").apply(FakeRequest("GET", "/"))
      status(response) mustBe OK
      contentAsString(response) mustBe
        """[{"client":"cdcm","conversationId":"D-80542-20201120","subject":"D-80542-20201120","issueDate":"2020-11-10T15:00:18.000+0000","senderName":"Joe Bloggs","unreadMessages":true,"count":4}]"""
    }

    "return a 401 (UNAUTHORISED) error when no EORI enrolment found" in new TestCase {
      when(mockAuthConnector.authorise(any[Predicate], any[Retrieval[Enrolments]])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(
          Future.successful(
            Enrolments(
              Set(
                Enrolment(
                  key = "some other key",
                  identifiers = Seq(EnrolmentIdentifier("another enrolment", "GB123456789")),
                  state = "",
                  None)))))
      val response: Future[Result] = controller.getMetadataForConversations("HMRC-CUS-ORG", "EORINumber").apply(FakeRequest("GET", "/"))
      status(response) mustBe UNAUTHORIZED
      contentAsString(response) mustBe "\"No EORI enrolment found\""
    }
  }

  "getConversation" must {
    "return an OK (200) with a JSON body of a ApiConversations" in new TestCase {
      when(mockAuthConnector.authorise(any[Predicate], any[Retrieval[Enrolments]])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(
          Future.successful(
            Enrolments(
              Set(
                uk.gov.hmrc.auth.core.Enrolment(
                  key = "HMRC-CUS-ORG",
                  identifiers = Seq(EnrolmentIdentifier("EORINumber", "GB123456789")),
                  state = "",
                  None)))))

      when(mockSecureMessageService.getConversation(any[String], any[String], any[generic.CustomerEnrolment])(any[ExecutionContext]))
        .thenReturn(Future(Some(ApiConversation("cdcm", "D-80542-20201120", Open, Some(Map("queryId" -> "D-80542-20201120", "caseId" -> "D-80542",
          "notificationType" -> "CDS Exports", "mrn" -> "DMS7324874993", "sourceId" -> "CDCM")), "D-80542-20201120", English,
          NonEmptyList.one(
            ApiMessage(
              Some(SenderInformation(Some("CDS Exports Team"),DateTime.parse("2020-11-10T15:00:01.000Z"))),
              None,
              Some(FirstReaderInformation(None, DateTime.parse("2020-11-10T15:00:01.000Z"))),"QmxhaCBibGFoIGJsYWg="))))))
      val response: Future[Result] = controller.getConversationContent("cdcm", "D-80542-20201120", "HMRC-CUS-ORG", "EORINumber").apply(FakeRequest("GET", "/"))
      status(response) mustBe OK
      contentAsString(response) mustBe """{"client":"cdcm","conversationId":"D-80542-20201120","status":"open","tags":{"queryId":"D-80542-20201120","caseId":"D-80542","notificationType":"CDS Exports","mrn":"DMS7324874993","sourceId":"CDCM"},"subject":"D-80542-20201120","language":"en","messages":[{"senderInformation":{"name":"CDS Exports Team","created":"2020-11-10T15:00:01.000+0000"},"firstReader":{"read":"2020-11-10T15:00:01.000+0000"},"content":"QmxhaCBibGFoIGJsYWg="}]}"""
    }

    "return an BadRequest (400) with a JSON body of No conversation found" in new TestCase {
      when(mockAuthConnector.authorise(any[Predicate], any[Retrieval[Enrolments]])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(
          Future.successful(
            Enrolments(
              Set(
                uk.gov.hmrc.auth.core.Enrolment(
                  key = "HMRC-CUS-ORG",
                  identifiers = Seq(EnrolmentIdentifier("EORINumber", "GB123456789")),
                  state = "",
                  None)))))
      when(mockSecureMessageService.getConversation(any[String], any[String], any[generic.CustomerEnrolment])(any[ExecutionContext]))
        .thenReturn(Future(None))
      val response: Future[Result] = controller.getConversationContent("cdcm", "D-80542-20201120", "HMRC-CUS-ORG", "EORINumber").apply(FakeRequest("GET", "/"))
      status(response) mustBe BAD_REQUEST
      contentAsString(response) mustBe
        "\"No conversation found\""
    }

    "return a 401 (UNAUTHORISED) error when no EORI enrolment found" in new TestCase {
      when(mockAuthConnector.authorise(any[Predicate], any[Retrieval[Enrolments]])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(
          Future.successful(
            Enrolments(
              Set(
                Enrolment(
                  key = "some other key",
                  identifiers = Seq(EnrolmentIdentifier("another enrolment", "GB123456789")),
                  state = "",
                  None)))))
      val response: Future[Result] = controller.getConversationContent("cdcm", "D-80542-20201120","HMRC-CUS-ORG", "EORINumber").apply(FakeRequest("GET", "/"))
      status(response) mustBe UNAUTHORIZED
      contentAsString(response) mustBe "\"No EORI enrolment found\""
    }
  }

  "Calling createCaseworkerMessage" must {
    "return CREATED (201) when with valid payload" in new TestCase {
      private val caseworkerMessagePayload = Resources.readJson("model/api/caseworker-message.json")
      private val fakeRequest = FakeRequest(
        method = POST,
        uri = routes.SecureMessageController.createCaseworkerMessage("cdcm", "123").url,
        headers = FakeHeaders(Seq(CONTENT_TYPE -> JSON)),
        body = caseworkerMessagePayload
      )
      when(mockSecureMessageService.addCaseWorkerMessageToConversation(
        any[String],
        any[String],
        any[CaseworkerMessageRequest])(any[ExecutionContext], any[HeaderCarrier])).thenReturn(Future(()))
      private val response = controller.createCaseworkerMessage("cdcm", "123")(fakeRequest)
      status(response) mustBe CREATED
    }
    "return UNAUTHORIZED (401) when the caseworker is not a conversation participant" in new TestCase {
      private val caseworkerMessagePayload = Resources.readJson("model/api/caseworker-message.json")
      private val fakeRequest = FakeRequest(
        method = POST,
        uri = routes.SecureMessageController.createCaseworkerMessage("cdcm", "123").url,
        headers = FakeHeaders(Seq(CONTENT_TYPE -> JSON)),
        body = caseworkerMessagePayload
      )
      when(mockSecureMessageService.addCaseWorkerMessageToConversation(
        any[String],
        any[String],
        any[CaseworkerMessageRequest])(any[ExecutionContext], any[HeaderCarrier]))
        .thenReturn(Future.failed(AuthorisationException.fromString("Caseworker ID not found")))
      private val response = controller.createCaseworkerMessage("cdcm", "123")(fakeRequest)
      status(response) mustBe UNAUTHORIZED
    }
    "return BAD_REQUEST (400) when the message content is not base64 encoded" in new TestCase {
      private val caseworkerMessagePayload = Resources.readJson("model/api/caseworker-message.json")
      private val fakeRequest = FakeRequest(
        method = POST,
        uri = routes.SecureMessageController.createCaseworkerMessage("cdcm", "123").url,
        headers = FakeHeaders(Seq(CONTENT_TYPE -> JSON)),
        body = caseworkerMessagePayload
      )
      when(mockSecureMessageService.addCaseWorkerMessageToConversation(
        any[String],
        any[String],
        any[CaseworkerMessageRequest])(any[ExecutionContext], any[HeaderCarrier])).thenReturn(Future.failed(new ParseException("Not valid base64 content", 0)))
      private val response = controller.createCaseworkerMessage("cdcm", "123")(fakeRequest)
      status(response) mustBe BAD_REQUEST
      contentAsString(response) mustBe "Not valid base64 content"
    }
    "return BAD_REQUEST (400) when the message content is not valid HTML" in new TestCase {
      private val caseworkerMessagePayload = Resources.readJson("model/api/caseworker-message.json")
      private val fakeRequest = FakeRequest(
        method = POST,
        uri = routes.SecureMessageController.createCaseworkerMessage("cdcm", "123").url,
        headers = FakeHeaders(Seq(CONTENT_TYPE -> JSON)),
        body = caseworkerMessagePayload
      )
      when(mockSecureMessageService.addCaseWorkerMessageToConversation(
        any[String],
        any[String],
        any[CaseworkerMessageRequest])(any[ExecutionContext], any[HeaderCarrier])).thenReturn(Future.failed(new ParseException("Not valid HTML content", 0)))
      private val response = controller.createCaseworkerMessage("cdcm", "123")(fakeRequest)
      status(response) mustBe BAD_REQUEST
      contentAsString(response) mustBe "Not valid HTML content"
    }
  }

  "Calling createCustomerMessage" must {
    "return CREATED (201) when a message is successfully added to the conversation" in new TestCase {
      when(mockAuthConnector.authorise(any[Predicate], any[Retrieval[Enrolments]])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(
          Future.successful(
            Enrolments(
              Set(
                uk.gov.hmrc.auth.core.Enrolment(
                  key = "HMRC-CUS-ORG",
                  identifiers = Seq(EnrolmentIdentifier("EORINumber", "GB123456789")),
                  state = "",
                  None)))))
      private val fakeRequest = FakeRequest(
        method = POST,
        uri = routes.SecureMessageController.createCustomerMessage("cdcm", "D-80542-20201120").url,
        headers = FakeHeaders(Seq(CONTENT_TYPE -> JSON)),
        body = Json.obj("content" -> "PGRpdj5IZWxsbzwvZGl2Pg==")
      )
      when(mockSecureMessageService.addCustomerMessageToConversation(any[String],
        any[String],
        any[CustomerMessageRequest],
        any[Enrolments])(any[ExecutionContext])).thenReturn(Future(()))
      private val response = controller.createCustomerMessage("cdcm","D-80542-20201120")(fakeRequest)
      status(response) mustBe CREATED
    }
    "return UNAUTHORIZED (401) when the customer is not a conversation participant" in new TestCase {
      when(
        mockAuthConnector.authorise(any[Predicate], any[Retrieval[Enrolments]])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(
          Future.successful(
            Enrolments(
              Set(
                uk.gov.hmrc.auth.core.Enrolment(
                  key = "HMRC-CUS-ORG",
                  identifiers = Seq(EnrolmentIdentifier("EORINumber", "GB123456789")),
                  state = "",
                  None)))))
      private val fakeRequest = FakeRequest(
        method = POST,
        uri = routes.SecureMessageController.createCustomerMessage("cdcm", "D-80542-20201120").url,
        headers = FakeHeaders(Seq(CONTENT_TYPE -> JSON)),
        body = Json.obj("content" -> "PGRpdj5IZWxsbzwvZGl2Pg==")
      )
      when(
        mockSecureMessageService.addCustomerMessageToConversation(
          any[String],
          any[String],
          any[CustomerMessageRequest],
          any[Enrolments])(any[ExecutionContext]))
        .thenReturn(Future.failed(AuthorisationException.fromString("InsufficientEnrolments")))
      private val response = controller.createCustomerMessage("cdcm","D-80542-20201120")(fakeRequest)
      status(response) mustBe UNAUTHORIZED
    }
    "return NOT_FOUND (404) when the conversation ID is not recognised" in new TestCase {
      when(mockAuthConnector.authorise(any[Predicate], any[Retrieval[Enrolments]])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(
          Future.successful(
            Enrolments(
              Set(
                uk.gov.hmrc.auth.core.Enrolment(
                  key = "HMRC-CUS-ORG",
                  identifiers = Seq(EnrolmentIdentifier("EORINumber", "GB123456789")),
                  state = "",
                  None)))))
      private val fakeRequest = FakeRequest(
        method = POST,
        uri = routes.SecureMessageController.createCustomerMessage("cdcm", "D-80542-20201120").url,
        headers = FakeHeaders(Seq(CONTENT_TYPE -> JSON)),
        body = Json.obj("content" -> "PGRpdj5IZWxsbzwvZGl2Pg==")
      )
      when(
        mockSecureMessageService.addCustomerMessageToConversation(
          any[String],
          any[String],
          any[CustomerMessageRequest],
          any[Enrolments])(any[ExecutionContext]))
        .thenReturn(Future.failed(new IllegalArgumentException("Conversation ID not known")))
      private val response = controller.createCustomerMessage("cdcm","D-80542-20201120")(fakeRequest)
      status(response) mustBe NOT_FOUND
    }
    "return BAD_REQUEST (400) when the message content is not base64 encoded" in new TestCase {
      when(mockAuthConnector.authorise(any[Predicate], any[Retrieval[Enrolments]])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(
          Future.successful(
            Enrolments(
              Set(
                uk.gov.hmrc.auth.core.Enrolment(
                  key = "HMRC-CUS-ORG",
                  identifiers = Seq(EnrolmentIdentifier("EORINumber", "GB123456789")),
                  state = "",
                  None)))))
      private val fakeRequest = FakeRequest(
        method = POST,
        uri = routes.SecureMessageController.createCustomerMessage("cdcm", "D-80542-20201120").url,
        headers = FakeHeaders(Seq(CONTENT_TYPE -> JSON)),
        body = Json.obj("content" -> "PGRpdj5IZWxsbzwvZGl2Pg==")
      )
      when(mockSecureMessageService.addCustomerMessageToConversation(any[String],
        any[String],
        any[CustomerMessageRequest],
        any[Enrolments])(any[ExecutionContext])).thenReturn(Future.failed(new ParseException("Not valid base64 content", 0)))
      private val response = controller.createCustomerMessage("cdcm","D-80542-20201120")(fakeRequest)
      status(response) mustBe BAD_REQUEST
      contentAsString(response) mustBe "Not valid base64 content"
    }
    "return BAD_REQUEST (400) when the message content is not valid HTML" in new TestCase {
      when(mockAuthConnector.authorise(any[Predicate], any[Retrieval[Enrolments]])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(
          Future.successful(
            Enrolments(
              Set(
                uk.gov.hmrc.auth.core.Enrolment(
                  key = "HMRC-CUS-ORG",
                  identifiers = Seq(EnrolmentIdentifier("EORINumber", "GB123456789")),
                  state = "",
                  None)))))
      private val fakeRequest = FakeRequest(
        method = POST,
        uri = routes.SecureMessageController.createCustomerMessage("cdcm", "D-80542-20201120").url,
        headers = FakeHeaders(Seq(CONTENT_TYPE -> JSON)),
        body = Json.obj("content" -> "PGRpdj5IZWxsbzwvZGl2Pg==")
      )
      when(mockSecureMessageService.addCustomerMessageToConversation(any[String],
        any[String],
        any[CustomerMessageRequest],
        any[Enrolments])(any[ExecutionContext])).thenReturn(Future.failed(new ParseException("Not valid HTML content", 0)))
      private val response = controller.createCustomerMessage("cdcm","D-80542-20201120")(fakeRequest)
      status(response) mustBe BAD_REQUEST
      contentAsString(response) mustBe "Not valid HTML content"
    }
  }

  "updateReadTime" should {
    "return CREATED (201) with a JSON body of read time successfully added when a readTime has successfully been created " in new TestCase {
      when(mockAuthConnector.authorise(any[Predicate], any[Retrieval[Enrolments]])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(
          Future.successful(
            Enrolments(
              Set(
                uk.gov.hmrc.auth.core.Enrolment(
                  key = "HMRC-CUS-ORG",
                  identifiers = Seq(EnrolmentIdentifier("EORINumber", "GB123456789")),
                  state = "",
                  None)))))
      when(mockSecureMessageService.updateReadTime(any[String], any[String], any[Enrolments], any[DateTime])(any[ExecutionContext]))
        .thenReturn(Future.successful(true))
      private val fakeRequest = FakeRequest(
        method = POST,
        uri = routes.SecureMessageController.addCustomerReadTime("cdcm", "123").url,
        headers = FakeHeaders(Seq(CONTENT_TYPE -> JSON)),
        body = Json.toJson(ReadTime(DateTime.now))
      )
      val response = controller.addCustomerReadTime("cdcm", "D-80542-20201120")(fakeRequest)
      status(response) mustBe CREATED
      contentAsString(response) mustBe "\"read time successfully added\""
    }

    "return BADREQUEST (400) with a JSON body of issue with updating read time" in new TestCase {
      when(mockAuthConnector.authorise(any[Predicate], any[Retrieval[Enrolments]])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(
          Future.successful(
            Enrolments(
              Set(
                uk.gov.hmrc.auth.core.Enrolment(
                  key = "HMRC-CUS-ORG",
                  identifiers = Seq(EnrolmentIdentifier("EORINumber", "GB123456789")),
                  state = "",
                  None)))))
      when(mockSecureMessageService.updateReadTime(any[String], any[String], any[Enrolments], any[DateTime])(any[ExecutionContext]))
        .thenReturn(Future.successful(false))
      private val fakeRequest = FakeRequest(
        method = POST,
        uri = routes.SecureMessageController.addCustomerReadTime("cdcm", "123").url,
        headers = FakeHeaders(Seq(CONTENT_TYPE -> JSON)),
        body = Json.toJson(ReadTime(DateTime.now))
      )
      val response = controller.addCustomerReadTime("cdcm", "D-80542-20201120")(fakeRequest)
      status(response) mustBe BAD_REQUEST
      contentAsString(response) mustBe "\"issue with updating read time\""
    }
  }

  trait TestCase {
    val mockRepository: ConversationRepository = mock[ConversationRepository]
    val mockAuthConnector: AuthConnector = mock[AuthConnector]
    val mockSecureMessageService: SecureMessageService = mock[SecureMessageService]
    when(mockRepository.insertIfUnique(any[Conversation])(any[ExecutionContext])).thenReturn(Future.successful(true))
    val controller = new SecureMessageController(Helpers.stubControllerComponents(), mockAuthConnector, mockSecureMessageService)

  }
}
