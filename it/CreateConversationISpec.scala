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
import utils.ConversationUtil

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

    "return CREATED when sent a full and valid JSON payload" in {
      val wsClient = app.injector.instanceOf[WSClient]
      val response =
        wsClient
          .url(resource("/secure-messaging/conversation/cdcm/D-80542-20201120"))
          .withHttpHeaders((HeaderNames.CONTENT_TYPE, ContentTypes.JSON))
          .put(new File("./it/resources/create-conversation-full.json"))
          .futureValue
      response.status mustBe CREATED
      response.body mustBe ""
    }

    "return CREATED when sent a minimal and valid JSON payload" in {
      val wsClient = app.injector.instanceOf[WSClient]
      val response =
        wsClient
          .url(resource("/secure-messaging/conversation/cdcm/D-80542-20201120"))
          .withHttpHeaders((HeaderNames.CONTENT_TYPE, ContentTypes.JSON))
          .put(new File("./it/resources/create-conversation-minimal.json"))
          .futureValue
      response.status mustBe CREATED
      response.body mustBe ""
    }

    "return BAD REQUEST when sent a conversation request with an no email address" in {
      val wsClient = app.injector.instanceOf[WSClient]
      val response =
        wsClient
          .url(resource("/secure-messaging/conversation/cdcm/D-80542-20201120"))
          .withHttpHeaders((HeaderNames.CONTENT_TYPE, ContentTypes.JSON))
          .put(new File("./it/resources/create-conversation-no-email.json"))
          .futureValue
      response.status mustBe BAD_REQUEST
      response.body mustBe "No recipient email addresses provided"
    }

    "return BAD REQUEST when sent a conversation request with an invalid email address" in {
      val wsClient = app.injector.instanceOf[WSClient]
      val response =
        wsClient
          .url(resource("/secure-messaging/conversation/cdcm/D-80542-20201120"))
          .withHttpHeaders((HeaderNames.CONTENT_TYPE, ContentTypes.JSON))
          .put(new File("./it/resources/create-conversation-invalid-email.json"))
          .futureValue
      response.status mustBe BAD_REQUEST
    }

    "return BAD REQUEST when sent a minimal and invalid JSON payload" in {
      val wsClient = app.injector.instanceOf[WSClient]
      val response =
        wsClient
          .url(resource("/secure-messaging/conversation/cdcm/D-80542-20201120"))
          .withHttpHeaders((HeaderNames.CONTENT_TYPE, ContentTypes.JSON))
          .put(Json.parse("""{"missing":"data"}""".stripMargin))
          .futureValue
      response.status mustBe BAD_REQUEST
    }

    "return CONFLICT when a conversation with the given conversationId already exists" in {
      val wsClient = app.injector.instanceOf[WSClient]
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
      response.body mustBe "Duplicate of existing conversation"
    }

    "return BAD_REQUEST when the message content is not base64 encoded" in {
      val wsClient = app.injector.instanceOf[WSClient]
      val response =
        wsClient
          .url(resource("/secure-messaging/conversation/cdcm/D-80542-20201120"))
          .withHttpHeaders((HeaderNames.CONTENT_TYPE, ContentTypes.JSON))
          .put(ConversationUtil.getConversationRequest("aGV%sb-G8sIHdvcmxkIQ=="))
          .futureValue
      response.status mustBe BAD_REQUEST
      response.body mustBe "Not valid base64 content"
    }

    "return BAD_REQUEST when the message content is not valid HTML" in {
      val wsClient = app.injector.instanceOf[WSClient]
      val response =
        wsClient
          .url(resource("/secure-messaging/conversation/cdcm/D-80542-20201120"))
          .withHttpHeaders((HeaderNames.CONTENT_TYPE, ContentTypes.JSON))
          .put(ConversationUtil.getConversationRequest("PG1hdHQ+Q2FuIEkgaGF2ZSBteSB0YXggbW9uZXkgcGxlYXNlPzwvbWF0dD4="))
          .futureValue
      response.status mustBe BAD_REQUEST
      response.body mustBe "Not valid HTML content"
    }

    "return BAD REQUEST if message content is empty" in {
      val wsClient = app.injector.instanceOf[WSClient]
      val response =
        wsClient
          .url(resource("/secure-messaging/conversation/cdcm/D-80542-20201120"))
          .withHttpHeaders((HeaderNames.CONTENT_TYPE, ContentTypes.JSON))
          .put(ConversationUtil.getConversationRequest(""))
          .futureValue
      response.status mustBe BAD_REQUEST
      response.body mustBe "Not valid HTML content"
    }
  }
}
