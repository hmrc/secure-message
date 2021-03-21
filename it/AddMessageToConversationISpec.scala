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

import java.io.File

import org.scalatest.DoNotDiscover
import play.api.http.Status.CREATED
import play.api.http.{ ContentTypes, HeaderNames }
import play.api.libs.json.Json
import play.api.test.Helpers._

@DoNotDiscover
@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
class AddMessageToConversationISpec extends ISpec {

  "A PUT request to /secure-messaging/conversation/{client}/{conversationId}/customer-message" must {
    "return CREATED when the message is successfully added to the conversation" in {
      val client = "cdcm"
      val conversationId = "D-80542-20201120"
      await(
        wsClient
          .url(resource(s"/secure-messaging/conversation/$client/$conversationId"))
          .withHttpHeaders((HeaderNames.CONTENT_TYPE, ContentTypes.JSON))
          .put(new File("./it/resources/create-conversation-minimal.json")))
      val response =
        wsClient
          .url(resource(s"/secure-messaging/conversation/$client/$conversationId/customer-message"))
          .withHttpHeaders(buildEoriToken(VALID_EORI))
          .post(Json.obj("content" -> "PGRpdj5IZWxsbzwvZGl2Pg=="))
          .futureValue
      response.status mustBe CREATED
    }
    "return NOT FOUND when the conversation ID is not recognised" in {
      val client = "cdcm"
      val conversationId = "D-80542-20201120"
      val response =
        wsClient
          .url(resource(s"/secure-messaging/conversation/$client/$conversationId/customer-message"))
          .withHttpHeaders(buildEoriToken(VALID_EORI))
          .post(Json.obj("content" -> "PGRpdj5IZWxsbzwvZGl2Pg=="))
          .futureValue
      response.status mustBe NOT_FOUND
    }
    "return UNAUTHORIZED when the customer is not a participant" in {
      val client = "cdcm"
      val conversationId = "D-80542-20201120"
      await(
        wsClient
          .url(resource(s"/secure-messaging/conversation/$client/$conversationId"))
          .withHttpHeaders((HeaderNames.CONTENT_TYPE, ContentTypes.JSON))
          .put(new File("./it/resources/create-conversation-minimal.json")))
      val response =
        wsClient
          .url(resource(s"/secure-messaging/conversation/$client/$conversationId/customer-message"))
          .withHttpHeaders(buildEoriToken("GB1234567891"))
          .post(Json.obj("content" -> "PGRpdj5IZWxsbzwvZGl2Pg=="))
          .futureValue
      response.status mustBe UNAUTHORIZED
    }
  }
}
