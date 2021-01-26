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
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.http.ContentTypes._
import play.api.http.HeaderNames._
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.Helpers.{PUT, contentAsString, defaultAwaitTimeout, status}
import play.api.test.{FakeHeaders, FakeRequest, Helpers, NoMaterializer}
import uk.gov.hmrc.auth.core.retrieve.Retrieval
import uk.gov.hmrc.auth.core.{AuthConnector, Enrolment, EnrolmentIdentifier, Enrolments}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.securemessage.controllers.models.generic.ConversationDetails
import uk.gov.hmrc.securemessage.helpers.Resources
import uk.gov.hmrc.securemessage.models.core.Language.English
import uk.gov.hmrc.securemessage.models.core.{Conversation, _}
import uk.gov.hmrc.securemessage.repository.ConversationRepository
import uk.gov.hmrc.securemessage.services.SecureMessageService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
class SecureMessageControllerSpec extends PlaySpec with ScalaFutures with MockitoSugar {

  implicit val mat: Materializer = NoMaterializer
  val listOfCoreConversation = List(Conversation(
    client = "D-80542-20201120",
    conversationId = "conversationId",
    status = ConversationStatus.Open,
    tags = Some(
      Map(
        "sourceId"         -> "CDCM",
        "caseId"           -> "D-80542",
        "queryId"          -> "D-80542-20201120",
        "mrn"              -> "DMS7324874993",
        "notificationType" -> "CDS Exports"
      )),
    subject = "D-80542-20201120",
    language = English,
    participants = List(
      Participant(
        1,
        ParticipantType.System,
        Identifier("CDCM", "D-80542-20201120", None),
        Some("CDS Exports Team"),
        None),
      Participant(
        2,
        ParticipantType.Customer,
        Identifier("EORINumber", "GB1234567890", Some("HMRC-CUS-ORG")),
        Some("Joe Bloggs"),
        Some("joebloggs@test.com"))
    ),
    messages = List(Message(
      1,
      new DateTime("2020-11-10T15:00:01.000Z"),
      List(Reader(1, new DateTime("2020-11-10T15:00:01.000Z"))),
      "QmxhaCBibGFoIGJsYWg="
    ))
  )
  )

  implicit val hc: HeaderCarrier = HeaderCarrier()

  "Calling createConversation" should {

    "return CREATED (201) when sent a request with all optional fields populated" in new TestCase {
      private val fullConversationJson = Resources.readJson("model/api/create-conversation-full.json")
      private val controller = new SecureMessageController(Helpers.stubControllerComponents(), mockAuthConnector, mockSecureMessageService, mockRepository)
      private val fakeRequest = FakeRequest(
        method = PUT,
        uri = routes.SecureMessageController.createConversation("cdcm", "123").url,
        headers = FakeHeaders(Seq(CONTENT_TYPE -> JSON)),
        body = fullConversationJson
      )
      private val response = controller.createConversation("cdcm", "123")(fakeRequest)
      status(response) mustBe CREATED
    }

    "return CREATED (201) when sent a request with no optional fields populated" in new TestCase {
      private val minimalConversationJson = Resources.readJson("model/api/create-conversation-minimal.json")
      private val controller = new SecureMessageController(Helpers.stubControllerComponents(), mockAuthConnector, mockSecureMessageService, mockRepository)
      private val fakeRequest = FakeRequest(
        method = PUT,
        uri = routes.SecureMessageController.createConversation("cdcm", "123").url,
        headers = FakeHeaders(Seq(CONTENT_TYPE -> JSON)),
        body = minimalConversationJson
      )
      private val response = controller.createConversation("cdcm", "123")(fakeRequest)
      status(response) mustBe CREATED
    }

    "return BAD REQUEST (400) when sent a request with required fields missing" in new TestCase {
      private val controller = new SecureMessageController(Helpers.stubControllerComponents(), mockAuthConnector, mockSecureMessageService, mockRepository)
      private val fakeRequest = FakeRequest(
        method = PUT,
        uri = routes.SecureMessageController.createConversation("cdcm", "123").url,
        headers = FakeHeaders(Seq(CONTENT_TYPE -> JSON)),
        body = Json.parse("""{"missing":"data"}""".stripMargin)
      )
      private val response = controller.createConversation("cdcm", "123")(fakeRequest)
      status(response) mustBe BAD_REQUEST
    }
  }

  "Calling getConversations" should {
    "return an OK (200) with a JSON body of a list of conversations" in new TestCase {
      when(mockAuthConnector.authorise(any(), any[Retrieval[Enrolments]])(any(), any()))
        .thenReturn(
          Future.successful(
            Enrolments(
              Set(
                Enrolment(
                  key = "HMRC-CUS-ORG",
                  identifiers = Seq(EnrolmentIdentifier("EORINumber", "GB123456789")),
                  state = "",
                  None)))))
      when(mockSecureMessageService.getConversations(Identifier(name = "EORINumber", value = "GB123456789", enrolment = Some("HMRC-CUS-ORG"))))
        .thenReturn(List(ConversationDetails(conversationId = "D-80542-20201120",
          subject = "D-80542-20201120",
          issueDate = Some(DateTime.parse("2020-11-10T15:00:18.000+0000")),
          senderName = Some("Joe Bloggs"),
          unreadMessages = true,
          count = 4)))
      val controller = new SecureMessageController(Helpers.stubControllerComponents(), mockAuthConnector, mockSecureMessageService, mockRepository)
      val response: Future[Result] = controller.getListOfConversations().apply(FakeRequest("GET", "/"))
      status(response) mustBe OK
      contentAsString(response) mustBe
        """[{"conversationId":"D-80542-20201120","subject":"D-80542-20201120","issueDate":"2020-11-10T15:00:18.000+0000","senderName":"Joe Bloggs","unreadMessages":true,"count":4}]"""
    }

    "return a 401 (UNAUTHORISED) error when no EORI enrolment found" in new TestCase {
      when(mockAuthConnector.authorise(any(), any[Retrieval[Enrolments]])(any(), any()))
        .thenReturn(
          Future.successful(
            Enrolments(
              Set(
                Enrolment(
                  key = "some other key",
                  identifiers = Seq(EnrolmentIdentifier("another enrolment", "GB123456789")),
                  state = "",
                  None)))))
      val controller = new SecureMessageController(Helpers.stubControllerComponents(), mockAuthConnector, mockSecureMessageService, mockRepository)
      val response: Future[Result] = controller.getListOfConversations().apply(FakeRequest("GET", "/"))
      status(response) mustBe UNAUTHORIZED
      contentAsString(response) mustBe "\"No EORI enrolment found\""
    }
  }

  trait TestCase {
    val mockRepository: ConversationRepository = mock[ConversationRepository]
    val mockAuthConnector: AuthConnector = mock[AuthConnector]
    val mockSecureMessageService: SecureMessageService = mock[SecureMessageService]
    when(mockRepository.insertIfUnique(any[Conversation])(any[ExecutionContext])).thenReturn(Future.successful(true))
  }
}
