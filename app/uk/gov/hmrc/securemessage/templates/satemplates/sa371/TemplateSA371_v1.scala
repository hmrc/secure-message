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

package uk.gov.hmrc.securemessage.templates.satemplates.sa371

import play.api.i18n.Messages
import play.api.libs.json.{ Json, OFormat }
import uk.gov.hmrc.securemessage.templates.SATemplates
import uk.gov.hmrc.securemessage.templates.satemplates.helpers.{ RenderingData, TaxYear }
import uk.gov.hmrc.securemessage.templates.satemplates.sa37X.*
import uk.gov.hmrc.securemessage.templates.satemplates.sa371.fragments.html.*

object SA371_v1ContentParams {
  implicit val formats: OFormat[SA371_v1ContentParams] =
    Json.format[SA371_v1ContentParams]
}

case class SA371_v1ContentParams(
  taxYear: TaxYear,
  penaltiesTotal: String,
  partnershipName: String,
  showWhatToDoLateFiling: Boolean,
  showLateFilingHeading: Boolean,
  showRecentlyFiledInfo: Boolean,
  lateFilingPenalties: Seq[Penalty]
)

case object TemplateSA371_v1 extends SATemplates {
  val templateKey = "SA371_v1"

  override def render(
    renderingData: RenderingData
  )(implicit messages: Messages): play.twirl.api.HtmlFormat.Appendable =
    html.SA371_v1(
      renderingData.contentParametersData.as[SA371_v1ContentParams],
      List(
        SA371FilingFirst3MonthsPenaltyParams,
        SA371FilingSecond3MonthsPenaltyParams,
        SA371Filing6MonthsMinimumPenaltyParams,
        SA371Filing12MonthsMinimumPenaltyParams
      ),
      renderingData.platformUrls
    )
}

object SA371FilingFirst3MonthsPenaltyParams extends SA37XFilingFirst3MonthsPenaltyParams {
  override val templates = Map("FilingFirst3MonthsPenalty_v1" -> FilingFirst3MonthsPenalty.f)
}

object SA371FilingSecond3MonthsPenaltyParams extends SA37XFilingSecond3MonthsPenaltyParams {
  override val templates = Map(
    "FilingSecond3MonthsOnlinePenalty_v1" -> FilingSecond3MonthsOnlinePenalty.f,
    "FilingSecond3MonthsPaperPenalty_v1"  -> FilingSecond3MonthsPaperPenalty.f
  )
}

object SA371Filing6MonthsMinimumPenaltyParams extends SA37XFiling6MonthsMinimumPenaltyParams {
  override val templates = Map("Filing6MonthsMinimumPenalty_v1" -> Filing6MonthsMinimumPenalty.f)
}

object SA371Filing12MonthsMinimumPenaltyParams extends SA37XFiling12MonthsMinimumPenaltyParams {
  override val templates = Map("Filing12MonthsMinimumPenalty_v1" -> Filing12MonthsMinimumPenalty.f)
}
