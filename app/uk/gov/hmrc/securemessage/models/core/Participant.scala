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

package uk.gov.hmrc.securemessage.models.core

import cats.data.NonEmptyList
import play.api.libs.json.{ Format, Json }

final case class Participant(
  id: Int,
  participantType: ParticipantType,
  identifier: Identifier,
  name: Option[String],
  email: Option[String],
  parameters: Option[Map[String, String]])
object Participant {
  implicit val participantFormat: Format[Participant] = Json.format[Participant]
}

final case class Participants(participants: NonEmptyList[Participant])
object Participants {
  import uk.gov.hmrc.securemessage.models.utils.NonEmptyListOps._
  implicit val participantsFormat: Format[Participants] = Json.format[Participants]

}
