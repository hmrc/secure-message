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
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.mongodb.scala.bson.ObjectId
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.http.Status.*
import play.api.libs.json.{ JsNull, JsObject, JsValue, Json }
import play.api.mvc.Result
import play.api.test.Helpers.{ contentAsString, defaultAwaitTimeout, status }
import play.api.test.{ FakeRequest, Helpers }
import uk.gov.hmrc.auth.core.*
import uk.gov.hmrc.common.message.model.{ ConversationItem, MessageContentParameters }
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpVerbs.GET
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.securemessage.helpers.Resources
import uk.gov.hmrc.securemessage.models.core.*
import uk.gov.hmrc.securemessage.models.core.Letter.*
import uk.gov.hmrc.securemessage.models.{ AckJourneyStep, ReplyFormJourneyStep, ShowLinkJourneyStep }
import uk.gov.hmrc.securemessage.services.{ HtmlCreatorService, SAMessageRendererService, SecureMessageServiceImpl }
import uk.gov.hmrc.securemessage.templates.satemplates.helpers.{ DailyPenalty, PortalUrlBuilder, TaxYear }
import uk.gov.hmrc.securemessage.templates.satemplates.r002a.{ Electronic, R002A_v1ContentParams, Taxpayer }
import uk.gov.hmrc.securemessage.templates.satemplates.sa326d.SA326D_v1ContentParams
import uk.gov.hmrc.securemessage.templates.satemplates.sa328d.SA328D_v1ContentParams
import uk.gov.hmrc.securemessage.templates.satemplates.sa370.{ Filing12MonthsPenaltyParams, Filing6MonthsPenaltyParams, SA370_v1ContentParams }
import uk.gov.hmrc.securemessage.templates.satemplates.sa371.SA371_v1ContentParams
import uk.gov.hmrc.securemessage.templates.satemplates.sa372.SA372_ContentParams
import uk.gov.hmrc.securemessage.templates.satemplates.sa37X.*

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ ExecutionContext, Future }

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

  "renderMessageUnencryptedUrl" must {
    val messageId = new ObjectId()
    val fakeRequest = FakeRequest(
      GET,
      routes.SecureMessageRenderer
        .renderMessageUnencryptedUrl("utr", messageId.toString, Some(ShowLinkJourneyStep("/returnUrl")))
        .url
    )
    val storedLetter: JsValue =
      Resources.readJson("model/core/letter.json").as[JsObject] + ("_id" -> Json.toJson(messageId))
    val letter: Option[Letter] = storedLetter.validate[Letter].asOpt

    "return OK with sa messages template for templateId 'IgnorePaperFiling_v1' " in new TestCase {
      val contentParams: Option[MessageContentParameters] =
        Some(MessageContentParameters(JsNull, "IgnorePaperFiling_v1"))
      val saMessage: Option[Letter] = letter.map(_.copy(contentParameters = contentParams))

      when(mockAuthConnector.authorise(any(), any())(any(), any())).thenReturn(Future.successful {})
      when(
        mockSecureMessageService
          .getLetter(any[ObjectId])(any[ExecutionContext])
      )
        .thenReturn(Future.successful(saMessage))

      val response: Future[Result] =
        controller.renderMessageUnencryptedUrl("utr", messageId.toString, Some(ShowLinkJourneyStep("/returnUrl")))(
          fakeRequest
        )
      status(response) mustBe OK
      contentAsString(response) must include(
        "We understand that switching from paper to digital messages is a big change. As this is the first year we have sent online messages instead of letters and you have not yet filed your return, we are sending you a paper Notice to File."
      )
    }

    "return OK with sa messages template for templateId 'R002A_v1' " in new TestCase {
      val R002AContentParams: JsValue =
        Json.toJson(R002A_v1ContentParams(BigDecimal(10), Some(BigDecimal(2)), Electronic, "test", Taxpayer))
      val contentParams: Option[MessageContentParameters] =
        Some(MessageContentParameters(R002AContentParams, "R002A_v1"))
      val saMessage: Option[Letter] = letter.map(_.copy(contentParameters = contentParams))

      when(mockAuthConnector.authorise(any(), any())(any(), any())).thenReturn(Future.successful {})
      when(
        mockSecureMessageService
          .getLetter(any[ObjectId])(any[ExecutionContext])
      )
        .thenReturn(Future.successful(saMessage))

      val response: Future[Result] =
        controller.renderMessageUnencryptedUrl("utr", messageId.toString, Some(ShowLinkJourneyStep("/returnUrl")))(
          fakeRequest
        )
      status(response) mustBe OK
      contentAsString(response) must include("Test have subjects11")
      contentAsString(response) must include("This message was sent to you on 26 April 2021")
      contentAsString(response) must include("You're due a tax refund of £10.00.")
    }

    "return OK with sa messages template for templateId 'SA326D_filed_v1' " in new TestCase {
      val SA326DContentParams: JsValue = Json.toJson(
        SA326D_v1ContentParams(
          TaxYear(2023, 2024),
          LocalDate.now(),
          None,
          None,
          false,
          Some(DailyPenalty(Some(LocalDate.now()), None))
        )
      )
      val contentParams: Option[MessageContentParameters] =
        Some(MessageContentParameters(SA326DContentParams, "SA326D_filed_v1"))
      val saMessage: Option[Letter] = letter.map(_.copy(contentParameters = contentParams))

      when(mockAuthConnector.authorise(any(), any())(any(), any())).thenReturn(Future.successful {})
      when(
        mockSecureMessageService
          .getLetter(any[ObjectId])(any[ExecutionContext])
      )
        .thenReturn(Future.successful(saMessage))

      val response: Future[Result] =
        controller.renderMessageUnencryptedUrl("utr", messageId.toString, Some(ShowLinkJourneyStep("/returnUrl")))(
          fakeRequest
        )
      status(response) mustBe OK
      contentAsString(response) must include("Test have subjects11")
      contentAsString(response) must include("This message was sent to you on 26 April 2021")
      contentAsString(response) must include("Your tax return for the 2023 to 2024 tax year was late.")
    }

    "return OK with sa messages template for templateId 'SA326D_not_filed_v1' " in new TestCase {
      val SA326DContentParams: JsValue = Json.toJson(
        SA326D_v1ContentParams(
          TaxYear(2023, 2024),
          LocalDate.now(),
          Some(LocalDate.now()),
          None,
          false,
          Some(DailyPenalty(Some(LocalDate.now()), None))
        )
      )
      val contentParams: Option[MessageContentParameters] =
        Some(MessageContentParameters(SA326DContentParams, "SA326D_not_filed_v1"))
      val saMessage: Option[Letter] = letter.map(_.copy(contentParameters = contentParams))

      when(mockAuthConnector.authorise(any(), any())(any(), any())).thenReturn(Future.successful {})
      when(
        mockSecureMessageService
          .getLetter(any[ObjectId])(any[ExecutionContext])
      )
        .thenReturn(Future.successful(saMessage))

      val response: Future[Result] =
        controller.renderMessageUnencryptedUrl("utr", messageId.toString, Some(ShowLinkJourneyStep("/returnUrl")))(
          fakeRequest
        )
      status(response) mustBe OK
      contentAsString(response) must include("Test have subjects11")
      contentAsString(response) must include("This message was sent to you on 26 April 2021")
      contentAsString(response) must include("send your tax return and pay any tax you owe")
    }

    "return OK with sa messages template for templateId 'SA328D_v1' " in new TestCase {
      val SA328DContentParams: JsValue = Json.toJson(
        SA328D_v1ContentParams(
          TaxYear(2023, 2024),
          LocalDate.now(),
          Some("Partnership name")
        )
      )
      val contentParams: Option[MessageContentParameters] =
        Some(MessageContentParameters(SA328DContentParams, "SA328D_v1"))
      val saMessage: Option[Letter] = letter.map(_.copy(contentParameters = contentParams))

      when(mockAuthConnector.authorise(any(), any())(any(), any())).thenReturn(Future.successful {})
      when(
        mockSecureMessageService
          .getLetter(any[ObjectId])(any[ExecutionContext])
      )
        .thenReturn(Future.successful(saMessage))

      val response: Future[Result] =
        controller.renderMessageUnencryptedUrl("utr", messageId.toString, Some(ReplyFormJourneyStep("/returnUrl")))(
          fakeRequest
        )
      status(response) mustBe OK
      contentAsString(response) must include("Test have subjects11")
      contentAsString(response) must include("This message was sent to you on 26 April 2021")
      contentAsString(response) must include("The 2023 to 2024 tax return for Partnership name is late.")
    }

    "return OK with sa messages template for templateId 'SA370_v1' " in new TestCase {
      val SA370ContentParams: JsValue = Json.toJson(
        SA370_v1ContentParams(
          TaxYear(2023, 2024),
          "£100",
          true,
          true,
          true,
          true,
          false,
          Seq(
            Penalty(
              "Filing12MonthsAmendedPenalty_v1",
              Json.toJson(
                Filing12MonthsPenaltyParams(
                  "50",
                  "45",
                  "10",
                  "40"
                )
              )
            )
          ),
          Seq(
            Penalty(
              "Filing6MonthsAmendedPenalty_v1",
              Json.toJson(
                Filing6MonthsPenaltyParams(
                  "20",
                  "25",
                  "10",
                  "30"
                )
              )
            )
          )
        )
      )
      val contentParams: Option[MessageContentParameters] =
        Some(MessageContentParameters(SA370ContentParams, "SA370_v1"))
      val saMessage: Option[Letter] = letter.map(_.copy(contentParameters = contentParams))

      when(mockAuthConnector.authorise(any(), any())(any(), any())).thenReturn(Future.successful {})
      when(
        mockSecureMessageService
          .getLetter(any[ObjectId])(any[ExecutionContext])
      )
        .thenReturn(Future.successful(saMessage))

      val response: Future[Result] =
        controller.renderMessageUnencryptedUrl("utr", messageId.toString, Some(ReplyFormJourneyStep("/returnUrl")))(
          fakeRequest
        )
      status(response) mustBe OK
      contentAsString(response) must include("Test have subjects11")
      contentAsString(response) must include("This message was sent to you on 26 April 2021")
      contentAsString(response) must include(
        "12 months late – now you have filed an amended tax return you have a further penalty to pay."
      )
      contentAsString(response) must include(
        "6 months late – now you have filed an amended tax return you have a further penalty to pay."
      )
    }

    "return OK with sa messages template for templateId 'SA372'" in new TestCase {
      val SA372ContentParams: JsValue = Json.toJson(
        SA372_ContentParams(
          TaxYear(2023, 2024),
          Some(LocalDate.now()),
          true,
          true
        )
      )
      val contentParams: Option[MessageContentParameters] =
        Some(MessageContentParameters(SA372ContentParams, "SA372"))
      val saMessage: Option[Letter] = letter.map(_.copy(contentParameters = contentParams))

      when(mockAuthConnector.authorise(any(), any())(any(), any())).thenReturn(Future.successful {})
      when(
        mockSecureMessageService
          .getLetter(any[ObjectId])(any[ExecutionContext])
      )
        .thenReturn(Future.successful(saMessage))

      val response: Future[Result] =
        controller.renderMessageUnencryptedUrl("utr", messageId.toString, Some(ShowLinkJourneyStep("/returnUrl")))(
          fakeRequest
        )
      status(response) mustBe OK
      contentAsString(response) must include("Test have subjects11")
      contentAsString(response) must include("This message was sent to you on 26 April 2021")
      contentAsString(response) must include("Your tax return for the 2023 to 2024 tax year is late.")
    }

    "return OK with sa messages template for templateId 'SA300_v1' " in new TestCase {
      val contentParams: Option[MessageContentParameters] =
        Some(MessageContentParameters(JsNull, "SA300_v1"))
      val saMessage: Option[Letter] = letter.map(_.copy(contentParameters = contentParams))

      when(mockAuthConnector.authorise(any(), any())(any(), any())).thenReturn(Future.successful {})
      when(
        mockSecureMessageService
          .getLetter(any[ObjectId])(any[ExecutionContext])
      )
        .thenReturn(Future.successful(saMessage))

      val response: Future[Result] =
        controller.renderMessageUnencryptedUrl("utr", messageId.toString, Some(AckJourneyStep))(
          fakeRequest
        )
      status(response) mustBe OK
      contentAsString(response) must include("Test have subjects11")
      contentAsString(response) must include("This message was sent to you on 26 April 2021")
      contentAsString(response) must include(
        "Your new Self Assessment statement has been prepared. You'll be able to view it online within 4 working days."
      )
    }

    "return OK with sa messages template for templateId 'SA371_v1' " in new TestCase {
      val SA371v1ContentParams: JsValue = Json.toJson(
        SA371_v1ContentParams(
          TaxYear(2023, 2024),
          "£100",
          "",
          true,
          true,
          false,
          Seq(
            Penalty(
              "FilingSecond3MonthsOnlinePenalty_v1",
              Json.toJson(
                FilingSecond3MonthsPenaltyParams(
                  "2",
                  "10",
                  "10/01/2016",
                  "10/03/2016",
                  "600"
                )
              )
            ),
            Penalty(
              "Filing6MonthsMinimumPenalty_v1",
              Json.toJson(
                Filing6MonthsMinimumPenaltyParams(
                  "50"
                )
              )
            )
          )
        )
      )
      val contentParams: Option[MessageContentParameters] =
        Some(MessageContentParameters(SA371v1ContentParams, "SA371_v1"))
      val saMessage: Option[Letter] = letter.map(_.copy(contentParameters = contentParams))

      when(mockAuthConnector.authorise(any(), any())(any(), any())).thenReturn(Future.successful {})
      when(
        mockSecureMessageService
          .getLetter(any[ObjectId])(any[ExecutionContext])
      )
        .thenReturn(Future.successful(saMessage))

      val response: Future[Result] =
        controller.renderMessageUnencryptedUrl("utr", messageId.toString, Some(ShowLinkJourneyStep("/returnUrl")))(
          fakeRequest
        )
      status(response) mustBe OK
      contentAsString(response) must include("Test have subjects11")
      contentAsString(response) must include("This message was sent to you on 26 April 2021")
      contentAsString(response) must include("6 months late – a penalty of £50.")
      contentAsString(response) must include("3 months late – a daily penalty of £2 a day for 10 days.")
    }

    "return OK with a message when user has insufficient enrollments" in new TestCase {
      when(mockAuthConnector.authorise(any(), any())(any(), any()))
        .thenReturn(Future.failed(InsufficientEnrolments("error")))
      val response: Future[Result] =
        controller.renderMessageUnencryptedUrl("utr", messageId.toString, Some(ShowLinkJourneyStep("/returnUrl")))(
          fakeRequest
        )
      status(response) mustBe OK
      contentAsString(response) must include("You need to activate your Self Assessment to view your message content.")
    }
  }

  class TestCase {
    val mockAuthConnector: AuthConnector = mock[AuthConnector]
    val mockAuditConnector: AuditConnector = mock[AuditConnector]
    val mockSecureMessageService: SecureMessageServiceImpl = mock[SecureMessageServiceImpl]
    val mockConfig: ServicesConfig = mock[ServicesConfig]
    val mockHtmlCreatorService: HtmlCreatorService = new HtmlCreatorService(mockConfig)
    val mockPortalUrlBuilder: PortalUrlBuilder = new PortalUrlBuilder(mockConfig)
    val mockSAMessageRendererService: SAMessageRendererService =
      new SAMessageRendererService(mockConfig, mockPortalUrlBuilder)
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
