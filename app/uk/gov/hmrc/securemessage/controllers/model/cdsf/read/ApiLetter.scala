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

package uk.gov.hmrc.securemessage.controllers.model.cdsf.read

import java.time.{Instant, LocalDate}
import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.common.message.model.Language
import uk.gov.hmrc.securemessage.controllers.model.common.read.MessageMetadata
import uk.gov.hmrc.securemessage.controllers.model.{ApiFormats, ApiMessage}
import uk.gov.hmrc.securemessage.models.core.{Identifier, Letter}
import uk.gov.hmrc.securemessage.models.v4.SecureMessage

final case class ApiLetter(
  subject: String,
  content: String,
  firstReaderInformation: Option[FirstReaderInformation],
  senderInformation: SenderInformation,
  identifier: Identifier,
  readTime: Option[Instant] = None, // TODO: why is this always NONE ?
  tags: Option[Map[String, String]] = None
) extends ApiMessage

final case class FirstReaderInformation(name: Option[String], read: Instant)
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

  def fromSecureMessage(secureMessage: SecureMessage)(implicit language: Language): ApiLetter = {
    val taxId = secureMessage.recipient.identifier
    val content = MessageMetadata.contentForLanguage(language, secureMessage.content)
    ApiLetter(
      content.map(_.subject).getOrElse(""),
      content.map(_.body).getOrElse(""),
      secureMessage.readTime.map(FirstReaderInformation(None, _)),
      SenderInformation("HMRC", secureMessage.validFrom),
      identifier = Identifier("", taxId.value, Some(taxId.name)),
      readTime = secureMessage.readTime,
      tags = secureMessage.tags
    )
  }

  implicit val firstReaderInformationFormat: Format[FirstReaderInformation] =
    Json.format[FirstReaderInformation]

  implicit val senderInformationFormat: Format[SenderInformation] =
    Json.format[SenderInformation]

  implicit val messageFormat: Format[ApiLetter] =
    Json.format[ApiLetter]
}
