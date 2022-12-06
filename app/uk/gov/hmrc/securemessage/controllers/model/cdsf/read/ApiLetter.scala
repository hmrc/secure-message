/*
 * Copyright 2022 HM Revenue & Customs
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
import play.api.libs.json.{ Format, Json }
import uk.gov.hmrc.securemessage.controllers.model.{ ApiFormats, ApiMessage }
import uk.gov.hmrc.securemessage.models.core.{ Identifier, Letter }

final case class ApiLetter(
  subject: String,
  content: String,
  firstReaderInformation: Option[FirstReaderInformation],
  senderInformation: SenderInformation,
  identifier: Identifier,
  readTime: Option[DateTime] = None, //TODO: why is this always NONE ?
  tags: Option[Map[String, String]] = None
) extends ApiMessage

final case class FirstReaderInformation(name: Option[String], read: DateTime)
final case class SenderInformation(name: String, sent: LocalDate)

object ApiLetter extends ApiFormats {
  def fromCore(letter: Letter): ApiLetter =
    ApiLetter(
      letter.subject,
      letter.content.getOrElse(""),
      letter.readTime.map(FirstReaderInformation(None, _)),
      SenderInformation("HMRC", letter.validFrom),
      identifier = letter.recipient.identifier,
      readTime = letter.readTime,
      tags = letter.tags
    )

  implicit val firstReaderInformationFormat: Format[FirstReaderInformation] =
    Json.format[FirstReaderInformation]

  implicit val senderInformationFormat: Format[SenderInformation] =
    Json.format[SenderInformation]

  implicit val messageFormat: Format[ApiLetter] =
    Json.format[ApiLetter]
}
