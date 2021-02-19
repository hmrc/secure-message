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
import play.api.libs.ws.WSClient
import play.api.test.Helpers._
import uk.gov.hmrc.integration.ServiceSpec
import uk.gov.hmrc.securemessage.repository.ConversationRepository
import utils.AuthHelper

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
