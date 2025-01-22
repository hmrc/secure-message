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

import play.api.libs.json.{ Format, JsValue, Json, OFormat }
import uk.gov.hmrc.securemessage.templates.satemplates.sa37X.ParamType
import uk.gov.hmrc.securemessage.templates.satemplates.sa370.fragments.html.*

case class Filing12MonthsPenaltyParams(
  penaltyPercentage: String,
  penalty: String,
  charge: String,
  toPay: String
)

object Filing12MonthsPenaltyParams extends ParamType[Filing12MonthsPenaltyParams] {
  implicit val formats: OFormat[Filing12MonthsPenaltyParams] =
    Json.format[Filing12MonthsPenaltyParams]

  override val templates = Map(
    "Filing12MonthsAmendedPenalty_v1"            -> Filing12MonthsAmendedPenalty_v1.f,
    "Filing12MonthsCorrectedFurtherPenalty_v1"   -> Filing12MonthsCorrectedFurtherPenalty_v1.f,
    "Filing12MonthsDeterminedPenalty_v1"         -> Filing12MonthsDeterminedPenalty_v1.f,
    "Filing12MonthsFiledAndFurtherPenalty_v1"    -> Filing12MonthsFiledAndFurtherPenalty_v1.f,
    "Filing12MonthsFurtherPenalty_v1"            -> Filing12MonthsFurtherPenalty_v1.f,
    "Filing12MonthsRevisedAfterFilingPenalty_v1" -> Filing12MonthsRevisedAfterFilingPenalty_v1.f,
    "Filing12MonthsSettledFurtherPenalty_v1"     -> Filing12MonthsSettledFurtherPenalty_v1.f,
    "Filing12MonthsTotalPenalty_v1"              -> Filing12MonthsTotalPenalty_v1.f
  )

  override def convertParams(contentParams: JsValue): Filing12MonthsPenaltyParams =
    contentParams.as[Filing12MonthsPenaltyParams]
}
