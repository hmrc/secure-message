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

package uk.gov.hmrc.securemessage.controllers.model.cdcm.read

import org.joda.time.DateTime
import play.api.libs.json.{ Format, Json }
import uk.gov.hmrc.securemessage.controllers.model.ApiFormats

final case class ApiMessage(
  senderInformation: Option[SenderInformation],
  firstReader: Option[FirstReaderInformation],
  content: String)

final case class SenderInformation(name: Option[String], sent: DateTime, self: Boolean)

final case class FirstReaderInformation(name: Option[String], read: DateTime)

object ApiMessage extends ApiFormats {

  implicit val senderInformationFormat: Format[SenderInformation] =
    Json.format[SenderInformation]

  implicit val firstReaderTime: Format[FirstReaderInformation] =
    Json.format[FirstReaderInformation]

  implicit val messageFormat: Format[ApiMessage] =
    Json.format[ApiMessage]

}
