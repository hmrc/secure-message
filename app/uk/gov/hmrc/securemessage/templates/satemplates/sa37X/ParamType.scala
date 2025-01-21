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

package uk.gov.hmrc.securemessage.templates.satemplates.sa37X

import play.twirl.api.HtmlFormat
import play.api.libs.json.{ Format, JsValue, Json, OFormat }

trait ParamType[T] {
  def templateFor(templateId: String): Option[T => HtmlFormat.Appendable] =
    templates.get(templateId)

  def fromJson(contentParams: JsValue, templateId: String): HtmlFormat.Appendable =
    templateFor(templateId).get.apply(convertParams(contentParams))

  protected def convertParams(contentParams: JsValue): T

  protected val templates: Map[String, T => HtmlFormat.Appendable]
}

case class Filing6MonthsMinimumPenaltyParams(minimumPenalty: String)

object Filing6MonthsMinimumPenaltyParams {
  implicit val formats: OFormat[Filing6MonthsMinimumPenaltyParams] =
    Json.format[Filing6MonthsMinimumPenaltyParams]
}

trait SA37XFiling6MonthsMinimumPenaltyParams extends ParamType[Filing6MonthsMinimumPenaltyParams] {
  override def convertParams(contentParams: JsValue): Filing6MonthsMinimumPenaltyParams =
    contentParams.as[Filing6MonthsMinimumPenaltyParams]
}

case class Filing12MonthsMinimumPenaltyParams(minimumPenalty: String)

object Filing12MonthsMinimumPenaltyParams {
  implicit val formats: OFormat[Filing12MonthsMinimumPenaltyParams] =
    Json.format[Filing12MonthsMinimumPenaltyParams]
}

trait SA37XFiling12MonthsMinimumPenaltyParams extends ParamType[Filing12MonthsMinimumPenaltyParams] {
  override def convertParams(contentParams: JsValue): Filing12MonthsMinimumPenaltyParams =
    contentParams.as[Filing12MonthsMinimumPenaltyParams]
}

case class FilingFirst3MonthsPenaltyParams(dailyPenalty: String, maxValue: String)

object FilingFirst3MonthsPenaltyParams {
  implicit val formats: OFormat[FilingFirst3MonthsPenaltyParams] =
    Json.format[FilingFirst3MonthsPenaltyParams]
}

trait SA37XFilingFirst3MonthsPenaltyParams extends ParamType[FilingFirst3MonthsPenaltyParams] {
  override def convertParams(contentParams: JsValue): FilingFirst3MonthsPenaltyParams =
    contentParams.as[FilingFirst3MonthsPenaltyParams]
}

case class FilingSecond3MonthsPenaltyParams(
  dailyPenalty: String,
  numberOfDays: String,
  fromDate: String,
  toDate: String,
  totalPenalty: String
)

object FilingSecond3MonthsPenaltyParams {
  implicit val formats: OFormat[FilingSecond3MonthsPenaltyParams] =
    Json.format[FilingSecond3MonthsPenaltyParams]
}

trait SA37XFilingSecond3MonthsPenaltyParams extends ParamType[FilingSecond3MonthsPenaltyParams] {
  override def convertParams(contentParams: JsValue): FilingSecond3MonthsPenaltyParams =
    contentParams.as[FilingSecond3MonthsPenaltyParams]
}
