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

import play.api.http.Status.BAD_REQUEST
import play.api.http.Status.NO_CONTENT
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.EitherValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.Configuration
import uk.gov.hmrc.http.client.{ HttpClientV2, RequestBuilder }
import uk.gov.hmrc.http.{ HeaderCarrier, HttpReads, HttpResponse }
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.securemessage.models.{ QueryMessageRequest, QueryMessageWrapper, RequestCommon, RequestDetail }

import java.net.URL
import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ ExecutionContext, Future }
import uk.gov.hmrc.http.HttpReads.Implicits.*

class EISConnectorSpec extends PlaySpec with ScalaFutures with MockitoSugar with EitherValues {

  "forwardMessage" must {
    val httpClient = mock[HttpClientV2]
    val auditConnector = mock[AuditConnector]
    val requestBuilder = mock[RequestBuilder]
    val servicesConfig = new ServicesConfig(
      Configuration(
        "microservice.services.eis.host"         -> "host",
        "microservice.services.eis.port"         -> 443,
        "microservice.services.eis.protocol"     -> "https",
        "microservice.services.eis.bearer-token" -> "AbCdEf123456",
        "microservice.services.eis.endpoint"     -> "9102",
        "microservice.services.eis.environment"  -> "dev"
      )
    )
    when(httpClient.put(any[URL])(any[HeaderCarrier])).thenReturn(requestBuilder)
    when(requestBuilder.withBody(any)(any, any, any)).thenReturn(requestBuilder)
    when(requestBuilder.setHeader(any)).thenReturn(requestBuilder)

    "return unit on success" in {
      when(requestBuilder.execute[HttpResponse]).thenReturn(Future.successful(HttpResponse(NO_CONTENT, "")))

      val eisConnector = new EISConnector(httpClient, servicesConfig, auditConnector)
      val result = eisConnector.forwardMessage(
        QueryMessageWrapper(QueryMessageRequest(RequestCommon("", Instant.now, ""), RequestDetail("", "", "")))
      )
      result.futureValue.toOption.get mustBe ()
    }
    "return error for bad request from eis" in {
      when(requestBuilder.execute[HttpResponse]).thenReturn(Future.successful(HttpResponse(BAD_REQUEST, "")))

      val eisConnector = new EISConnector(httpClient, servicesConfig, auditConnector)
      val result = eisConnector.forwardMessage(
        QueryMessageWrapper(QueryMessageRequest(RequestCommon("", Instant.now, ""), RequestDetail("", "", "")))
      )
      result.futureValue.left.value.message must include("There was an issue with forwarding the message to EIS")
    }
  }
}
