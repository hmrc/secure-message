/*
 * Copyright 2021 HM Revenue & Customs
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

import org.joda.time.{ DateTime, LocalDate }
import play.api.libs.json.Format
import play.api.libs.json.JodaReads.{ jodaDateReads, jodaLocalDateReads }
import play.api.libs.json.JodaWrites.{ jodaDateWrites, jodaLocalDateWrites }

trait ApiFormats {
  private val dateTimeFormatString = "yyyy-MM-dd'T'HH:mm:ss.SSSZ"

  implicit val dateTimeFormat: Format[DateTime] =
    Format(jodaDateReads(dateTimeFormatString), jodaDateWrites(dateTimeFormatString))

  private val dateFormatString = "yyyy-MM-dd"

  implicit val dateFormat: Format[LocalDate] =
    Format(jodaLocalDateReads(dateFormatString), jodaLocalDateWrites(dateFormatString))
}
