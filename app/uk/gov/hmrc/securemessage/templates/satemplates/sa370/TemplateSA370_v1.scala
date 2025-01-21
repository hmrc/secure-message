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

import play.api.i18n.Messages
import play.api.libs.json.{ Json, OFormat }
import uk.gov.hmrc.securemessage.templates.{ SATemplates, WithSecureMessageIntegration }
import uk.gov.hmrc.securemessage.templates.satemplates.helpers.{ RenderingData, TaxYear }
import uk.gov.hmrc.securemessage.templates.satemplates.sa37X.*
import uk.gov.hmrc.securemessage.templates.satemplates.sa370.fragments.html.*
import scala.util.matching.Regex

object SA370_v1ContentParams {
  implicit val formats: OFormat[SA370_v1ContentParams] =
    Json.format[SA370_v1ContentParams]
}

case class SA370_v1ContentParams(
  taxYear: TaxYear,
  penaltiesTotal: String,
  showWhatToDoLateFiling: Boolean,
  showWhatToDoLatePayment: Boolean,
  showLateFilingHeading: Boolean,
  showLatePaymentHeading: Boolean,
  showRecentlyFiledInfo: Boolean,
  lateFilingPenalties: Seq[Penalty],
  latePaymentPenalties: Seq[Penalty]
)

case object TemplateSA370_v1 extends SATemplates with WithSecureMessageIntegration {
  val templateKey = "SA370_v1"

  override def render(
    renderingData: RenderingData
  )(implicit messages: Messages): play.twirl.api.HtmlFormat.Appendable =
    html.SA370_v1(
      renderingData.contentParametersData.as[SA370_v1ContentParams],
      List(
        SA370FilingFirst3MonthsPenaltyParams,
        SA370FilingSecond3MonthsPenaltyParams,
        Filing6MonthsPenaltyParams,
        SA370Filing6MonthsMinimumPenaltyParams,
        Filing12MonthsPenaltyParams,
        SA370Filing12MonthsMinimumPenaltyParams,
        PaymentPenaltyParams,
        PaymentIncreasedPenaltyParams
      ),
      renderingData
    )
}

object SA370Filing6MonthsMinimumPenaltyParams extends SA37XFiling6MonthsMinimumPenaltyParams {
  override val templates = Map("Filing6MonthsMinimumPenalty_v1" -> Filing6MonthsMinimumPenalty_v1.f)
}

object SA370Filing12MonthsMinimumPenaltyParams extends SA37XFiling12MonthsMinimumPenaltyParams {
  override val templates = Map(
    "Filing12MonthsMinimumPenalty_v1" -> Filing12MonthsMinimumPenalty_v1.f
  )
}

object SA370FilingFirst3MonthsPenaltyParams extends SA37XFilingFirst3MonthsPenaltyParams {
  override val templates = Map("FilingFirst3MonthsPenalty_v1" -> FilingFirst3MonthsPenalty_v1.f)
}

object SA370FilingSecond3MonthsPenaltyParams extends SA37XFilingSecond3MonthsPenaltyParams {
  override val templates = Map(
    "FilingSecond3MonthsOnlinePenalty_v1" -> FilingSecond3MonthsOnlinePenalty_v1.f,
    "FilingSecond3MonthsPaperPenalty_v1"  -> FilingSecond3MonthsPaperPenalty_v1.f
  )
}
