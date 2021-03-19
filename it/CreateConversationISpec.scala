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

import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import play.api.http.Status.{ BAD_REQUEST, CREATED }
import play.api.http.{ ContentTypes, HeaderNames }
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.test.Helpers._
import uk.gov.hmrc.integration.ServiceSpec
import uk.gov.hmrc.securemessage.repository.ConversationRepository

import scala.concurrent.ExecutionContext

@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
class CreateConversationISpec extends PlaySpec with ServiceSpec with BeforeAndAfterEach {

  override def externalServices: Seq[String] = Seq.empty

  val repository = app.injector.instanceOf[ConversationRepository]
  val ec = app.injector.instanceOf[ExecutionContext]

  override protected def beforeEach(): Unit = {
    val _ = await(repository.removeAll()(ec))
  }

  "A PUT request to /secure-messaging/conversation/{client}/{conversationId}" should {

    "return CREATED when sent a full and valid JSON payload" in new TestContent {
      val response =
        wsClient
          .url(resource("/secure-messaging/conversation/cdcm/D-80542-20201120"))
          .withHttpHeaders((HeaderNames.CONTENT_TYPE, ContentTypes.JSON))
          .put(new File("./it/resources/create-conversation-full.json"))
          .futureValue
      response.status mustBe CREATED
    }

    "return CREATED when sent a minimal and valid JSON payload" in new TestContent {
      val response =
        wsClient
          .url(resource("/secure-messaging/conversation/cdcm/D-80542-20201120"))
          .withHttpHeaders((HeaderNames.CONTENT_TYPE, ContentTypes.JSON))
          .put(new File("./it/resources/create-conversation-minimal.json"))
          .futureValue
      response.status mustBe CREATED
    }

    "return CREATED when sent a conversation request with no email address and it is found in CDS" in new TestContent {
      val response =
        wsClient
          .url(resource("/secure-messaging/conversation/cdcm/D-80542-20201120"))
          .withHttpHeaders((HeaderNames.CONTENT_TYPE, ContentTypes.JSON))
          .put(new File("./it/resources/create-conversation-no-email.json"))
          .futureValue
      response.status mustBe CREATED
    }

    "return BAD REQUEST when sent a conversation request with an invalid email address" in new TestContent {
      val response =
        wsClient
          .url(resource("/secure-messaging/conversation/cdcm/D-80542-20201120"))
          .withHttpHeaders((HeaderNames.CONTENT_TYPE, ContentTypes.JSON))
          .put(new File("./it/resources/create-conversation-invalid-email.json"))
          .futureValue
      response.status mustBe BAD_REQUEST
    }

    "return BAD REQUEST when sent a minimal and invalid JSON payload" in new TestContent {
      val response =
        wsClient
          .url(resource("/secure-messaging/conversation/cdcm/D-80542-20201120"))
          .withHttpHeaders((HeaderNames.CONTENT_TYPE, ContentTypes.JSON))
          .put(Json.parse("""{"missing":"data"}""".stripMargin))
          .futureValue
      response.status mustBe BAD_REQUEST
    }

    "return CONFLICT when a conversation with the given conversationId already exists" in new TestContent {
      val _ = wsClient
        .url(resource("/secure-messaging/conversation/cdcm/D-80542-20201120"))
        .withHttpHeaders((HeaderNames.CONTENT_TYPE, ContentTypes.JSON))
        .put(new File("./it/resources/create-conversation-minimal.json"))
        .futureValue
      val response = wsClient
        .url(resource("/secure-messaging/conversation/cdcm/D-80542-20201120"))
        .withHttpHeaders((HeaderNames.CONTENT_TYPE, ContentTypes.JSON))
        .put(new File("./it/resources/create-conversation-minimal.json"))
        .futureValue
      response.status mustBe CONFLICT
    }

    "return BAD_REQUEST when invalid message content is supplied" in new TestContent {
      val response =
        wsClient
          .url(resource("/secure-messaging/conversation/cdcm/D-80542-20201120"))
          .withHttpHeaders((HeaderNames.CONTENT_TYPE, ContentTypes.JSON))
          .put(new File("./it/resources/conversation-request-invalid-html.json"))
          .futureValue
      response.status mustBe BAD_REQUEST
      response.body mustBe "\"Error on conversation with client: cdcm, conversationId: D-80542-20201120, error message: Html contains disallowed tags, attributes or protocols within the tags: matt. For allowed elements see class org.jsoup.safety.Whitelist.relaxed()\""
    }
  }

  class TestContent {
    val wsClient = app.injector.instanceOf[WSClient]
  }
}
