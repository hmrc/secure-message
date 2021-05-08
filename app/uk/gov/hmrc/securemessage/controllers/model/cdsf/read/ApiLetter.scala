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

package uk.gov.hmrc.securemessage.controllers.model.cdsf.read

import org.joda.time.{ DateTime, LocalDate }
import play.api.libs.json.JodaReads.{ jodaDateReads, jodaLocalDateReads }
import play.api.libs.json.JodaWrites.{ jodaDateWrites, jodaLocalDateWrites }
import play.api.libs.json.{ Format, Json }
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.securemessage.models.core.Letter

final case class ApiLetter(
  subject: String,
  content: String,
  firstReaderInformation: Option[FirstReaderInformation],
  senderInformation: SenderInformation,
  readTime: Option[DateTime] = None
)

final case class FirstReaderInformation(name: Option[String], read: DateTime)
final case class SenderInformation(name: String, sent: LocalDate)

object ApiLetter {
  def fromCore(letter: Letter): ApiLetter =
    ApiLetter(
      letter.subject,
      letter.content,
      letter.readTime.map(FirstReaderInformation(None, _)),
      SenderInformation("HMRC", letter.validFrom)
    )

  private val dateTimeFormatString = "yyyy-MM-dd'T'HH:mm:ss.SSSZ"
  private val dateFormatString = "yyyy-MM-dd"

  implicit val datetimeFormat: Format[DateTime] =
    Format(jodaDateReads(dateTimeFormatString), jodaDateWrites(dateTimeFormatString))

  implicit val dateFormat: Format[LocalDate] =
    Format(jodaLocalDateReads(dateFormatString), jodaLocalDateWrites(dateFormatString))

  implicit val firstReaderInformationFormat: Format[FirstReaderInformation] =
    Json.format[FirstReaderInformation]

  implicit val senderInformationFormat: Format[SenderInformation] =
    Json.format[SenderInformation]

  implicit val bsonObjectIdFormat: Format[BSONObjectID] =
    Json.format[BSONObjectID]

  implicit val messageFormat: Format[ApiLetter] =
    Json.format[ApiLetter]
}
