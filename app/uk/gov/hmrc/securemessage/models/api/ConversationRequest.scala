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

package uk.gov.hmrc.securemessage.models.api

import play.api.libs.json.{ Json, OFormat }

final case class Alert(templateId: String, parameters: Option[Map[String, String]])
object Alert {
  implicit val alertFormat: OFormat[Alert] =
    Json.format[Alert]
}

final case class Enrolment(key: String, name: String, value: String)
object Enrolment {
  implicit val enrolmentFormat: OFormat[Enrolment] =
    Json.format[Enrolment]
}

final case class System(name: String, parameters: Map[String, String], display: String)
object System {
  implicit val systemFormat: OFormat[System] =
    Json.format[System]
}

final case class Customer(enrolment: Enrolment, name: Option[String], email: Option[String])
object Customer {
  implicit val customerFormat: OFormat[Customer] =
    Json.format[Customer]
}

final case class Sender(system: System)
object Sender {
  implicit val senderFormat: OFormat[Sender] =
    Json.format[Sender]
}

final case class Recipient(customer: Customer)
object Recipient {
  implicit val recipientFormat: OFormat[Recipient] =
    Json.format[Recipient]
}

final case class ConversationRequest(
  sender: Sender,
  recipients: List[Recipient],
  alert: Alert,
  tags: Map[String, String],
  subject: String,
  message: String,
  language: Option[String])
object ConversationRequest {
  implicit val conversationRequestFormat: OFormat[ConversationRequest] =
    Json.format[ConversationRequest]
}
