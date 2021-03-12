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
import play.api.http.{ ContentTypes, HeaderNames }
import play.api.libs.json.{ Json, Reads }
import play.api.libs.ws.{ WSClient, WSResponse }
import play.api.test.Helpers._
import uk.gov.hmrc.integration.ServiceSpec
import uk.gov.hmrc.securemessage.repository.ConversationRepository

import scala.concurrent.{ ExecutionContext, Future }

@SuppressWarnings(Array("org.wartremover.warts.All"))
class GetIndividualConversationISpec extends PlaySpec with ServiceSpec with BeforeAndAfterEach {

  override def externalServices: Seq[String] = Seq("auth-login-api")

  val wsClient = app.injector.instanceOf[WSClient]
  val repository = app.injector.instanceOf[ConversationRepository]
  val ec = app.injector.instanceOf[ExecutionContext]

  override protected def beforeEach(): Unit = {
    val _ = await(repository.removeAll()(ec))
  }

  "A GET request to /secure-messaging/conversation/:client/:conversationId" should {

    "return a JSON body of api conversation with a list of api messages" in {
      createConversation
      val response =
        wsClient
          .url(resource("/secure-messaging/conversation/cdcm/D-80542-20201120"))
          .withHttpHeaders(AuthUtil.buildEoriToken)
          .get()
          .futureValue
      response.body must include("""{"senderInformation":{"name":"CDS Exports Team"""")
    }

    "return a JSON body of [No conversation found] when a conversationId does not match" in {
      createConversation
      val response =
        wsClient
          .url(resource("/secure-messaging/conversation/cdcm/D-80542-77777777"))
          .withHttpHeaders(AuthUtil.buildEoriToken)
          .get()
          .futureValue
      response.body mustBe "\"No conversation found\""
    }

    "return a JSON body of [No conversation found] when auth session enrolments do not match a conversation's participants identifiers" in {
      val response =
        wsClient
          .url(resource("/secure-messaging/conversation/cdcm/D-80542-20201120"))
          .withHttpHeaders(AuthUtil.buildNonEoriToken)
          .get()
          .futureValue
      response.body mustBe "\"No conversation found\""
    }
  }

  object AuthUtil {
    lazy val ggAuthPort: Int = externalServicePorts("auth-login-api")

    implicit val deserialiser: Reads[GatewayToken] = Json.reads[GatewayToken]

    case class GatewayToken(gatewayToken: String)

    private val NO_EORI_USER_PAYLOAD =
      """
        | {
        |  "credId": "1235",
        |  "affinityGroup": "Organisation",
        |  "confidenceLevel": 100,
        |  "credentialStrength": "none",
        |  "enrolments": []
        |  }
     """.stripMargin

    private val EORI_USER_PAYLOAD =
      """
        | {
        |  "credId": "1235",
        |  "affinityGroup": "Organisation",
        |  "confidenceLevel": 200,
        |  "credentialStrength": "none",
        |  "enrolments": [
        |      {
        |        "key": "HMRC-CUS-ORG",
        |        "identifiers": [
        |          {
        |            "key": "EORINumber",
        |            "value": "GB1234567890"
        |          }
        |        ],
        |        "state": "Activated"
        |      }
        |    ]
        |  }
     """.stripMargin

    private def buildUserToken(payload: String): (String, String) = {
      val response = wsClient
        .url(s"http://localhost:$ggAuthPort/government-gateway/session/login")
        .withHttpHeaders(("Content-Type", "application/json"))
        .post(payload)
        .futureValue

      ("Authorization", response.header("Authorization").get)
    }

    def buildEoriToken: (String, String) = buildUserToken(EORI_USER_PAYLOAD)
    def buildNonEoriToken: (String, String) = buildUserToken(NO_EORI_USER_PAYLOAD)
  }

  def createConversation: Future[WSResponse] = {
    val wsClient = app.injector.instanceOf[WSClient]
    wsClient
      .url(resource("/secure-messaging/conversation/cdcm/D-80542-20201120"))
      .withHttpHeaders((HeaderNames.CONTENT_TYPE, ContentTypes.JSON))
      .put(new File("./it/resources/create-conversation-full.json"))
  }
}
