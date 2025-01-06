/*
 * Copyright 2024 HM Revenue & Customs
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

import org.apache.commons.codec.binary.Base64
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.testkit.NoMaterializer
import org.mockito.ArgumentMatchers.{ any, eq as eqTo }
import org.mockito.Mockito.when
import org.mongodb.scala.bson.ObjectId
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.http.ContentTypes.*
import play.api.http.HeaderNames.*
import play.api.http.Status.*
import play.api.i18n.Messages
import play.api.libs.json.{ JsNull, JsObject, JsResult, JsValue, Json, OFormat }
import play.api.mvc.{ AnyContent, AnyContentAsEmpty, Request, Result }
import play.api.test.Helpers.{ POST, PUT, contentAsJson, contentAsString, defaultAwaitTimeout, status, stubMessages }
import play.api.test.{ FakeHeaders, FakeRequest, Helpers }
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpVerbs.GET
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.securemessage.helpers.Resources
import uk.gov.hmrc.securemessage.services.{ HtmlCreatorService, SAMessageRenderer, SAMessageRendererService, SecureMessageServiceImpl }
import uk.gov.hmrc.auth.core.*
import uk.gov.hmrc.common.message.model.{ ConversationItem, MessageContentParameters }
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.securemessage.models.core.Letter.*
import uk.gov.hmrc.securemessage.models.core.*
import uk.gov.hmrc.securemessage.templates.satemplates.helpers.PortalUrlBuilder

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ ExecutionContext, ExecutionException, Future }

class SecureMessageRendererSpec extends PlaySpec with ScalaFutures with MockitoSugar {

  "view" must {
    val messageId = new ObjectId
    val fakeRequest = FakeRequest(GET, routes.SecureMessageRenderer.view(messageId).url)
    val storedLetter: JsValue =
      Resources.readJson("model/core/letter.json").as[JsObject] + ("_id" -> Json.toJson(messageId))
    val letter: Option[Letter] = storedLetter.validate[Letter].asOpt

    "return OK with ats_v2 template when a V3 message exists" in new TestCase {
      val contentParams: Option[MessageContentParameters] = Some(MessageContentParameters(JsNull, "ats_v2"))
      val atsLetter: Option[Letter] = letter.map(_.copy(contentParameters = contentParams))

      when(
        mockSecureMessageService
          .getLetter(any[ObjectId])(any[ExecutionContext])
      )
        .thenReturn(Future.successful(atsLetter))
      val response: Future[Result] = controller.view(messageId)(fakeRequest)
      status(response) mustBe OK
      contentAsString(response) must include("Annual Tax Summary")
      contentAsString(response) must include("This message was sent to you on 26 April 2021")
    }

    "return InternalServerError when no V3 message exists" in new TestCase {
      when(
        mockSecureMessageService
          .getLetter(any[ObjectId])(any[ExecutionContext])
      )
        .thenReturn(Future.successful(None))
      val response: Future[Result] = controller.view(messageId)(fakeRequest)
      status(response) mustBe INTERNAL_SERVER_ERROR
    }

    "return InternalServerError when non ats message is requested via ats endpoint" in new TestCase {
      val nonAtsletter: Option[Letter] = storedLetter.validate[Letter].asOpt

      when(
        mockSecureMessageService
          .getLetter(any[ObjectId])(any[ExecutionContext])
      )
        .thenReturn(Future.successful(None))
      val response: Future[Result] = controller.view(messageId)(fakeRequest)
      status(response) mustBe INTERNAL_SERVER_ERROR
    }
  }

  "getContentBy" must {
    val messageId = new ObjectId().toString
    val fakeRequest = FakeRequest(GET, routes.SecureMessageRenderer.getContentBy(messageId, "").url)
    val conversationItemList: List[ConversationItem] = List(
      ConversationItem(
        messageId,
        "test-subject",
        None,
        LocalDate.of(2024, 12, 3),
        Some(Base64.encodeBase64String("test-content".getBytes("UTF-8")))
      )
    )

    "return OK with two-way-message template for message-type as 'Customer'" in new TestCase {
      when(
        mockSecureMessageService
          .findMessageListById(any[String])(any[HeaderCarrier], any[ExecutionContext])
      )
        .thenReturn(Future.successful(Right(conversationItemList)))

      val response: Future[Result] = controller.getContentBy(messageId, "Customer")(fakeRequest)
      status(response) mustBe OK
      contentAsString(response) must include("test-subject")
      contentAsString(response) must include("This message was sent on 03 December 2024")
    }

    "return OK with two-way-message template for message-type as 'Adviser'" in new TestCase {
      when(
        mockSecureMessageService
          .findMessageListById(any[String])(any[HeaderCarrier], any[ExecutionContext])
      )
        .thenReturn(Future.successful(Right(conversationItemList)))

      val response: Future[Result] = controller.getContentBy(messageId, "Adviser")(fakeRequest)
      status(response) mustBe OK
      contentAsString(response) must include("This message was sent on 03 December 2024")
    }

    "return BadRequest when message type is not Customer or Advisor" in new TestCase {
      val response: Future[Result] = controller.getContentBy(messageId, "Advisor")(fakeRequest)
      status(response) mustBe BAD_REQUEST
    }
  }

  class TestCase {
    val mockAuthConnector: AuthConnector = mock[AuthConnector]
    val mockAuditConnector: AuditConnector = mock[AuditConnector]
    val mockSecureMessageService: SecureMessageServiceImpl = mock[SecureMessageServiceImpl]
    val mockConfig: ServicesConfig = mock[ServicesConfig]
    val mockHtmlCreatorService: HtmlCreatorService = new HtmlCreatorService(mockConfig)
    val mockSAMessageRenderer: SAMessageRenderer = new SAMessageRenderer()(Helpers.stubMessages())
    val mockPortalUrlBuilder: PortalUrlBuilder = new PortalUrlBuilder(mockConfig)
    val mockSAMessageRendererService: SAMessageRendererService =
      new SAMessageRendererService(mockConfig, mockSAMessageRenderer, mockPortalUrlBuilder)
    when(mockConfig.getString(any[String])).thenReturn("test-url")

    val controller =
      new SecureMessageRenderer(
        Helpers.stubControllerComponents(),
        mockAuthConnector,
        mockAuditConnector,
        mockSecureMessageService,
        mockHtmlCreatorService,
        mockSAMessageRendererService
      )
  }
}
