/*
 * Copyright 2025 HM Revenue & Customs
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

import org.mockito.ArgumentMatchers.{ any, eq as eqTo }
import org.mockito.Mockito.*
import org.mockito.Mockito
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.Status
import uk.gov.hmrc.http.{ HeaderCarrier, HttpResponse }
import uk.gov.hmrc.http.client.{ HttpClientV2, RequestBuilder }
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.securemessage.models.v4.MobileNotification
import uk.gov.hmrc.securemessage.services.utils.GenerateRandom

import java.net.URL
import scala.concurrent.{ ExecutionContext, Future }

class MobilePushNotificationsConnectorSpec extends AnyWordSpec with Matchers with MockitoSugar with ScalaFutures {

  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  private val baseUrl = "http://mock-notification-service"

  private val http = mock[HttpClientV2]
  private val auditConnector = mock[AuditConnector]

  private val requestBuilder = mock[RequestBuilder]

  private val nino = GenerateRandom.nino()
  private val notification = MobileNotification(nino, "test-template-id")

  when(http.post(any[URL])(any[HeaderCarrier])).thenReturn(requestBuilder)
  when(requestBuilder.withBody(any)(any, any, any)).thenReturn(requestBuilder)

  private val connector = new MobilePushNotificationsConnector(http, auditConnector, baseUrl)

  "sendNotification" should {

    "audit and return Unit when the service responds with CREATED (201)" in {
      when(requestBuilder.execute[HttpResponse](any(), any()))
        .thenReturn(Future.successful(HttpResponse(Status.CREATED, "")))

      val result = connector.sendNotification(notification)

      result.map { _ =>
        verify(connector).auditMobilePushNotification(
          eqTo(notification),
          eqTo(Status.CREATED.toString),
          eqTo(None)
        )
        succeed
      }
    }

    "audit error when service responds with non-201 status" in {
      when(requestBuilder.execute[HttpResponse](any(), any()))
        .thenReturn(Future.successful(HttpResponse(Status.BAD_REQUEST, "bad request")))

      connector.sendNotification(notification).map { _ =>
        verify(connector).auditMobilePushNotification(
          eqTo(notification),
          eqTo(Status.BAD_REQUEST.toString),
          eqTo(Some("Failed to push the notification. Response:bad request"))
        )
        succeed
      }
    }

    "audit internal error when an exception occurs" in {
      when(requestBuilder.execute[HttpResponse](any(), any()))
        .thenReturn(Future.failed(new RuntimeException("error")))

      connector.sendNotification(notification).map { _ =>
        verify(connector).auditMobilePushNotification(
          eqTo(notification),
          eqTo("internal-error"),
          eqTo(Some("error"))
        )
        succeed
      }
    }
  }
}
