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
import play.api.libs.json._
import uk.gov.hmrc.common.message.model.TaxpayerName
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.http.{ HeaderCarrier, HttpClient, NotFoundException }
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.http.HttpReads.Implicits._

import javax.inject.{ Inject, Singleton }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class TaxpayerNameConnector @Inject()(
  http: HttpClient,
  servicesConfig: ServicesConfig
) extends Logging {

  def baseUrl: String = servicesConfig.baseUrl("taxpayer-data")

  def url(path: String): String = s"$baseUrl$path"

  def taxpayerName(utr: SaUtr)(implicit hc: HeaderCarrier): Future[Option[TaxpayerName]] = {

    implicit val nameFormat: OFormat[NameFromHods] = NameFromHods.format

    http
      .GET[NameFromHods](url(s"/self-assessment/individual/$utr/designatory-details/taxpayer"))
      .map(_.name)
      .recover {
        case notFound: NotFoundException =>
          logger.warn(s"No taxpayer name found for utr: $utr", notFound)
          None
        case e =>
          logger.error(s"Unable to get taxpayer name for $utr", e)
          None
      }
  }
}

case class NameFromHods(name: Option[TaxpayerName])

object NameFromHods {

  implicit val taxpayerNameFormat: Format[TaxpayerName] = TaxpayerName.taxpayerNameFormat
  implicit val format: OFormat[NameFromHods] = Json.format[NameFromHods]
}
