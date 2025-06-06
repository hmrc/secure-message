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

import java.time.Instant
import play.api.libs.json.{ Format, Json }
import uk.gov.hmrc.securemessage.formatter.PlayJsonFormats.*
import uk.gov.hmrc.common.message.emailaddress.*
import uk.gov.hmrc.common.message.model.DateFormats.{ formatInstantReads, formatInstantWrites }

final case class Participant(
  id: Int,
  participantType: ParticipantType,
  identifier: Identifier,
  name: Option[String],
  email: Option[EmailAddress],
  parameters: Option[Map[String, String]],
  readTimes: Option[List[Instant]]
) extends OrderingDefinitions {
  def lastReadTime: Option[Instant] = readTimes.map(_.max(dateTimeAscending))
}

object Participant {

  implicit val instantFormat: Format[Instant] = Format(formatInstantReads(), formatInstantWrites())

  implicit val emailAddressFormat: Format[EmailAddress] = Format(emailAddressReads, emailAddressWrites)

  implicit val participantFormat: Format[Participant] = Json.format[Participant]
}
