/*
 * Copyright 2022 HM Revenue & Customs
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

import com.github.nscala_time.time.Imports.DateTime
import play.api.http.Status.BAD_REQUEST
import play.api.http.Status.NO_CONTENT
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Writes
import uk.gov.hmrc.http.{ HttpClient, HttpResponse }
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.securemessage.models.{ QueryMessageRequest, QueryMessageWrapper, RequestCommon, RequestDetail }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ ExecutionContext, Future }

class EISConnectorSpec extends PlaySpec with ScalaFutures with MockitoSugar {

  "forwardMessage" must {
    val httpClient = mock[HttpClient]
    val servicesConfig = mock[ServicesConfig]
    val auditConnector = mock[AuditConnector]
    "return unit on success" in {
      when(
        httpClient.doPut(any[String], any[String](), any[Seq[(String, String)]]())(
          any[Writes[String]](),
          any[ExecutionContext]()))
        .thenReturn(Future.successful(HttpResponse(NO_CONTENT, "")))

      val eisConnector = new EISConnector(httpClient, servicesConfig, auditConnector)
      val result = eisConnector.forwardMessage(
        QueryMessageWrapper(QueryMessageRequest(RequestCommon("", DateTime.now, ""), RequestDetail("", "", ""))))
      result.futureValue.right.get mustBe (())
    }
    "return error for bad request from eis" in {
      when(
        httpClient.doPut(any[String], any[String](), any[Seq[(String, String)]]())(
          any[Writes[String]](),
          any[ExecutionContext]()))
        .thenReturn(Future.successful(HttpResponse(BAD_REQUEST, "")))

      val eisConnector = new EISConnector(httpClient, servicesConfig, auditConnector)
      val result = eisConnector.forwardMessage(
        QueryMessageWrapper(QueryMessageRequest(RequestCommon("", DateTime.now, ""), RequestDetail("", "", ""))))
      result.futureValue.left.get.message must include("There was an issue with forwarding the message to EIS")
    }
  }
}
