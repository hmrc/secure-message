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

import play.api.Logging
import play.api.libs.json.*
import uk.gov.hmrc.common.message.model.TaxpayerName
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.http.{ HeaderCarrier, NotFoundException }
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2

import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }
import java.net.URL

@Singleton
class TaxpayerNameConnector @Inject() (
  http: HttpClientV2,
  servicesConfig: ServicesConfig
)(implicit ec: ExecutionContext)
    extends Logging {

  def baseUrl: String = servicesConfig.baseUrl("taxpayer-data")

  def prepareUrl(path: String): URL = common.url(s"$baseUrl$path")

  def taxpayerName(utr: SaUtr)(implicit hc: HeaderCarrier): Future[Option[TaxpayerName]] = {

    implicit val nameFormat: OFormat[NameFromHods] = NameFromHods.format

    http
      .get(prepareUrl(s"/self-assessment/individual/$utr/designatory-details/taxpayer"))
      .execute[NameFromHods]
      .map(_.name)
      .recover {
        case notFound: NotFoundException =>
          logger.warn(s"No taxpayer name found for utr: $utr, ${notFound.getMessage}")
          None
        case e =>
          logger.error(s"Unable to get taxpayer name for $utr, ${e.getMessage}")
          None
      }
  }
}

case class NameFromHods(name: Option[TaxpayerName])

object NameFromHods {

  implicit val taxpayerNameFormat: Format[TaxpayerName] = TaxpayerName.taxpayerNameFormat
  implicit val format: OFormat[NameFromHods] = Json.format[NameFromHods]
}
