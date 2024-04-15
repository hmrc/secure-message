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

package uk.gov.hmrc.securemessage.models

import play.api.libs.json.{ Json, OWrites }
import uk.gov.hmrc.common.message.emailaddress._

final case class Tags(messageId: Option[String], source: Option[String], enrolment: Option[String])

final case class EmailRequest(
  to: List[EmailAddress],
  templateId: String,
  parameters: Map[String, String],
  tags: Option[Tags],
  auditData: Map[String, String] = Map.empty,
  eventUrl: Option[String] = None,
  onSendUrl: Option[String] = None,
  alertQueue: Option[String] = None,
  emailSource: Option[String] = None
)

object EmailRequest {

  implicit val enrolmentsRequestWrite: OWrites[Tags] = Json.writes[Tags]

  implicit val emailRequestWrites: OWrites[EmailRequest] = Json.writes[EmailRequest]

}
