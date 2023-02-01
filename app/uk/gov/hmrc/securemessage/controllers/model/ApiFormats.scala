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

package uk.gov.hmrc.securemessage.controllers.model

import org.joda.time.format.{ DateTimeFormat, ISODateTimeFormat }
import org.joda.time.{ DateTime, LocalDate }
import play.api.libs.json.{ Format, JsError, JsNumber, JsPath, JsResult, JsString, JsSuccess, JsValue, JsonValidationError, Reads, Writes }
import play.api.libs.json.JodaReads.jodaLocalDateReads
import play.api.libs.json.JodaWrites.jodaLocalDateWrites

trait ApiFormats {
  private val dateTimeFormatString = "yyyy-MM-dd'T'HH:mm:ss.SSSZ"

  implicit val dateTimeFormat: Format[DateTime] = {
    def jodaDateReads(pattern: String, corrector: String => String = identity): Reads[DateTime] = new Reads[DateTime] {
      val df = if (pattern == "") ISODateTimeFormat.dateOptionalTimeParser else DateTimeFormat.forPattern(pattern)

      def reads(json: JsValue): JsResult[DateTime] = json match {
        case JsNumber(d) => JsSuccess(new DateTime(d.toLong))
        case JsString(s) =>
          parseDate(corrector(s)) match {
            case Some(d) => JsSuccess(d)
            case _       => JsError(Seq(JsPath() -> Seq(JsonValidationError("error.expected.jodadate.format", pattern))))
          }
        case _ => JsError(Seq(JsPath() -> Seq(JsonValidationError("error.expected.date"))))
      }

      private def parseDate(input: String): Option[DateTime] =
        scala.util.control.Exception.nonFatalCatch[DateTime].opt(DateTime.parse(input, df.withZoneUTC()))
    }

    def jodaDateWrites(pattern: String): Writes[DateTime] = new Writes[DateTime] {
      val df = org.joda.time.format.DateTimeFormat.forPattern(pattern)
      def writes(d: DateTime): JsValue = JsString(d.toString(df.withZoneUTC()))
    }

    Format(jodaDateReads(dateTimeFormatString), jodaDateWrites(dateTimeFormatString))
  }

  private val dateFormatString = "yyyy-MM-dd"

  implicit val dateFormat: Format[LocalDate] =
    Format(jodaLocalDateReads(dateFormatString), jodaLocalDateWrites(dateFormatString))
}
