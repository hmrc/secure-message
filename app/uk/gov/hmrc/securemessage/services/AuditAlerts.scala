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

package uk.gov.hmrc.securemessage.services

import play.api.Logging
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.securemessage.models.v4.SecureMessage

import scala.concurrent.{ ExecutionContext, Future }

trait AuditAlerts extends Logging {
  def auditConnector: AuditConnector

  def auditAlert(alertEvent: AlertEvent)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Unit] =
    auditConnector.sendEvent(alertEvent.auditEvent).map { r =>
      logger.debug(s"AuditEvent is processed for $alertEvent with the result $r")
    }
}

object EventTypes {
  val Succeeded = "TxSucceeded"
  val Failed = "TxFailed"
}

sealed trait AlertEvent {

  def auditEvent: DataEvent

  def auditAlertEvent(
    message: SecureMessage,
    emailAddress: Option[String],
    failureReason: Option[String] = None
  ): DataEvent =
    DataEvent(
      auditSource = "secure-message",
      auditType = failureReason.fold(EventTypes.Succeeded)(_ => EventTypes.Failed),
      tags = Map(
        "transactionName"                 -> "Send Email Alert",
        message.recipient.identifier.name -> message.recipient.identifier.value
      ) ++ emailAddress.map("emailAddress" -> _).toMap,
      detail = Map(
        "emailTemplateName" -> message.templateId
      )
        ++ message.details.map("formId" -> _.formId).toMap
        ++ failureReason.map("failureReason" -> _).toMap
        ++ message.auditData
    )
}

case class AlertFailed(message: SecureMessage, failureReason: String) extends AlertEvent {
  def auditEvent: DataEvent =
    auditAlertEvent(message, None, Some(failureReason))
}

case class AlertSucceeded(message: SecureMessage, emailAddress: String) extends AlertEvent {
  def auditEvent: DataEvent =
    auditAlertEvent(message, Some(emailAddress), None)
}
