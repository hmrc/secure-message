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

package uk.gov.hmrc.securemessage.controllers.models.generic

import org.joda.time.DateTime
import play.api.libs.json.JodaReads.jodaDateReads
import play.api.libs.json.JodaWrites.jodaDateWrites
import play.api.libs.json.{ Format, Json, Writes }

final case class ApiMessage(
  senderInformation: Option[SenderInformation],
  firstReader: Option[FirstReaderInformation],
  content: String)

final case class SenderInformation(name: Option[String], sent: DateTime, self: Boolean)

final case class FirstReaderInformation(name: Option[String], read: DateTime)

object ApiMessage {

  private val dateFormatString = "yyyy-MM-dd'T'HH:mm:ss.SSSZ"

  implicit val dateFormat: Format[DateTime] =
    Format(jodaDateReads(dateFormatString), jodaDateWrites(dateFormatString))

  implicit val senderInformationFormat: Writes[SenderInformation] =
    Json.writes[SenderInformation]

  implicit val firstReaderTime: Writes[FirstReaderInformation] =
    Json.writes[FirstReaderInformation]

  implicit val messageFormat: Writes[ApiMessage] =
    Json.writes[ApiMessage]

}
