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

import play.api.{ Configuration, Logging }
import play.api.libs.json.{ Json, OFormat }
import play.mvc.Http.Status
import uk.gov.hmrc.http.{ HeaderCarrier, HttpResponse }
import uk.gov.hmrc.common.message.model.{ Regime, TaxEntity }
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.securemessage.models.TaxId
import uk.gov.hmrc.http.HttpReads.Implicits.readRaw
import uk.gov.hmrc.http.client.HttpClientV2

import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }

import java.net.URL
@Singleton
class EntityResolverConnector @Inject() (config: Configuration, httpClient: HttpClientV2)(implicit ec: ExecutionContext)
    extends ServicesConfig(config) with Logging {

  def prepareUrl(path: String): URL = common.url(s"${baseUrl("entity-resolver")}$path")

  def getTaxId(recipient: TaxEntity)(implicit hc: HeaderCarrier): Future[Option[TaxId]] = {
    val allowedRegimes = Set(Regime.itsa, Regime.paye, Regime.sa)
    if (allowedRegimes.contains(recipient.regime)) {
      httpClient
        .get(prepareUrl(s"/entity-resolver?taxRegime=${recipient.regime}&taxId=${recipient.identifier.value}"))
        .execute[HttpResponse]
        .map { response =>
          response.status match {
            case Status.OK => response.json.asOpt[TaxId]
            case _         => None
          }
        }
    } else {
      Future.successful(None)
    }
  }

}
