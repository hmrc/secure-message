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

package uk.gov.hmrc.securemessage.templates.satemplates.sa326d

import play.api.{ Configuration, Mode }
import play.api.i18n.Messages
import play.api.libs.json.*
import play.twirl.api.HtmlFormat
import uk.gov.hmrc.securemessage.templates.satemplates.helpers.{ DailyPenalty, RenderingData, TaxYear }
import uk.gov.hmrc.securemessage.templates.{ SATemplates, WithSecureMessageIntegration }

import java.time.LocalDate
import scala.concurrent.{ ExecutionContext, Future }

object SA326D_v1ContentParams {
  implicit val format: OFormat[SA326D_v1ContentParams] = {
    implicit val penaltyAccrualStartDatesFormat: OFormat[DailyPenalty] =
      DailyPenalty.format

    implicit val localdateFormatDefault: Format[LocalDate] =
      new Format[LocalDate] {
        override def reads(json: JsValue): JsResult[LocalDate] =
          Reads.DefaultLocalDateReads.reads(json)
        override def writes(o: LocalDate): JsValue =
          Writes.DefaultLocalDateWrites.writes(o)
      }
    Json.format[SA326D_v1ContentParams]
  }
}

case class SA326D_v1ContentParams(
  taxYear: TaxYear,
  penaltyDueDate: LocalDate,
  paperFilingDate: Option[LocalDate],
  onlineFilingDate: Option[LocalDate],
  filed: Boolean,
  dailyPenalty: Option[DailyPenalty]
)

case object TemplateSA326D_filed_v1 extends SATemplates with WithSecureMessageIntegration {
  val templateKey = "SA326D_filed_v1"

  def render(renderingData: RenderingData)(implicit messages: Messages): HtmlFormat.Appendable =
    html.SA326D_filed_v1(
      renderingData.contentParametersData.as[SA326D_v1ContentParams],
      renderingData
    )
}

case object TemplateSA326D_not_filed_v1 extends SATemplates with WithSecureMessageIntegration {
  val templateKey = "SA326D_not_filed_v1"

  def render(renderingData: RenderingData)(implicit messages: Messages): HtmlFormat.Appendable =
    html.SA326D_not_filed_v1(
      renderingData.contentParametersData.as[SA326D_v1ContentParams],
      renderingData
    )
}
