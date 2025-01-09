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

package uk.gov.hmrc.securemessage.templates.satemplates.helpers

import play.api.libs.functional.syntax._
import play.api.libs.json._

case class TaxYear(startYear: Int, endYear: Int)

object TaxYear {
  implicit val taxYearWrites: OWrites[TaxYear] = (
    (__ \ "start").write[Int] and
      (__ \ "end").write[Int]
  )(taxYear => Tuple.fromProductTyped(taxYear))
  implicit val taxYearReads: Reads[TaxYear] = (
    (__ \ "start").read[Int] and
      (__ \ "end").read[Int]
  )(TaxYear.apply)

  val taxYearStartReads: Reads[TaxYear] =
    Reads.IntReads.map(start => TaxYear(start, start + 1))

  def fromEndYear(taxEndYear: Int): TaxYear =
    TaxYear(taxEndYear - 1, taxEndYear)
}
