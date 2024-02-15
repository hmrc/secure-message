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

package uk.gov.hmrc.securemessage.models.core

import play.api.libs.json.{ JsError, JsPath, JsResult, JsString, JsSuccess, JsValue, JsonValidationError, Reads, Writes }

import java.time.{ Instant, LocalDate, OffsetDateTime, ZoneId, ZoneOffset }
import java.time.format.DateTimeFormatter

object DateFormats {

  def formatLocalDateReads(pattern: String, corrector: String => String = identity): Reads[LocalDate] =
    new Reads[LocalDate] {
      val df = if (pattern == "") DateTimeFormatter.ISO_DATE_TIME else DateTimeFormatter.ofPattern(pattern)

      def reads(json: JsValue): JsResult[LocalDate] = json match {
        case JsString(s) =>
          parseDate(corrector(s)) match {
            case Some(d) => JsSuccess(d)
            case _       => JsError(Seq(JsPath() -> Seq(JsonValidationError("error.expected.java date.format", pattern))))
          }
        case _ => JsError(Seq(JsPath() -> Seq(JsonValidationError("error.expected.date"))))
      }

      private def parseDate(input: String): Option[LocalDate] =
        scala.util.control.Exception.nonFatalCatch[LocalDate].opt(LocalDate.parse(input, df))
    }

  def formatLocalDateWrites(pattern: String): Writes[LocalDate] = {
    val df = DateTimeFormatter.ofPattern(pattern)
    Writes[LocalDate] { d =>
      JsString(d.format(df))
    }
  }

  def formatInstantReads(corrector: String => String = identity): Reads[Instant] =
    new Reads[Instant] {
      val df: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX")
      def reads(json: JsValue): JsResult[Instant] = json match {
        case JsString(s) =>
          parseDate(corrector(s)) match {
            case Some(d) => JsSuccess(d)
            case _       => JsError(Seq(JsPath() -> Seq(JsonValidationError("error.expected.instant date.format"))))
          }
        case _ => JsError(Seq(JsPath() -> Seq(JsonValidationError("error.expected.date"))))
      }

      private def parseDate(input: String): Option[Instant] =
        scala.util.control.Exception
          .nonFatalCatch[Instant]
          .opt(OffsetDateTime.parse(input, df).toInstant)
    }

  def formatInstantWrites(): Writes[Instant] = {
    val df: DateTimeFormatter =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ").withZone(ZoneId.from(ZoneOffset.UTC))
    Writes[Instant] { d =>
      JsString(df.format(d))
    }
  }
}
