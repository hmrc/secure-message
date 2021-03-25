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

package uk.gov.hmrc.securemessage.services

import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import uk.gov.hmrc.auth.core.Enrolments
import uk.gov.hmrc.emailaddress.EmailAddress
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.EventTypes
import uk.gov.hmrc.securemessage.controllers.model.ClientName
import uk.gov.hmrc.securemessage.controllers.model.cdcm.write.CaseworkerMessage
import uk.gov.hmrc.securemessage.controllers.model.common.write.CustomerMessage
import uk.gov.hmrc.securemessage.models.core.Conversation
import uk.gov.hmrc.securemessage.models.{ EmailRequest, QueryResponseWrapper }

import scala.concurrent.ExecutionContext

trait Auditing {

  val auditConnector: AuditConnector

  protected val isoDtf = ISODateTimeFormat.basicDateTime()
  protected val txnName = "transactionName"
  protected val newConversationTxnName: (String, String) = txnName  -> "Create new query conversation"
  protected val retrieveEmailTxnName: (String, String) = txnName    -> "Retrieve Email Address"
  protected val emailSentTxnName: (String, String) = txnName        -> "Email Alert Sent"
  protected val caseworkerReplyTxnName: (String, String) = txnName  -> "Caseworker reply to query conversation"
  protected val customerReplyTxnName: (String, String) = txnName    -> "Customer reply to query conversation"
  protected val messageReadTxnName: (String, String) = txnName      -> "Message is Read"
  protected val messageForwardedTxnName: (String, String) = txnName -> "Message forwarded to caseworker"

  def auditCreateConversation(txnStatus: String, conversation: Conversation)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Unit = {
    val detail = Map(
      newConversationTxnName,
      "client"         -> conversation.client,
      "id"             -> conversation.id,
      "subject"        -> conversation.subject,
      "initialMessage" -> conversation.messages.head.content
    )
    auditConnector.sendExplicitAudit(txnStatus, detail)
  }
  def auditRetrieveEmail(emailAddress: Option[EmailAddress])(implicit hc: HeaderCarrier, ec: ExecutionContext): Unit =
    emailAddress match {
      case Some(email) =>
        auditConnector.sendExplicitAudit(EventTypes.Succeeded, Map(retrieveEmailTxnName, "email" -> email.value))
      case _ =>
        auditConnector.sendExplicitAudit(EventTypes.Failed, Map(retrieveEmailTxnName))
    }

  def auditEmailSent(txnStatus: String, emailRequest: EmailRequest, emailResponseCode: Int)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Unit = {
    val details = Map(
      emailSentTxnName,
      "templateId"        -> emailRequest.templateId,
      "sentTo"            -> emailRequest.to.map(_.value).mkString(","),
      "parameters"        -> emailRequest.parameters.map(_.productIterator.mkString(":")).mkString("|"),
      "emailResponseCode" -> s"$emailResponseCode"
    )
    auditConnector.sendExplicitAudit(txnStatus, details)
  }

  def auditCaseworkerReply(txnStatus: String, client: ClientName, conversationId: String, cwm: CaseworkerMessage)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Unit = {
    val detail = Map(
      caseworkerReplyTxnName,
      "client"         -> client.entryName,
      "conversationId" -> conversationId,
      "content"        -> cwm.content
    )
    auditConnector.sendExplicitAudit(txnStatus, detail)
  }

  def auditCustomerReply(
    txnStatus: String,
    client: ClientName,
    conversationId: String,
    customerMessage: CustomerMessage)(implicit hc: HeaderCarrier, ec: ExecutionContext): Unit = {
    val detail = Map(
      customerReplyTxnName,
      "client"         -> client.entryName,
      "conversationId" -> conversationId,
      "content"        -> customerMessage.content)
    auditConnector.sendExplicitAudit(txnStatus, detail)
  }

  def auditConversationRead(
    txnStatus: String,
    client: ClientName,
    conversationId: String,
    readTime: DateTime,
    enrolments: Enrolments)(implicit hc: HeaderCarrier, ec: ExecutionContext): Unit = {
    val detail = Map(
      messageReadTxnName,
      "client"         -> client.entryName,
      "conversationId" -> conversationId,
      "readTime"       -> isoDtf.print(readTime),
      "enrolments"     -> prettyPrintEnrolments(enrolments)
    )
    auditConnector.sendExplicitAudit(txnStatus, detail)
  }

  def auditMessageForwarded(txnStatus: String, qrw: QueryResponseWrapper, eisResponseCode: Int)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Unit = {
    val detail = Map(
      txnName           -> "Message forwarded to caseworker",
      "eisResponseCode" -> eisResponseCode.toString,
      "conversationId"  -> qrw.queryResponse.conversationId,
      "x-request-id"    -> qrw.queryResponse.id,
      "message"         -> qrw.queryResponse.message
    )
    auditConnector.sendExplicitAudit(txnStatus, detail)
  }

  private def prettyPrintEnrolments(enrolments: Enrolments): String =
    enrolments.enrolments
      .map { enr =>
        val key = enr.key
        val ids = enr.identifiers.map(id => s"${id.key}=${id.value}").mkString(",")
        s"$key:$ids"
      }
      .mkString(";")

}
