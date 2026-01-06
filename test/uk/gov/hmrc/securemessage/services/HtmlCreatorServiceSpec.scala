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

package uk.gov.hmrc.securemessage.services

import org.mongodb.scala.bson.ObjectId
import play.api.Configuration
import play.api.test.Helpers.*
import play.twirl.api.Html
import uk.gov.hmrc.common.message.model.{ AlertDetails, ConversationItem, Details, EmailAlert, ExternalRef, Lifecycle, MailgunStatus, Message, MessageContentParameters, RenderUrl, Rescindment, TaxEntity }
import uk.gov.hmrc.mongo.workitem.ProcessingStatus
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.securemessage.SpecBase
import uk.gov.hmrc.securemessage.TestData.*
import uk.gov.hmrc.securemessage.models.RenderType.CustomerLink

import java.time.{ Instant, LocalDate }

class HtmlCreatorServiceSpec extends SpecBase {

  "createConversation" must {
    "return correct html content" when {
      "enquiryType starts with p800" in new Setup {

        val conversationItemList: List[ConversationItem] = List(ConversationItem(message))

        val result: Either[String, Html] = await(
          service.createConversation(
            latestMessageId = TEST_MSG_ID,
            messages = conversationItemList,
            replyType = CustomerLink
          )
        )

        result mustBe Right(
          Html(
            <h1 class="govuk-heading-xl margin-top-small margin-bottom-small">sub_test</h1>
              <p class="faded-text--small">This message was sent on 06 January 2026</p> <div></div>.mkString
          )
        )
      }

      "enquiryType starts with p800 and message's body is of type 2wsm-advisor" in new Setup {
        val messageBody: Option[Details] =
          Some(TEST_DETAILS.copy(enquiryType = Some("p800_D2"), `type` = Some("2wsm-advisor")))

        val conversationItemList: List[ConversationItem] = List(ConversationItem(message.copy(body = messageBody)))

        val result: Either[String, Html] = await(
          service.createConversation(
            latestMessageId = TEST_MSG_ID,
            messages = conversationItemList,
            replyType = CustomerLink
          )
        )

        result mustBe Right(
          Html(
            <h1 class="govuk-heading-xl margin-top-small margin-bottom-small">sub_test</h1>
              <p class="faded-text--small">This message was sent to you on 06 January 2026</p><div></div>
              <a href="test_url" target="_blank" rel="noopener noreferrer">Contact HMRC (opens in a new window or tab)</a>.mkString
          )
        )
      }

      "enquiryType does not starts with p800" in new Setup {
        val messageBody: Option[Details] =
          Some(TEST_DETAILS.copy(enquiryType = Some("p_D2"), `type` = Some("2wsm-advisor")))

        val messageWithNonP800: Message = message.copy(body = messageBody)

        val conversationItemList: List[ConversationItem] = List(ConversationItem(messageWithNonP800))

        val result: Either[String, Html] = await(
          service.createConversation(
            latestMessageId = TEST_MSG_ID,
            messages = conversationItemList,
            replyType = CustomerLink
          )
        )

        result.map { html =>
          assert(
            html.body.contains(<h1 class="govuk-heading-xl margin-top-small margin-bottom-small">sub_test</h1>.mkString)
          )
          assert(
            html.body.contains(
              <p class="faded-text--small">This message was sent to you on 06 January 2026</p>.mkString
            )
          )
        }
      }
    }
  }

  "getAdviserDatesText" must {
    "return correct text" when {

      "conversation type is 2wsm-advisor" in new Setup {
        val messageBody: Option[Details] =
          Some(TEST_DETAILS.copy(enquiryType = Some("p_D2"), `type` = Some("2wsm-advisor")))

        val conversationItem: ConversationItem = ConversationItem(message.copy(body = messageBody))

        val result: String = service.getAdviserDatesText(conversationItem)
        result must be("06 January 2026 by HMRC:")
      }

      "conversation type is 2wsm-customer" in new Setup {
        val messageBody: Option[Details] =
          Some(TEST_DETAILS.copy(enquiryType = Some("p_D2"), `type` = Some("2wsm-customer")))

        val conversationItem: ConversationItem = ConversationItem(message.copy(body = messageBody))

        val result: String = service.getAdviserDatesText(conversationItem)
        result must be("06 January 2026 by the customer:")
      }

      "conversation type is other than 2wsm-advisor and 2wsm-customer" in new Setup {
        val messageBody: Option[Details] =
          Some(TEST_DETAILS.copy(enquiryType = Some("p_D2"), `type` = Some("2wsm-unknown")))

        val conversationItem: ConversationItem = ConversationItem(message.copy(body = messageBody))

        val result: String = service.getAdviserDatesText(conversationItem)
        result must be("This message was sent on 06 January 2026")
      }

      "conversation has no body" in new Setup {
        val conversationItem: ConversationItem = ConversationItem(message.copy(body = None))

        val result: String = service.getAdviserDatesText(conversationItem)
        result must be("This message was sent on 20 December 2025")
      }
    }
  }

  trait Setup {
    val servicesConfig = new ServicesConfig(Configuration("contact-hmrc-url" -> "test_url"))
    val service: HtmlCreatorService = new HtmlCreatorService(servicesConfig)

    val message: Message = Message(
      id = new ObjectId("6021481d59f23de1fe8389db"),
      recipient = TEST_TAX_ENTITY,
      subject = TEST_SUBJECT,
      body = Some(TEST_DETAILS),
      validFrom = TEST_DATE,
      alertFrom = Some(TEST_DATE),
      alertDetails = AlertDetails(TEST_TEMPLATE_ID, None, TEST_PARAMETERS),
      alerts = Some(TEST_EMAIL_ALERT),
      lastUpdated = Some(TEST_TIME_INSTANT),
      hash = TEST_HASH,
      statutory = true,
      renderUrl = TEST_RENDER_URL,
      sourceData = Some(TEST_SOURCE)
    )
  }
}
