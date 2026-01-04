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

package uk.gov.hmrc.securemessage.handlers

import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.testkit.NoMaterializer
import org.mockito.ArgumentMatchers.{ any, eq as eqTo }
import org.mockito.Mockito.when
import org.mongodb.scala.bson.ObjectId
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.i18n.Messages
import play.api.libs.json.{ JsValue, Json }
import play.api.test.Helpers.{ await, stubMessages }
import play.api.test.Helpers.*
import uk.gov.hmrc.common.message.model.Language.English
import uk.gov.hmrc.common.message.model.{ MessageContentParameters, MessagesCount }
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.domain.TaxIds.TaxIdWithName
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.securemessage.{ MessageNotFound, SecureMessageError, UnitTest }
import uk.gov.hmrc.securemessage.connectors.AuthIdentifiersConnector
import uk.gov.hmrc.securemessage.controllers.model.{ ApiMessage, MessageResourceResponse, ServiceUrl }
import uk.gov.hmrc.securemessage.controllers.model.common.read.MessageMetadata
import uk.gov.hmrc.securemessage.helpers.Resources
import uk.gov.hmrc.securemessage.models.core.*
import uk.gov.hmrc.securemessage.services.SecureMessageServiceImpl
import uk.gov.hmrc.auth.core.{ Enrolment, EnrolmentIdentifier, Enrolments }
import uk.gov.hmrc.securemessage.TestData.{ TEST_DATE, TEST_DATE_STRING, TEST_EMAIL_ADDRESS_VALUE, TEST_FORM, TEST_HASH, TEST_IDENTIFIER, TEST_REGIME, TEST_SERVICE_NAME, TEST_SUBJECT, TEST_TEMPLATE_ID, TEST_TIME_INSTANT, TEST_TYPE, TEST_URL }
import uk.gov.hmrc.securemessage.controllers.model.MessageType.Letter
import uk.gov.hmrc.securemessage.controllers.utils.IdCoder.DecodedId

import java.time.{ Instant, LocalDate }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ ExecutionContext, Future }

class NonCDSMessageRetrieverSpec extends PlaySpec with MockitoSugar with UnitTest with ScalaFutures {

  "fetch messages" must {
    "return metadata for both conversations and letters" in new TestCase {
      val result: Future[JsValue] =
        retriever.fetch(MessageRequestWrapper(None, None, None, MessageFilter(List("nino"))), English)(hc, stubMsgs)
      result.map(_.as[List[MessageMetadata]] mustBe letters)
    }
  }

  "getMessagesCount" must {
    "return count" in new TestCase() {
      val result: Future[JsValue] =
        retriever.messageCount(MessageRequestWrapper(None, None, None, MessageFilter(List("nino"))))(hc, stubMsgs)
      result.map(_.as[List[MessagesCount]] mustBe MessagesCount(1, 1))
    }
  }

  "updateMessageContent" must {
    "update the message content" in new TestCase {
      val result: Future[Option[Letter]] = retriever.updateMessageContent(Some(storedLetter))
      result.futureValue mustBe Some(expectedLetterJson.as[Letter])
    }
  }

  "getMessage" must {
    "return correct output" when {
      "messageType is Letter and letter is found for the provided messageId" in new TestCase {
        val enrolment: Enrolment = Enrolment(
          "bar",
          List(EnrolmentIdentifier("key-a", "val-a"), EnrolmentIdentifier("key-b", "val-b")),
          "activated",
          None
        )

        val enrolments: Enrolments = Enrolments(Set(enrolment))
        val messageId: DecodedId = "123414566667676767676767"

        val msgReadReq: MessageReadRequest =
          MessageReadRequest(messageType = Letter, authEnrolments = enrolments, messageId = messageId)

        val letter: Letter = uk.gov.hmrc.securemessage.models.core.Letter(
          _id = new ObjectId("6021481d59f23de1fe8389db"),
          subject = TEST_SUBJECT,
          validFrom = TEST_DATE,
          hash = TEST_HASH,
          alertQueue = None,
          alertFrom = None,
          status = "test_status",
          content = None,
          statutory = true,
          lastUpdated = Some(TEST_TIME_INSTANT),
          recipient =
            Recipient(regime = TEST_REGIME, identifier = TEST_IDENTIFIER, email = Some(TEST_EMAIL_ADDRESS_VALUE)),
          renderUrl = RenderUrl(TEST_SERVICE_NAME, TEST_URL),
          externalRef = None,
          alertDetails = AlertDetails(templateId = TEST_TEMPLATE_ID, recipientName = None),
          alerts = None,
          readTime = None,
          replyTo = Some(TEST_EMAIL_ADDRESS_VALUE),
          tags = None
        )

        when(mockSecureMessageService.getLetter(any)(any)).thenReturn(Future.successful(Some(letter)))
        when(mockAuthConnector.isStrideUser(any)).thenReturn(Future.successful(true))

        val result: Either[SecureMessageError, ApiMessage] = await(retriever.getMessage(msgReadReq))

        result must be(
          Right(
            MessageResourceResponse(
              id = "6021481d59f23de1fe8389db",
              subject = TEST_SUBJECT,
              body = None,
              validFrom = TEST_DATE,
              readTime =
                Left(ServiceUrl("secure-message", "/secure-messaging/messages/6021481d59f23de1fe8389db/read-time ")),
              contentParameters = None,
              sentInError = false,
              renderUrl = ServiceUrl(TEST_SERVICE_NAME, TEST_URL)
            )
          )
        )
      }
    }
  }

  "getMessagesContentChain" must {
    "return the correct list of content" in new TestCase {
      val details: Details = Details(
        form = Some(TEST_FORM),
        `type` = Some(TEST_TYPE),
        suppressedAt = Some(TEST_DATE_STRING),
        detailsId = Some(TEST_TEMPLATE_ID),
        replyTo = Some("6021481d59f23de1fe8389db")
      )

      val letter: Letter = uk.gov.hmrc.securemessage.models.core.Letter(
        _id = new ObjectId("6021481d59f23de1fe8389db"),
        subject = TEST_SUBJECT,
        validFrom = TEST_DATE,
        hash = TEST_HASH,
        alertQueue = None,
        alertFrom = None,
        status = "test_status",
        content = None,
        statutory = true,
        lastUpdated = Some(TEST_TIME_INSTANT),
        recipient =
          Recipient(regime = TEST_REGIME, identifier = TEST_IDENTIFIER, email = Some(TEST_EMAIL_ADDRESS_VALUE)),
        renderUrl = RenderUrl(TEST_SERVICE_NAME, TEST_URL),
        externalRef = None,
        alertDetails = AlertDetails(templateId = TEST_TEMPLATE_ID, recipientName = None),
        alerts = None,
        readTime = None,
        replyTo = Some(TEST_EMAIL_ADDRESS_VALUE),
        tags = None,
        body = None
      )

      when(mockSecureMessageService.getLetter(any)(any)).thenReturn(Future.successful(Some(letter)))

      val objectId = new ObjectId("6021481d59f23de1fe8389db")

      val result: List[DecodedId] = await(retriever.getMessagesContentChain(objectId))

      val expectedContentList: List[DecodedId] = List(
        <h1 lang="en" class="govuk-heading-xl">sub_test</h1>.mkString ++
          <p class="message_time faded-text--small govuk-body">date.text.advisor</p><br/>.mkString
      )

      result must be(expectedContentList)
    }
  }

  "findAndSetReadTime" must {
    "return MessageNotFound error when no message is found for the provided object id" in new TestCase {
      val details: Details = Details(
        form = Some(TEST_FORM),
        `type` = Some(TEST_TYPE),
        suppressedAt = Some(TEST_DATE_STRING),
        detailsId = Some(TEST_TEMPLATE_ID),
        replyTo = Some("6021481d59f23de1fe8389db")
      )

      val letter: Letter = uk.gov.hmrc.securemessage.models.core.Letter(
        _id = new ObjectId("6021481d59f23de1fe8389db"),
        subject = TEST_SUBJECT,
        validFrom = TEST_DATE,
        hash = TEST_HASH,
        alertQueue = None,
        alertFrom = None,
        status = "test_status",
        content = None,
        statutory = true,
        lastUpdated = Some(TEST_TIME_INSTANT),
        recipient = Recipient(
          regime = TEST_REGIME,
          identifier = TEST_IDENTIFIER.copy(name = "nino", value = "SJ123456A"),
          email = Some(TEST_EMAIL_ADDRESS_VALUE)
        ),
        renderUrl = RenderUrl(TEST_SERVICE_NAME, TEST_URL),
        externalRef = None,
        alertDetails = AlertDetails(templateId = TEST_TEMPLATE_ID, recipientName = None),
        alerts = None,
        readTime = None,
        replyTo = Some(TEST_EMAIL_ADDRESS_VALUE),
        tags = None,
        body = None
      )

      when(mockSecureMessageService.getLetter(any)(any)).thenReturn(Future.successful(None))
      when(mockSecureMessageService.getSecureMessage(any)(any)).thenReturn(Future.successful(None))
      when(mockAuthConnector.isStrideUser(any)).thenReturn(Future.successful(true))

      val objectId = new ObjectId("6021481d59f23de1fe8389db")

      val result: Either[SecureMessageError, Option[Message]] = await(retriever.findAndSetReadTime(objectId))

      result must be(Right(None))
    }

    "return the message with updated read time" in new TestCase {
      val details: Details = Details(
        form = Some(TEST_FORM),
        `type` = Some(TEST_TYPE),
        suppressedAt = Some(TEST_DATE_STRING),
        detailsId = Some(TEST_TEMPLATE_ID),
        replyTo = Some("6021481d59f23de1fe8389db")
      )

      val letter: Letter = uk.gov.hmrc.securemessage.models.core.Letter(
        _id = new ObjectId("6021481d59f23de1fe8389db"),
        subject = TEST_SUBJECT,
        validFrom = TEST_DATE,
        hash = TEST_HASH,
        alertQueue = None,
        alertFrom = None,
        status = "test_status",
        content = None,
        statutory = true,
        lastUpdated = Some(TEST_TIME_INSTANT),
        recipient = Recipient(
          regime = TEST_REGIME,
          identifier = TEST_IDENTIFIER.copy(name = "nino", value = "SJ123456A"),
          email = Some(TEST_EMAIL_ADDRESS_VALUE)
        ),
        renderUrl = RenderUrl(TEST_SERVICE_NAME, TEST_URL),
        externalRef = None,
        alertDetails = AlertDetails(templateId = TEST_TEMPLATE_ID, recipientName = None),
        alerts = None,
        readTime = None,
        replyTo = Some(TEST_EMAIL_ADDRESS_VALUE),
        tags = None,
        body = None
      )

      when(mockSecureMessageService.getLetter(any)(any)).thenReturn(Future.successful(Some(letter)))
      when(mockAuthConnector.isStrideUser(any)).thenReturn(Future.successful(true))
      when(mockSecureMessageService.setReadTime(any[Letter])(any)).thenReturn(Future.successful(Right(letter)))

      val objectId = new ObjectId("6021481d59f23de1fe8389db")

      val result: Either[SecureMessageError, Option[Message]] = await(retriever.findAndSetReadTime(objectId))

      result must be(Right(None))
    }
  }

  class TestCase {
    implicit val mat: Materializer = NoMaterializer
    implicit val hc: HeaderCarrier = HeaderCarrier()
    implicit val stubMsgs: Messages = stubMessages()
    implicit lazy val lang: Language = English

    val mockSecureMessageService: SecureMessageServiceImpl = mock[SecureMessageServiceImpl]
    val mockAuthConnector: AuthIdentifiersConnector = mock[AuthIdentifiersConnector]

    val retriever = new NonCDSMessageRetriever(mockAuthConnector, mockSecureMessageService)

    val authTaxIds: Set[TaxIdWithName] = Set(Nino("SJ123456A"))

    val storedLetter: Letter = Resources.readJson("model/core/letter_for_message.json").as[Letter]
    val letters: List[Letter] = List(storedLetter)

    val expectedLetterJson: JsValue = Json.parse(
      """
        |{
        |  "_id": {
        |    "$oid": "609a5bd50100006c1800272d"
        |  },
        |  "subject": "Test have subjects11",
        |  "validFrom": "2021-04-26",
        |  "hash": "LfK755SXhY2rlc9kL50ohJZ2dvRzZGjU74kjcdJMAcY=",
        |  "alertQueue": "DEFAULT",
        |  "alertFrom": "2021-04-26",
        |  "status": "succeeded",
        |  "content": "<h1 lang=\"en\" class=\"govuk-heading-xl\">Test have subjects11</h1><p class=\"message_time faded-text--small govuk-body\">date.text.advisor</p><br/><h2>Test content</h2>",
        |  "statutory": false,
        |  "lastUpdated": {
        |    "$date": {
        |      "$numberLong": "1620728789509"
        |    }
        |  },
        |  "recipient": {
        |    "regime": "cds",
        |    "identifier": {
        |      "name": "nino",
        |      "value": "SJ123456A"
        |    },
        |    "email": "test@test.com"
        |  },
        |  "renderUrl": {
        |    "service": "message",
        |    "url": "/messages/6086dc1f4700009fed2f5745/content"
        |  },
        |  "externalRef": {
        |    "id": "1234567891234567892",
        |    "source": "mdtp"
        |  },
        |  "alertDetails": {
        |    "templateId": "cds_ddi_setup_dcs_alert"
        |  },
        |  "alerts": {
        |    "emailAddress": "test@test.com",
        |    "success": true
        |  },
        |  "tags": {
        |    "notificationType": "Direct Debit"
        |  }
        |}
        |""".stripMargin
    )

    val messageFilter: MessageFilter = MessageFilter(List("nino"))

    when(
      mockSecureMessageService
        .getMessagesList(eqTo(authTaxIds))(any[ExecutionContext], any[HeaderCarrier], eqTo(messageFilter))
    )
      .thenReturn(Future.successful(letters))

    when(
      mockSecureMessageService
        .getMessagesCount(eqTo(authTaxIds))(any[ExecutionContext], any[HeaderCarrier], eqTo(messageFilter))
    )
      .thenReturn(Future.successful(MessagesCount(1, 1)))

    when(mockAuthConnector.currentEffectiveTaxIdentifiers(any[HeaderCarrier])).thenReturn(Future.successful(authTaxIds))
  }
}
