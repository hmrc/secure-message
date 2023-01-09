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

package uk.gov.hmrc.securemessage.connectors

import org.mockito.Mockito.when
import org.mockito.ArgumentMatchers._
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.Configuration
import uk.gov.hmrc.http.{ HeaderCarrier, HttpClient, HttpResponse }
import play.api.http.Status.{ BAD_REQUEST, OK }
import uk.gov.hmrc.emailaddress.EmailAddress
import uk.gov.hmrc.securemessage.EmailLookupError
import uk.gov.hmrc.securemessage.models.core.Identifier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, Future }

class ChannelPreferencesConnectorSpec extends PlaySpec with ScalaFutures with MockitoSugar {
  implicit val hc: HeaderCarrier = HeaderCarrier()

  private val validEmailVerification = """{"address":"some@email.com","timestamp":"1987-03-20T01:02:03.000Z"}"""
  private val inValidEmailVerification = """{"add":"some@email.com","timestamp":"1987-03-20T01:02:03.000Z"}"""
  private val inValidJson = """{"add:"some@email.com","timestamp":"1987-03-20T01:02:03.000Z"}"""
  val cpURL =
    "https://host:443/channel-preferences/preference/email?enrolmentKey=HMRC-CUS-ORG&taxIdName=EORINumber&taxIdValue=eoriid"

  "getEmailForEnrolment" should {
    "return the email address if found by CDS" in new TestCase {
      private val connector = new ChannelPreferencesConnector(configuration, mockHttpClient)

      when(mockHttpResponse.body).thenReturn(validEmailVerification)
      Await.result(
        connector.getEmailForEnrolment(Identifier("EORINumber", "eoriid", Some("HMRC-CUS-ORG"))),
        Duration.Inf) mustBe Right(EmailAddress("some@email.com"))
    }
    "return FAILED_DEPENDENCY if the json does not have a valid email address" in new TestCase {
      private val connector = new ChannelPreferencesConnector(configuration, mockHttpClient)

      when(mockHttpResponse.body).thenReturn(inValidEmailVerification)
      connector.getEmailForEnrolment(Identifier("EORINumber", "eoriid", Some("HMRC-CUS-ORG"))).futureValue mustBe
        Left(EmailLookupError(s"""could not find an email address in the response: $inValidEmailVerification"""))
    }
    "return FAILED_DEPENDENCY if the json is invalid" in new TestCase {
      private val connector = new ChannelPreferencesConnector(configuration, mockHttpClient)

      when(mockHttpResponse.body).thenReturn(inValidJson)
      val res = connector
        .getEmailForEnrolment(Identifier("EORINumber", "eoriid", Some("HMRC-CUS-ORG")))
        .futureValue
        .swap
        .toOption
        .fold(EmailLookupError(""))(identity)
      res.message must startWith(s"""channel-preferences response was an invalid json: $inValidJson""")
    }
    "forward the status from CDS if not OK" in new TestCase {
      private val connector = new ChannelPreferencesConnector(configuration, mockHttpClient)

      when(mockHttpResponse.status).thenReturn(BAD_REQUEST)
      when(mockHttpResponse.body).thenReturn("")
      val res = connector
        .getEmailForEnrolment(Identifier("EORINumber", "eoriid", Some("HMRC-CUS-ORG")))
        .futureValue
        .swap
        .toOption
        .fold(EmailLookupError(""))(identity)
      res.message mustBe s"""channel-preferences returned status: $BAD_REQUEST body: """
    }

  }

  trait TestCase {
    val mockHttpClient: HttpClient = mock[HttpClient]
    val mockHttpResponse: HttpResponse = mock[HttpResponse]
    when(
      mockHttpClient
        .GET[HttpResponse](any[String], any[Seq[(String, String)]], any[Seq[(String, String)]])(
          any[uk.gov.hmrc.http.HttpReads[uk.gov.hmrc.http.HttpResponse]],
          any[uk.gov.hmrc.http.HeaderCarrier],
          any[scala.concurrent.ExecutionContext]))
      .thenReturn(Future.successful(mockHttpResponse))
    when(mockHttpResponse.status).thenReturn(OK)
  }

  val configuration: Configuration = Configuration(
    "microservice.services.channel-preferences.host"     -> "host",
    "microservice.services.channel-preferences.port"     -> 443,
    "microservice.services.channel-preferences.protocol" -> "https"
  )

}
