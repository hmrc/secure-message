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

import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import play.api.http.Status.CREATED
import play.api.http.{ ContentTypes, HeaderNames }
import play.api.libs.json.Json
import play.api.libs.ws.{ WSClient, WSResponse }
import play.api.test.Helpers._
import uk.gov.hmrc.integration.ServiceSpec
import uk.gov.hmrc.securemessage.repository.ConversationRepository
import utils.{ AuthHelper, ConversationUtil }
import java.io.File

import scala.concurrent.ExecutionContext

@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
class AddMessageToConversationISpec extends PlaySpec with ServiceSpec with BeforeAndAfterEach with AuthHelper {

  override def externalServices: Seq[String] = Seq("auth-login-api")
  override val ggAuthPort: Int = externalServicePorts("auth-login-api")
  override val wsClient: WSClient = app.injector.instanceOf[WSClient]

  private val repository = app.injector.instanceOf[ConversationRepository]
  private val ec = app.injector.instanceOf[ExecutionContext]

  override protected def beforeEach(): Unit = {
    val _ = await(repository.removeAll()(ec))
  }

  "A POST request to /secure-messaging/conversation/{client}/{conversationId}/customer-message" must {
    "return CREATED when the message is successfully added to the conversation" in new CustomerTestCase(
      "PGRpdj5IZWxsbzwvZGl2Pg==",
      VALID_EORI) {
      response.status mustBe CREATED
      response.body mustBe "Created for client CDCM and conversationId D-80542-20201120"
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
      response.body mustBe "Conversation ID not known"
    }
    "return UNAUTHORIZED when the customer is not a participant" in new CustomerTestCase(
      "PGRpdj5IZWxsbzwvZGl2Pg==",
      "GB1234567891") {
      response.status mustBe UNAUTHORIZED
      response.body mustBe "Insufficient Enrolments"
    }
    "return BAD_REQUEST when the message content is not base64 encoded" in new CustomerTestCase(
      "aGV%sb-G8sIHdvcmxkIQ==",
      VALID_EORI) {
      response.status mustBe BAD_REQUEST
      response.body mustBe "Not valid base64 content"
    }
    "return BAD_REQUEST when the message content is not valid HTML" in new CustomerTestCase(
      "PG1hdHQ+Q2FuIEkgaGF2ZSBteSB0YXggbW9uZXkgcGxlYXNlPzwvbWF0dD4=",
      VALID_EORI) {
      response.status mustBe BAD_REQUEST
      response.body mustBe "Not valid HTML content"
    }
    "return BAD REQUEST if message content is empty" in new CustomerTestCase("", VALID_EORI) {
      response.status mustBe BAD_REQUEST
      response.body mustBe "Not valid HTML content"
    }
  }

  "A POST request to /secure-messaging/conversation/{client}/{conversationId}/caseworker-message" must {
    "return CREATED when the message is successfully added to the conversation" in new CaseworkerTestCase(
      "QmxhaCBibGFoIGJsYWg=") {
      response.status mustBe CREATED
      response.body mustBe "Created for client CDCM and conversationId D-80542-20201120"
    }
    "return NOT FOUND when the conversation ID is not recognised" in {
      val client = "cdcm"
      val conversationId = "D-80542-20201120"
      val response =
        wsClient
          .url(resource(s"/secure-messaging/conversation/$client/$conversationId/caseworker-message"))
          .withHttpHeaders((HeaderNames.CONTENT_TYPE, ContentTypes.JSON))
          .post(new File("./it/resources/caseworker-message.json"))
          .futureValue
      response.status mustBe NOT_FOUND
      response.body mustBe "Conversation ID not known"
    }
    "return BAD_REQUEST when the message content is not base64 encoded" in new CaseworkerTestCase(
      "aGV%sb-G8sIHdvcmxkIQ==") {
      response.status mustBe BAD_REQUEST
      response.body mustBe "Not valid base64 content"
    }
    "return BAD_REQUEST when the message content is not valid HTML" in new CaseworkerTestCase(
      "PG1hdHQ+Q2FuIEkgaGF2ZSBteSB0YXggbW9uZXkgcGxlYXNlPzwvbWF0dD4=") {
      response.status mustBe BAD_REQUEST
      response.body mustBe "Not valid HTML content"
    }
    "return BAD REQUEST if message content is empty" in new CaseworkerTestCase("") {
      response.status mustBe BAD_REQUEST
      response.body mustBe "Not valid HTML content"
    }
  }

  class CaseworkerTestCase(content: String) {
    val client = "CDCM"
    val conversationId = "D-80542-20201120"
    await(
      wsClient
        .url(resource(s"/secure-messaging/conversation/$client/$conversationId"))
        .withHttpHeaders((HeaderNames.CONTENT_TYPE, ContentTypes.JSON))
        .put(new File("./it/resources/create-conversation-minimal.json")))
    val response: WSResponse =
      wsClient
        .url(resource(s"/secure-messaging/conversation/$client/$conversationId/caseworker-message"))
        .withHttpHeaders((HeaderNames.CONTENT_TYPE, ContentTypes.JSON))
        .post(ConversationUtil.getCaseWorkerMessage(content))
        .futureValue
  }

  class CustomerTestCase(content: String, eori: String) {
    val client = "CDCM"
    val conversationId = "D-80542-20201120"
    await(
      wsClient
        .url(resource(s"/secure-messaging/conversation/$client/$conversationId"))
        .withHttpHeaders((HeaderNames.CONTENT_TYPE, ContentTypes.JSON))
        .put(new File("./it/resources/create-conversation-minimal.json")))
    val response: WSResponse =
      wsClient
        .url(resource(s"/secure-messaging/conversation/$client/$conversationId/customer-message"))
        .withHttpHeaders(buildEoriToken(eori))
        .post(Json.obj("content" -> content))
        .futureValue
  }
}
