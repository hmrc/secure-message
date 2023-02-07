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

package uk.gov.hmrc.securemessage

import org.scalatest.DoNotDiscover
import play.api.http.Status.CREATED
import play.api.http.{ ContentTypes, HeaderNames }
import play.api.libs.json.Json
import play.api.libs.ws.WSResponse
import play.api.test.Helpers._
import uk.gov.hmrc.securemessage.controllers.model.common.read.MessageMetadata

import java.io.File

@DoNotDiscover
class AddMessageToConversationISpec extends ISpec {

  "A POST request to /secure-messaging/conversation/{client}/{conversationId}/customer-message" must {
    "return CREATED when the message is successfully added to the conversation" in new CustomerTestCase(
      VALID_EORI,
      "D-80542-20201110") {

      response.body mustBe s""""Created customer message for encodedId: $messageId""""
      response.status mustBe CREATED
    }
    "return NOT FOUND when the conversation ID is not recognised" in new CustomerTestCase(
      VALID_EORI,
      "D-80542-20201121") {
      val actualRresponse =
        wsClient
          .url(resource(s"/secure-messaging/messages/$nonExistingEncodedId/customer-message"))
          .withHttpHeaders(buildEoriToken(VALID_EORI))
          .post(Json.obj("content" -> "PGRpdj5IZWxsbzwvZGl2Pg=="))
          .futureValue
      actualRresponse.status mustBe NOT_FOUND
      actualRresponse.body mustBe s""""Error on message with client: None, message id: $nonExistingEncodedId, error message: Conversation not found for identifiers: Set(Identifier(EORINumber,GB1234567890,Some(HMRC-CUS-ORG)))""""
    }

    "return NOT_FOUND when the customer is not a participant" in new CustomerTestCase(
      "GB1234567891",
      "D-80542-20201122") {
      response.status mustBe NOT_FOUND
      response.body mustBe s""""Error on message with client: None, message id: $nonExistingEncodedId, error message: Conversation not found for identifiers: Set(Identifier(EORINumber,GB1234567891,Some(HMRC-CUS-ORG)))""""
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
      response.body mustBe "\"Error on message with client: Some(CDCM), message id: D-80542-20201120, error message: Conversation not found for identifiers: Set(Identifier(CDCM,D-80542-20201120,None))\""
    }
    "return BAD_REQUEST when invalid message content is supplied" in new CaseworkerTestCase(
      "./it/resources/cdcm/caseworker-message-invalid-html.json") {
      response.status mustBe BAD_REQUEST
      response.body mustBe "\"Error on message with client: Some(CDCM), message id: D-80542-20201120, error message: Html contains disallowed tags, attributes or protocols within the tags: matt. For allowed elements see class org.jsoup.safety.Safelist.relaxed()\""
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

  class CustomerTestCase(eori: String, conversationId: String) {
    val client = "CDCM"
    val nonExistingEncodedId = "Y29udmVyc2F0aW9uLzYwYTcxMzFlMTUwMDAwNmE1YjYyZWVlZg=="
    await(
      wsClient
        .url(resource(s"/secure-messaging/conversation/$client/$conversationId"))
        .withHttpHeaders((HeaderNames.CONTENT_TYPE, ContentTypes.JSON))
        .put(new File("./it/resources/cdcm/create-conversation-minimal.json")))
    val messageId: String =
      wsClient
        .url(resource("/secure-messaging/messages"))
        .withHttpHeaders(buildEoriToken(eori))
        .get()
        .futureValue
        .json
        .as[Seq[MessageMetadata]]
        .headOption
        .map(_.id)
        .getOrElse(nonExistingEncodedId)
    val response: WSResponse =
      wsClient
        .url(resource(s"/secure-messaging/messages/$messageId/customer-message"))
        .withHttpHeaders(buildEoriToken(eori))
        .post(Json.obj("content" -> "PGRpdj5IZWxsbzwvZGl2Pg=="))
        .futureValue
  }
}
