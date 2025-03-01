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
import org.mockito.Mockito.*
import org.scalatest.EitherValues
import org.scalatest.concurrent.{ IntegrationPatience, ScalaFutures }
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{ JsObject, JsString }
import uk.gov.hmrc.domain.{ HmrcMtdVat, SaUtr }
import uk.gov.hmrc.http.client.{ HttpClientV2, RequestBuilder }
import uk.gov.hmrc.http.{ HeaderCarrier, HttpReads, HttpResponse }
import uk.gov.hmrc.mongo.metrix.MetricOrchestrator
import uk.gov.hmrc.securemessage.models.TaxId
import uk.gov.hmrc.securemessage.services.utils.{ GenerateRandom, MessageFixtures, MetricOrchestratorStub }

import java.net.URL
import scala.concurrent.{ ExecutionContext, Future }

class EntityResolverConnectorSpec
    extends PlaySpec with ScalaFutures with MockitoSugar with MetricOrchestratorStub with IntegrationPatience
    with EitherValues {

  implicit val hc: HeaderCarrier = HeaderCarrier()
  lazy implicit val ec: ExecutionContext = mock[ExecutionContext]

  lazy val mockHttp: HttpClientV2 = mock[HttpClientV2]
  lazy val requestBuilder = mock[RequestBuilder]

  private val injector = new GuiceApplicationBuilder()
    .overrides(bind[MetricOrchestrator].to(mockMetricOrchestrator))
    .overrides(bind[HttpClientV2].to(mockHttp))
    .configure("metrics.enabled" -> false)
    .injector()

  lazy val connector: EntityResolverConnector = injector.instanceOf[EntityResolverConnector]

  when(mockHttp.get(any[URL])(any[HeaderCarrier])).thenReturn(requestBuilder)

  "getTaxId in connector" must {
    "return a valid taxId information for the given saUtr" in {
      when(requestBuilder.execute[HttpResponse](any[HttpReads[HttpResponse]], any[ExecutionContext]))
        .thenReturn(
          Future.successful(
            HttpResponse(
              200,
              s"""{ "_id"  : "entityId",
                 |"sautr" :  "1234567890",
                 |"nino"  :  "SJ12345678A"}""".stripMargin,
              Map.empty[String, Seq[String]]
            )
          )
        )

      connector
        .getTaxId(MessageFixtures.createTaxEntity(SaUtr("someUtr")))
        .futureValue mustBe Some(TaxId("entityId", Some("1234567890"), Some("SJ12345678A")))
    }
    "return None when regime is not in list of allowed regimes to make this call eg: vat" in {
      connector
        .getTaxId(MessageFixtures.createTaxEntity(HmrcMtdVat("123456789")))
        .futureValue mustBe None
    }
  }
}
