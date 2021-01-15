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
import play.api.libs.json.{ Json, OFormat }
import uk.gov.hmrc.securemessage.models.core._
import uk.gov.hmrc.time.DateTimeUtils

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

final case class System(name: String, display: String, parameters: Option[Map[String, String]])
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
  tags: Option[Map[String, String]],
  subject: String,
  message: String,
  language: Option[String])
    extends DateTimeUtils {

  def asConversation(conversationId: String): Conversation = asConversationWithCreatedDate(conversationId, now)

  def asConversationWithCreatedDate(conversationId: String, created: DateTime): Conversation = {
    val initialMessage = Message(1, created, List(Reader(1, created)), message)
    val initialParticipants = getSenderParticipant(sender, conversationId) :: getRecipientParticipants(recipients)
    Conversation(
      conversationId,
      ConversationStatus.Open,
      if (tags.isDefined) tags else None,
      subject,
      getLanguage(language),
      initialParticipants,
      List(initialMessage))
  }

  private def getLanguage(maybeLanguage: Option[String]): Language =
    maybeLanguage match {
      case Some(lang) => Language.withNameInsensitiveOption(lang).getOrElse(Language.English)
      case _          => Language.English

    }
  private def getSenderParticipant(sender: Sender, conversationId: String): Participant =
    Participant(
      1,
      ParticipantType.System,
      Identifier(sender.system.name, conversationId, None),
      Some(sender.system.display),
      None)

  private def getRecipientParticipants(recipients: List[Recipient]): List[Participant] =
    recipients.zip(Stream from 2) map { r =>
      val customer = r._1.customer
      Participant(
        r._2,
        ParticipantType.Customer,
        getCustomerIdentifier(customer.enrolment),
        customer.name,
        customer.email)
    }

  private def getCustomerIdentifier(enrolment: Enrolment): Identifier =
    Identifier(enrolment.name, enrolment.value, Some(enrolment.key))

}
object ConversationRequest {
  implicit val conversationRequestFormat: OFormat[ConversationRequest] =
    Json.format[ConversationRequest]
}
