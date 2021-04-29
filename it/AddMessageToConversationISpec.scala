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

import org.scalatest.DoNotDiscover
import play.api.http.Status.CREATED
import play.api.http.{ ContentTypes, HeaderNames }
import play.api.libs.json.Json
import play.api.libs.ws.WSResponse
import play.api.test.Helpers._

import java.io.File

@DoNotDiscover
@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
class AddMessageToConversationISpec extends ISpec {

  "A POST request to /secure-messaging/conversation/{client}/{conversationId}/customer-message" must {
    "return CREATED when the message is successfully added to the conversation" in new CustomerTestCase(VALID_EORI) {

      response.status mustBe CREATED
      response.body mustBe "\"Created customer message for client CDCM and conversationId D-80542-20201120\""
    }
    "return NOT FOUND when the conversation ID is not recognised" in {
      val client = "CDCM"
      val conversationId = "D-80542-20201120"
      val response =
        wsClient
          .url(resource(s"/secure-messaging/conversation/$client/$conversationId/customer-message"))
          .withHttpHeaders(buildEoriToken(VALID_EORI))
          .post(Json.obj("content" -> "PGRpdj5IZWxsbzwvZGl2Pg=="))
          .futureValue
      response.status mustBe NOT_FOUND
      response.body mustBe "\"Error on conversation with client: Some(CDCM), conversationId: D-80542-20201120, error message: Conversation not found for identifier: Set(Identifier(EORINumber,GB1234567890,Some(HMRC-CUS-ORG)))\""
    }

    "return NOT_FOUND when the customer is not a participant" in new CustomerTestCase("GB1234567891") {
      response.status mustBe NOT_FOUND
      response.body mustBe "\"Error on conversation with client: Some(CDCM), conversationId: D-80542-20201120, error message: Conversation not found for identifier: Set(Identifier(EORINumber,GB1234567891,Some(HMRC-CUS-ORG)))\""
    }
  }

  "A POST request to /secure-messaging/conversation/{client}/{conversationId}/caseworker-message" must {
    "return CREATED when the message is successfully added to the conversation" in new CaseworkerTestCase(
      "./it/resources/cdcm/caseworker-message.json") {

      response.status mustBe CREATED
    }
    "return NOT FOUND when the conversation ID is not recognised" in {
      val client = "CDCM"
      val conversationId = "D-80542-20201120"
      val response =
        wsClient
          .url(resource(s"/secure-messaging/conversation/$client/$conversationId/caseworker-message"))
          .withHttpHeaders((HeaderNames.CONTENT_TYPE, ContentTypes.JSON))
          .post(new File("./it/resources/cdcm/caseworker-message.json"))
          .futureValue
      response.status mustBe NOT_FOUND
      response.body mustBe "\"Error on conversation with client: Some(CDCM), conversationId: D-80542-20201120, error message: Conversation not found for identifier: Set(Identifier(CDCM,D-80542-20201120,None))\""
    }
    "return BAD_REQUEST when invalid message content is supplied" in new CaseworkerTestCase(
      "./it/resources/cdcm/caseworker-message-invalid-html.json") {
      response.status mustBe BAD_REQUEST
      response.body mustBe "\"Error on conversation with client: Some(CDCM), conversationId: D-80542-20201120, error message: Html contains disallowed tags, attributes or protocols within the tags: matt. For allowed elements see class org.jsoup.safety.Whitelist.relaxed()\""
    }
  }

  class CaseworkerTestCase(file: String) {
    val client = "CDCM"
    val conversationId = "D-80542-20201120"
    await(
      wsClient
        .url(resource(s"/secure-messaging/conversation/$client/$conversationId"))
        .withHttpHeaders((HeaderNames.CONTENT_TYPE, ContentTypes.JSON))
        .put(new File("./it/resources/cdcm/create-conversation-minimal.json")))
    val response: WSResponse =
      wsClient
        .url(resource(s"/secure-messaging/conversation/$client/$conversationId/caseworker-message"))
        .withHttpHeaders((HeaderNames.CONTENT_TYPE, ContentTypes.JSON))
        .post(new File(file))
        .futureValue
  }

  class CustomerTestCase(eori: String) {
    val client = "CDCM"
    val conversationId = "D-80542-20201120"
    await(
      wsClient
        .url(resource(s"/secure-messaging/conversation/$client/$conversationId"))
        .withHttpHeaders((HeaderNames.CONTENT_TYPE, ContentTypes.JSON))
        .put(new File("./it/resources/cdcm/create-conversation-minimal.json")))
    val response: WSResponse =
      wsClient
        .url(resource(s"/secure-messaging/conversation/$client/$conversationId/customer-message"))
        .withHttpHeaders(buildEoriToken(eori))
        .post(Json.obj("content" -> "PGRpdj5IZWxsbzwvZGl2Pg=="))
        .futureValue
  }
}
