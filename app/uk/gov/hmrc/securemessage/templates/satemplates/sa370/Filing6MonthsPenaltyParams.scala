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

package uk.gov.hmrc.securemessage.templates.satemplates.sa370

import uk.gov.hmrc.securemessage.templates.satemplates.sa370.fragments.html.*
import uk.gov.hmrc.securemessage.templates.satemplates.sa37X.ParamType
import play.api.libs.json.{ Format, JsValue, Json, OFormat }

case class Filing6MonthsPenaltyParams(
  penaltyPercentage: String,
  penalty: String,
  charge: String,
  toPay: String
)

object Filing6MonthsPenaltyParams extends ParamType[Filing6MonthsPenaltyParams] {
  implicit val format: OFormat[Filing6MonthsPenaltyParams] =
    Json.format[Filing6MonthsPenaltyParams]

  override val templates = Map(
    "Filing6MonthsDeterminedPenalty_v1"         -> Filing6MonthsDeterminedPenalty_v1.f,
    "Filing6MonthsAmendedPenalty_v1"            -> Filing6MonthsAmendedPenalty_v1.f,
    "Filing6MonthsCorrectedFurtherPenalty_v1"   -> Filing6MonthsCorrectedFurtherPenalty_v1.f,
    "Filing6MonthsFiledAndFurtherPenalty_v1"    -> Filing6MonthsFiledAndFurtherPenalty_v1.f,
    "Filing6MonthsFurtherPenalty_v1"            -> Filing6MonthsFurtherPenalty_v1.f,
    "Filing6MonthsRevisedAfterFilingPenalty_v1" -> Filing6MonthsRevisedAfterFilingPenalty_v1.f,
    "Filing6MonthsSettledFurtherPenalty_v1"     -> Filing6MonthsSettledFurtherPenalty_v1.f,
    "Filing6MonthsTotalPenalty_v1"              -> Filing6MonthsTotalPenalty_v1.f
  )

  override def convertParams(contentParams: JsValue): Filing6MonthsPenaltyParams =
    contentParams.as[Filing6MonthsPenaltyParams]
}
