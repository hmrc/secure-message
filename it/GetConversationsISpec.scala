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

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{ Json, Reads }
import play.api.libs.ws.WSClient
import uk.gov.hmrc.integration.ServiceSpec

class GetConversationsISpec extends PlaySpec with ServiceSpec {

  override def externalServices: Seq[String] = Seq("auth-login-api")

  val wsClient = app.injector.instanceOf[WSClient]

  "A GET request to /secure-messaging/conversations" should {

    "return a JSON body of conversation details" in {
      val response =
        wsClient
          .url(resource("/secure-messaging/conversations"))
          .withHttpHeaders(AuthUtil.buildEoriToken)
          .get()
          .futureValue
      response.body mustBe
        """[{"conversationId":"D-80542-20201120","subject":"D-80542-20201120","issueDate":"2020-11-10T15:00:01.000+0000","senderName":"CDS Exports Team","unreadMessages":true,"count":1}]"""
    }

    "return a JSON body of [No EORI enrolment found] when there's an auth session, but no EORI enrolment" in {
      val response =
        wsClient
          .url(resource("/secure-messaging/conversations"))
          .withHttpHeaders(AuthUtil.buildNonEoriToken)
          .get()
          .futureValue
      response.body mustBe "\"No EORI enrolment found\""
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

}
