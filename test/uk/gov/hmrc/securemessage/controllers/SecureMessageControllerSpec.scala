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
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.PlaySpec
import play.api.http.ContentTypes._
import play.api.http.HeaderNames._
import play.api.http.Status._
import play.api.libs.json.{ JsValue, Json }
import play.api.test.Helpers.{ PUT, defaultAwaitTimeout, status }
import play.api.test.{ FakeHeaders, FakeRequest, Helpers, NoMaterializer }
import uk.gov.hmrc.securemessage.helpers.Resources

class SecureMessageControllerSpec extends PlaySpec with ScalaFutures {

  implicit val mat: Materializer = NoMaterializer

  "Calling createConversation" should {
    "return CREATED (201) when sent a request with all optional fields populated" in {
      val fullConversationJson: JsValue = Resources.readJson("model/api/create-conversation-full.json")
      val controller = new SecureMessageController(Helpers.stubControllerComponents())
      val fakeRequest = FakeRequest(
        method = PUT,
        uri = routes.SecureMessageController.createConversation("cdcm", "123").url,
        headers = FakeHeaders(Seq(CONTENT_TYPE -> JSON)),
        body = fullConversationJson
      )
      val response = controller.createConversation("cdcm", "123")(fakeRequest)
      status(response) mustBe CREATED
    }

    "return CREATED (201) when sent a request with no optional fields populated" in {
      val minimalConversationJson: JsValue = Resources.readJson("model/api/create-conversation-minimal.json")
      val controller = new SecureMessageController(Helpers.stubControllerComponents())
      val fakeRequest = FakeRequest(
        method = PUT,
        uri = routes.SecureMessageController.createConversation("cdcm", "123").url,
        headers = FakeHeaders(Seq(CONTENT_TYPE -> JSON)),
        body = minimalConversationJson
      )
      val response = controller.createConversation("cdcm", "123")(fakeRequest)
      status(response) mustBe CREATED
    }

    "return BAD REQUEST (400) when sent a request with required fields missing" in {
      val controller = new SecureMessageController(Helpers.stubControllerComponents())
      val fakeRequest = FakeRequest(
        method = PUT,
        uri = routes.SecureMessageController.createConversation("cdcm", "123").url,
        headers = FakeHeaders(Seq(CONTENT_TYPE -> JSON)),
        body = Json.parse("""{"missing":"data"}""".stripMargin)
      )
      val response = controller.createConversation("cdcm", "123")(fakeRequest)
      status(response) mustBe BAD_REQUEST
    }
  }
}
