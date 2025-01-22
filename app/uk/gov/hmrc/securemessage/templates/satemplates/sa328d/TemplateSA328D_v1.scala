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

package uk.gov.hmrc.securemessage.templates.satemplates.sa328d

import play.api.{ Configuration, Mode }
import play.api.i18n.Messages
import play.api.libs.json.*
import play.twirl.api.HtmlFormat
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.securemessage.templates.{ SATemplates, WithSecureMessageIntegration }
import uk.gov.hmrc.securemessage.templates.satemplates.helpers.{ RenderingData, TaxYear }

import java.time.format.DateTimeFormatter
import java.time.{ Instant, LocalDate }
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.matching.Regex

case class SA328D_v1ContentParams(
  taxYear: TaxYear,
  penaltyDueDate: LocalDate,
  partnershipName: Option[String] = None
) {
  def penaltyDueDateFormatted: String =
    DateTimeFormatter
      .ofPattern("dd MMMM YYYY")
      .format(penaltyDueDate)

}

object SA328D_v1ContentParams {
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

  implicit val format: OFormat[SA328D_v1ContentParams] =
    Json.format[SA328D_v1ContentParams]
}

case object TemplateSA328D_v1 extends SATemplates with WithSecureMessageIntegration {
  val templateKey = "SA328D_v1"

  def render(renderingData: RenderingData)(implicit messages: Messages): HtmlFormat.Appendable =
    html.SA328D_v1(
      renderingData.contentParametersData.as[SA328D_v1ContentParams],
      renderingData
    )
}
