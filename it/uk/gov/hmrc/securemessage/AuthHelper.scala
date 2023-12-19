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

import org.scalatest.concurrent.ScalaFutures
import play.api.libs.json.{ Json, Reads }
import play.api.libs.ws.WSClient

trait AuthHelper extends ScalaFutures {

  val ggAuthPort: Int

  val wsClient: WSClient

  val VALID_EORI: String = "GB1234567890"

  implicit val deserialiser: Reads[GatewayToken] = Json.reads[GatewayToken]

  case class GatewayToken(gatewayToken: String)

  private val NO_EORI_USER_PAYLOAD =
    """
      | {
      |  "credId": "secure-message-001",
      |  "affinityGroup": "Organisation",
      |  "confidenceLevel": 200,
      |  "credentialStrength": "none",
      |  "enrolments": []
      |  }
     """.stripMargin

  private def getEoriUserPayload(eori: String) =
    s"""
       | {
       |  "credId": "secure-message-002",
       |  "affinityGroup": "Organisation",
       |  "confidenceLevel": 200,
       |  "credentialStrength": "strong",
       |  "enrolments": [
       |      {
       |        "key": "HMRC-CUS-ORG",
       |        "identifiers": [
       |          {
       |            "key": "EORINumber",
       |            "value": "$eori"
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

    ("Authorization", response.header("Authorization").getOrElse(""))
  }

  def buildEoriToken(eori: String): (String, String) = buildUserToken(getEoriUserPayload(eori))
  def buildNonEoriToken: (String, String) = buildUserToken(NO_EORI_USER_PAYLOAD)
}
