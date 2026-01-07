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
import play.api.i18n.Messages
import play.api.libs.json.{ JsString, JsValue, Json }
import play.api.test.{ FakeHeaders, FakeRequest }
import play.api.test.Helpers.POST
import uk.gov.hmrc.securemessage.SpecBase
import uk.gov.hmrc.securemessage.TestData.*
import uk.gov.hmrc.securemessage.models.ShowLinkJourneyStep
import uk.gov.hmrc.securemessage.models.core.{ AlertDetails, Letter, Recipient, RenderUrl }
import play.api.test.Helpers.*
import play.twirl.api.Html
import uk.gov.hmrc.common.message.model.MessageContentParameters
import uk.gov.hmrc.securemessage.models.core.Details
import uk.gov.hmrc.securemessage.templates.satemplates.r002a.{ Electronic, R002A_v1ContentParams, Taxpayer }

import java.time.Instant

class SAMessageRendererServiceSpec extends SpecBase {

  "apply" must {
    "return the correct output" when {
      "there are no contentParameters in the Message and form id is SA300" in new Setup {
        val result: Html =
          await(service(message = letter, journeyStep = Some(ShowLinkJourneyStep(TEST_URL)), utr = "1234567890"))

        assert(
          result.body.contains(
            <p>Your new Self Assessment statement has been prepared. You'll be able to view it online within 4 working days.</p>.mkString
          )
        )

        assert(
          result.body.contains(
            <p>Your statement will tell you if you owe any payments to HM Revenue and Customs, or if you're due a refund.</p>.mkString
          )
        )
      }

      "there are no contentParameters in the Message and form id is SS300" in new Setup {
        val msgBody: Details =
          Details(form = Some("SS300"), `type` = Some(TEST_TYPE), suppressedAt = None, detailsId = None)

        val result: Html =
          await(
            service(
              message = letter.copy(body = Some(msgBody)),
              journeyStep = Some(ShowLinkJourneyStep(TEST_URL)),
              utr = "1234567890"
            )
          )

        assert(
          result.body.contains(
            <p>Your new Self Assessment statement has been prepared. You'll be able to view it online within 4 working days.</p>.mkString
          )
        )

        assert(
          result.body.contains(
            <p>Your statement will tell you if you owe any payments to HM Revenue and Customs, or if you're due a refund.</p>.mkString
          )
        )
      }

      "contentParameters are present in the Message" in new Setup {
        val R002AContentParams: JsValue =
          Json.toJson(R002A_v1ContentParams(BigDecimal(10), Some(BigDecimal(2)), Electronic, "test", Taxpayer))

        val contentParams: Option[MessageContentParameters] =
          Some(MessageContentParameters(R002AContentParams, "R002A_v1"))

        val msg: Letter = letter.copy(contentParameters = contentParams)

        val result: Html =
          await(service(message = msg, journeyStep = Some(ShowLinkJourneyStep(TEST_URL)), utr = "1234567890"))

        assert(result.body.contains(<h3>Payment details</h3>.mkString))
        assert(result.body.contains(<p>An electronic payment will be made shortly to:</p>.mkString))
        assert(result.body.contains(<p>test</p>.mkString))
      }
    }
  }

  trait Setup {
    implicit val fakeRequest: FakeRequest[JsString] = FakeRequest(POST, "/messages", FakeHeaders(), JsString("test"))
    implicit val messages: Messages = stubMessages()

    val formId = "SA300"

    val letter: Letter = Letter(
      _id = new ObjectId(),
      subject = TEST_SUBJECT,
      validFrom = TEST_DATE,
      hash = TEST_HASH,
      alertQueue = None,
      alertFrom = None,
      status = TEST_STATUS,
      content = Some(TEST_CONTENT),
      statutory = false,
      lastUpdated = Some(TEST_TIME_INSTANT),
      recipient = Recipient(TEST_REGIME, TEST_IDENTIFIER, Some(TEST_EMAIL_ADDRESS_VALUE)),
      renderUrl = RenderUrl(TEST_SERVICE_NAME, TEST_URL),
      externalRef = None,
      alertDetails = AlertDetails(TEST_TEMPLATE_ID, None),
      readTime = Some(TEST_TIME_INSTANT),
      body = Some(
        Details(form = Some(formId), `type` = Some(TEST_TYPE), suppressedAt = None, detailsId = None)
      )
    )

    val service: SAMessageRendererService = app.injector.instanceOf[SAMessageRendererService]
  }
}
