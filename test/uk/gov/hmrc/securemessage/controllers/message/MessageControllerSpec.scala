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

package uk.gov.hmrc.securemessage.controllers.message

import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.testkit.NoMaterializer
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.http.Status.*
import play.api.i18n.Messages
import play.api.libs.json.JsValue
import play.api.mvc.*
import play.api.test.Helpers.{ POST, defaultAwaitTimeout, status, stubMessages }
import play.api.test.{ FakeHeaders, FakeRequest, Helpers }
import uk.gov.hmrc.common.message.util.SecureMessageUtil as commonUtil
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.securemessage.UnitTest
import uk.gov.hmrc.securemessage.controllers.message.MessageController
import uk.gov.hmrc.securemessage.controllers.{ SecureMessageController, SecureMessageUtil }
import uk.gov.hmrc.securemessage.helpers.Resources

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ ExecutionContext, Future }

class MessageControllerSpec extends PlaySpec with ScalaFutures with MockitoSugar with OptionValues with UnitTest {

  implicit val mat: Materializer = NoMaterializer
  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit val messages: Messages = stubMessages()

  "createMessage" must {
    "return CREATED for the valid message" in
      new TestCase(requestBody = Resources.readJson("model/core/v3/HmrcMtdVat.json")) {

        val mockSecureMessageAction: Action[AnyContent] =
          DefaultActionBuilder(cc.parsers.defaultBodyParser)(cc.executionContext) { _ =>
            Results.Created
          }
        when(mockSecureMessageController.createMessage()).thenReturn(mockSecureMessageAction)

        val response = controller.createMessageForV3()(fakeRequest)
        status(response) mustBe CREATED
      }
    "return BAD_REQUEST for the message with missing mandatory fields" in
      new TestCase(requestBody = Resources.readJson("model/core/v3/missing_mandatory_fields.json")) {
        val response = controller.createMessageForV3()(fakeRequest)
        status(response) mustBe BAD_REQUEST
      }
  }

  class TestCase(requestBody: JsValue) {
    val mockSecureMessageController: SecureMessageController = mock[SecureMessageController]
    val mockSecuremessageUtil: SecureMessageUtil = mock[SecureMessageUtil]
    val cc: ControllerComponents = Helpers.stubControllerComponents()
    val controller = new MessageController(cc, mockSecureMessageController, mockSecuremessageUtil)

    val fakeRequest = FakeRequest(POST, "/messages", FakeHeaders(), requestBody)

    when(mockSecuremessageUtil.auditCreateMessageForFailure(any())(any(), any())).thenReturn(Future.successful(()))

  }
}
