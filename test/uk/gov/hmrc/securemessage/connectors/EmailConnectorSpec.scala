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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.EitherValues
import uk.gov.hmrc.common.message.emailaddress.EmailAddress
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.Configuration
import uk.gov.hmrc.http.client.{ HttpClientV2, RequestBuilder }
import uk.gov.hmrc.http.{ HeaderCarrier, HttpReads, HttpResponse }
import uk.gov.hmrc.securemessage.models.EmailRequest

import java.net.URL
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ ExecutionContext, Future }

class EmailConnectorSpec extends PlaySpec with ScalaFutures with MockitoSugar with EitherValues {

  "send" must {
    val httpClient = mock[HttpClientV2]
    val auditConnector = mock[AuditConnector]
    lazy val requestBuilder = mock[RequestBuilder]

    val servicesConfig = new ServicesConfig(
      Configuration(
        "microservice.services.email.host"     -> "host",
        "microservice.services.email.port"     -> 443,
        "microservice.services.email.protocol" -> "https"
      )
    )

    when(httpClient.post(any[URL])(any[HeaderCarrier])).thenReturn(requestBuilder)
    when(requestBuilder.withBody(any)(any, any, any)).thenReturn(requestBuilder)

    "return unit when Accepted" in {
      when(requestBuilder.execute[HttpResponse](any[HttpReads[HttpResponse]], any[ExecutionContext]))
        .thenReturn(Future.successful(HttpResponse(202, "")))

      val emailConnector = new EmailConnector(httpClient, servicesConfig, auditConnector)

      val result =
        emailConnector.send(EmailRequest(List(EmailAddress("test@test.com")), "", Map.empty, None))(new HeaderCarrier())

      result.futureValue.toOption.get mustBe (())
    }

    "return error when not Accepted" in {
      when(requestBuilder.execute[HttpResponse](any[HttpReads[HttpResponse]], any[ExecutionContext]))
        .thenReturn(Future.successful(HttpResponse(404, "")))

      val emailConnector = new EmailConnector(httpClient, servicesConfig, auditConnector)

      val result =
        emailConnector.send(EmailRequest(List(EmailAddress("test@test.com")), "", Map.empty, None))(new HeaderCarrier())
      result.futureValue.left.value.message must include("Email request failed")
    }
  }
}
