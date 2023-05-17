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

package uk.gov.hmrc.securemessage.services.utils

import uk.gov.hmrc.common.message.model._
import uk.gov.hmrc.domain.TaxIds._
import uk.gov.hmrc.domain._

object MessageFixtures {

  val utr = "1234567890"

  def createTaxEntity(identifier: TaxIdWithName, email: Option[String] = None): TaxEntity = identifier match {
    case x: Nino         => TaxEntity(Regime.paye, x, email)
    case x: SaUtr        => TaxEntity(Regime.sa, x, email)
    case x: CtUtr        => TaxEntity(Regime.ct, x, email)
    case x: HmrcObtdsOrg => TaxEntity(Regime.fhdds, x)
    case x: HmrcMtdVat   => TaxEntity(Regime.vat, x)
    case x               => throw new RuntimeException(s"unsupported identifier $x")
  }

}
