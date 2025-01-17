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

package uk.gov.hmrc.securemessage.templates.satemplates.sa372

import play.api.i18n.Messages
import play.api.libs.json.*
import play.api.{ Configuration, Mode }
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.securemessage.templates.{ SATemplates, WithSecureMessageIntegration }
import uk.gov.hmrc.securemessage.templates.satemplates.helpers.{ RenderingData, TaxYear }
import uk.gov.hmrc.securemessage.templates.satemplates.sa372.SA372_ContentParams.*

import java.time.{ Instant, LocalDate }
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.matching.Regex

object SA372_ContentParams {
  implicit val datetimeFormatDefault: Format[Instant] = new Format[Instant] {
    override def reads(json: JsValue): JsResult[Instant] =
      Reads.DefaultInstantReads.reads(json)
    override def writes(o: Instant): JsValue =
      Writes.DefaultInstantWrites.writes(o)
  }
  implicit val localdateFormatDefault: Format[LocalDate] =
    new Format[LocalDate] {
      override def reads(json: JsValue): JsResult[LocalDate] =
        Reads.DefaultLocalDateReads.reads(json)
      override def writes(o: LocalDate): JsValue =
        Writes.DefaultLocalDateWrites.writes(o)
    }

  implicit val formats: OFormat[SA372_ContentParams] =
    Json.format[SA372_ContentParams]
}

case class SA372_ContentParams(
  taxYear: TaxYear,
  onlineFilingDate: Option[LocalDate],
  showWhatToDoLateFiling: Boolean,
  showPenaltyFor30Variant: Boolean
)

case object TemplateSA372 extends SATemplates with WithSecureMessageIntegration {
  val templateKey = "SA372"

  override def render(
    renderingData: RenderingData
  )(implicit messages: Messages): play.twirl.api.HtmlFormat.Appendable =
    html.SA372(renderingData.contentParametersData.as[SA372_ContentParams], renderingData)
}
