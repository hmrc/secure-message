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

package uk.gov.hmrc.securemessage.templates.satemplates.sa359

import play.api.{ Configuration, Mode }
import play.api.i18n.Messages
import play.api.libs.json.*
import play.twirl.api.HtmlFormat
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.securemessage.templates.SATemplates
import uk.gov.hmrc.securemessage.templates.satemplates.helpers.{ RenderingData, TaxYear }

import java.time.{ Instant, LocalDate, Month }
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.matching.Regex

case class SA359_v1ContentParams(taxYear: TaxYear, onlineDeadline: Option[LocalDate])

object SA359_v1ContentParams {
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

  implicit val formats: OFormat[SA359_v1ContentParams] =
    Json.format[SA359_v1ContentParams]
}

case object TemplateSA359_v1 extends SATemplates {
  val templateKey = "SA359_v1"

  def render(renderingData: RenderingData)(implicit messages: Messages): HtmlFormat.Appendable =
    html.SA359_v1(
      renderingData.contentParametersData.as[SA359_v1ContentParams],
      renderingData.platformUrls
    )
}

case class SA359_v2ContentParams(taxYear: TaxYear, onlineDeadline: Option[LocalDate])

object SA359_v2ContentParams {
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
  implicit val formats: OFormat[SA359_v2ContentParams] =
    Json.format[SA359_v2ContentParams]
}

case object TemplateSA359_v2 extends SATemplates {
  val templateKey = "SA359_v2"

  def render(renderingData: RenderingData)(implicit messages: Messages): HtmlFormat.Appendable =
    html.SA359_v2(
      renderingData.contentParametersData.as[SA359_v2ContentParams],
      renderingData.platformUrls
    )
}
