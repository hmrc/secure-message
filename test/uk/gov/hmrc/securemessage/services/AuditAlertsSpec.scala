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

import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.config.AuditingConfig
import uk.gov.hmrc.play.audit.http.connector.{ AuditChannel, AuditConnector, AuditResult, DatastreamMetrics }
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.securemessage.models.v4.SecureMessage
import uk.gov.hmrc.securemessage.services.utils.MessageFixtures._
import uk.gov.hmrc.securemessage.services.utils.SecureMessageFixtures

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ ExecutionContext, Future }

class AuditAlertsSpec extends PlaySpec {

  "audit alert events" must {

    implicit val hc: HeaderCarrier = new HeaderCarrier

    "audit alert succeeded " in new AuditAlerterTest {
      //given
      val message: SecureMessage = SecureMessageFixtures.messageForSA(utr)
      val email = "me@me.com"
      val alertSucceeded: AlertSucceeded = AlertSucceeded(message, email)

      //when
      auditAlert(alertSucceeded)

      //then
      event must have(
        Symbol("auditSource")("secure-message"),
        Symbol("auditType")("TxSucceeded"),
        Symbol("tags")(
          Map(
            "transactionName" -> "Send Email Alert",
            "sautr"           -> message.recipient.identifier.toString,
            "emailAddress"    -> email
          )
        ),
        Symbol("detail")(
          Map(
            "emailTemplateName" -> message.alertDetails.templateId,
            "messageId"         -> message._id.toString,
            "sautr"             -> message.recipient.identifier.toString
          )
        )
      )
    }

    "audit alert failed " in new AuditAlerterTest {
      //given
      val message: SecureMessage = SecureMessageFixtures.messageForSA(utr)
      private val failureReason = "email not verified for unknown reason"
      val alertFailed: AlertFailed = AlertFailed(message, failureReason)

      //when
      auditAlert(alertFailed)

      //then
      event must have(
        Symbol("auditSource")("secure-message"),
        Symbol("auditType")("TxFailed"),
        Symbol("tags")(
          Map(
            "transactionName" -> "Send Email Alert",
            "sautr"           -> message.recipient.identifier.toString
          )
        ),
        Symbol("detail")(
          Map(
            "emailTemplateName" -> message.alertDetails.templateId,
            "failureReason"     -> failureReason,
            "messageId"         -> message._id.toString,
            "sautr"             -> message.recipient.identifier.toString
          )
        )
      )
    }

  }

  trait AuditAlerterTest extends AuditAlerts {

    val auditConnector = new FakeAuditConnector
    val deskProTicketSequenceNumber = 123
    var event: DataEvent = null

    class FakeAuditConnector extends AuditConnector {
      override def sendEvent(
        dataEvent: DataEvent)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[AuditResult] = {
        event = dataEvent
        Future.successful(AuditResult.Success)
      }
      override def auditingConfig: AuditingConfig = ???

      override def auditChannel: AuditChannel = ???

      override def datastreamMetrics: DatastreamMetrics = ???
    }

  }

}
