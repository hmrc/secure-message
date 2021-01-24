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

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{ Json, Reads }
import play.api.libs.ws.WSClient
import play.api.libs.ws.ahc.{ AhcWSClient, AhcWSClientConfig, StandaloneAhcWSClient }
import uk.gov.hmrc.integration.ServiceSpec

class GetConversationsISpec extends PlaySpec with ServiceSpec {

  override def externalServices: Seq[String] = Seq("datastream", "auth-login-api")
  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = akka.stream.ActorMaterializer()
  implicit val httpClient: WSClient = new AhcWSClient(StandaloneAhcWSClient(AhcWSClientConfig()))

  override def additionalConfig: Map[String, _] =
    Map("auditing.consumer.baseUri.port" -> externalServicePorts("datastream"))

  object AuthUtil {
    lazy val authPort = 8500
    lazy val ggAuthPort: Int = externalServicePorts("auth-login-api")

    implicit val deserialiser: Reads[GatewayToken] = Json.reads[GatewayToken]

    case class GatewayToken(gatewayToken: String)

    private val GG_SA_USER_PAYLOAD =
      """
        | {
        |  "credId": "1235",
        |  "affinityGroup": "Organisation",
        |  "confidenceLevel": 100,
        |  "credentialStrength": "none",
        |  "enrolments": [
        |      {
        |        "key": "IR-SA",
        |        "identifiers": [
        |          {
        |            "key": "UTR",
        |            "value": "1234567890"
        |          }
        |        ],
        |        "state": "Activated"
        |      }
        |    ]
        |  }
     """.stripMargin

    private def buildUserToken(payload: String): (String, String) = {
      val response = httpClient
        .url(s"http://localhost:$ggAuthPort/government-gateway/session/login")
        .withHttpHeaders(("Content-Type", "application/json"))
        .post(payload)
        .futureValue

      ("Authorization", response.header("Authorization").get)
    }

    def buildSaUserToken: (String, String) = buildUserToken(GG_SA_USER_PAYLOAD)
  }

}
