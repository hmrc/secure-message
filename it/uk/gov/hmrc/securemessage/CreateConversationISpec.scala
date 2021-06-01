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

package uk.gov.hmrc.securemessage

import org.scalatest.DoNotDiscover
import play.api.http.Status.{ BAD_REQUEST, CREATED }
import play.api.http.{ ContentTypes, HeaderNames }
import play.api.libs.json.Json
import play.api.libs.ws.{ WSClient, WSResponse }
import play.api.test.Helpers._
import java.io.File

@DoNotDiscover
@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
class CreateConversationISpec extends ISpec {

  "A PUT request to /secure-messaging/conversation/{client}/{conversationId}" should {

    "return CREATED when sent a full and valid JSON payload" in new TestContent {
      val response: WSResponse =
        wsClient
          .url(resource("/secure-messaging/conversation/CDCM/D-80542-20201120"))
          .withHttpHeaders((HeaderNames.CONTENT_TYPE, ContentTypes.JSON))
          .put(new File("./it/resources/cdcm/create-conversation.json"))
          .futureValue
      response.status mustBe CREATED
    }

    "return CREATED when sent a minimal and valid JSON payload" in new TestContent {
      val response: WSResponse =
        wsClient
          .url(resource("/secure-messaging/conversation/CDCM/D-80542-20201120"))
          .withHttpHeaders((HeaderNames.CONTENT_TYPE, ContentTypes.JSON))
          .put(new File("./it/resources/cdcm/create-conversation-minimal.json"))
          .futureValue
      response.status mustBe CREATED
    }

    "return BAD REQUEST when sending lowercase cdcm in URL" in new TestContent {
      val response: WSResponse =
        wsClient
          .url(resource("/secure-messaging/conversation/cdcm/D-80542-20201120"))
          .withHttpHeaders((HeaderNames.CONTENT_TYPE, ContentTypes.JSON))
          .put(new File("./it/resources/cdcm/create-conversation.json"))
          .futureValue
      response.status mustBe BAD_REQUEST
      response.body must startWith("Unknown value supplied for uk.gov.hmrc.securemessage.controllers.model.ClientName")
      response.body must include("cdcm")
    }

    "return BAD REQUEST when sent a minimal and invalid JSON payload" in new TestContent {
      val response: WSResponse =
        wsClient
          .url(resource("/secure-messaging/conversation/CDCM/D-80542-20201120"))
          .withHttpHeaders((HeaderNames.CONTENT_TYPE, ContentTypes.JSON))
          .put(Json.parse("""{"missing":"data"}""".stripMargin))
          .futureValue
      response.status mustBe BAD_REQUEST
    }

    "return CONFLICT when a conversation with the given conversationId already exists" in new TestContent {
      val _ = wsClient
        .url(resource("/secure-messaging/conversation/CDCM/D-80542-20201120"))
        .withHttpHeaders((HeaderNames.CONTENT_TYPE, ContentTypes.JSON))
        .put(new File("./it/resources/cdcm/create-conversation-minimal.json"))
        .futureValue
      val response: WSResponse = wsClient
        .url(resource("/secure-messaging/conversation/CDCM/D-80542-20201120"))
        .withHttpHeaders((HeaderNames.CONTENT_TYPE, ContentTypes.JSON))
        .put(new File("./it/resources/cdcm/create-conversation-minimal.json"))
        .futureValue
      response.status mustBe CONFLICT
    }

    "return BAD_REQUEST when invalid message content is supplied" in new TestContent {
      val response: WSResponse =
        wsClient
          .url(resource("/secure-messaging/conversation/CDCM/D-80542-20201120"))
          .withHttpHeaders((HeaderNames.CONTENT_TYPE, ContentTypes.JSON))
          .put(new File("./it/resources/cdcm/conversation-request-invalid-html.json"))
          .futureValue
      response.status mustBe BAD_REQUEST
      response.body mustBe
        "\"Error on message with client: Some(CDCM), message id: D-80542-20201120, " +
          "error message: Html contains disallowed tags, " +
          "attributes or protocols within the tags: matt. For allowed elements see class org.jsoup.safety.Whitelist.relaxed()\""
    }
  }

  class TestContent {
    val wsClient = app.injector.instanceOf[WSClient]
  }
}
