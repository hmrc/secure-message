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

package uk.gov.hmrc.securemessage.connectors

import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.Configuration
import uk.gov.hmrc.http.{ HeaderCarrier, HttpClient, HttpResponse }
import play.api.http.Status.{ BAD_GATEWAY, NOT_FOUND, OK }
import uk.gov.hmrc.emailaddress.EmailAddress
import uk.gov.hmrc.securemessage.models.core.Identifier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, Future }

@SuppressWarnings(Array("org.wartremover.warts.All"))
class ChannelPreferencesConnectorSpec extends PlaySpec with ScalaFutures with MockitoSugar {
  implicit val hc: HeaderCarrier = HeaderCarrier()
  private val validEmailVerification = """{"address":"some@email.com","timestamp":"1987-03-20T01:02:03.000Z"}"""

  "getEmailForEnrolment" should {
    "return the email address if found by CDS" in new TestCase {
      private val connector = new ChannelPreferencesConnector(configuration, mockHttpClient)
      when(
        mockHttpClient
          .doGet("https://host:443/channel-preferences/preference/email/HMRC-CUS-ORG/EORINumber/eoriid")(hc, global))
        .thenReturn(Future.successful(mockHttpResponse))
      when(mockHttpResponse.status).thenReturn(OK)
      when(mockHttpResponse.body).thenReturn(validEmailVerification)
      Await.result(
        connector.getEmailForEnrolment(Identifier("EORINumber", "eoriid", Some("HMRC-CUS-ORG"))),
        Duration.Inf) mustBe Right(EmailAddress("some@email.com"))
    }
  }

  trait TestCase {
    val mockHttpClient: HttpClient = mock[HttpClient]
    val mockHttpResponse: HttpResponse = mock[HttpResponse]
  }

  val configuration: Configuration = Configuration(
    "microservice.services.channel-preferences.host"     -> "host",
    "microservice.services.channel-preferences.port"     -> 443,
    "microservice.services.channel-preferences.protocol" -> "https"
  )
}
