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

package uk.gov.hmrc.securemessage.templates.satemplates.sa316

import play.api.{ Configuration, Mode }
import play.api.i18n.Messages
import play.api.libs.json.*
import play.twirl.api.HtmlFormat
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.securemessage.templates.satemplates.helpers.{ RenderingData, TaxYear }
import uk.gov.hmrc.mongo.workitem.ProcessingStatus.ToDo
import uk.gov.hmrc.play.audit.model.EventTypes.Failed as TxFailed
import uk.gov.hmrc.securemessage.templates.SATemplates

import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.{ Instant, LocalDate, ZoneId }
import scala.concurrent.{ ExecutionContext, Future }

case class SA316_v1ContentParams(
  taxYear: TaxYear,
  paperDeadline: Option[LocalDate],
  onlineDeadline: LocalDate,
  finalPaymentDeadline: LocalDate
)

object SA316_v1ContentParams {

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

  implicit val jsonFormat: Format[SA316_v1ContentParams] =
    Json.format[SA316_v1ContentParams]
}

case object TemplateSA316_v1 extends SATemplates {
  val templateKey = "SA316_v1"

  override def render(
    renderingData: RenderingData
  )(implicit messages: Messages): HtmlFormat.Appendable =
    html.SA316_v1(
      data = renderingData.contentParametersData.as[SA316_v1ContentParams],
      urlBuilder = renderingData.portalUrlBuilder,
      saUtr = renderingData.saUtr
    )
}

case object TemplateSA316_previous_year_v1 extends SATemplates {
  val templateKey = "SA316_previous_year_v1"

  def render(renderingData: RenderingData)(implicit messages: Messages): HtmlFormat.Appendable =
    html.SA316_previous_year_v1(
      renderingData.contentParametersData.as[SA316_v1ContentParams],
      renderingData.portalUrlBuilder,
      renderingData.saUtr
    )
}

case object TemplateSA316_v2 extends SATemplates {
  val templateKey = "SA316_v2"

  override def render(
    renderingData: RenderingData
  )(implicit messages: Messages): HtmlFormat.Appendable =
    html.SA316_v2(
      renderingData.contentParametersData.as[SA316_v2ContentParams],
      renderingData.portalUrlBuilder,
      renderingData.saUtr
    )
}

case object TemplateSA316_previous_year_v2 extends SATemplates {
  val templateKey = "SA316_previous_year_v2"

  def render(renderingData: RenderingData)(implicit messages: Messages): HtmlFormat.Appendable =
    html.SA316_previous_year_v2(
      renderingData.contentParametersData.as[SA316_v2ContentParams],
      renderingData.portalUrlBuilder,
      renderingData.saUtr
    )
}

case class SA316_v2ContentParams(
  taxYear: TaxYear,
  paperDeadline: Option[LocalDate],
  onlineDeadline: LocalDate,
  finalPaymentDeadline: LocalDate
)

object SA316_v2ContentParams {

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

  implicit val jsonFormat: Format[SA316_v2ContentParams] =
    Json.format[SA316_v2ContentParams]
}
