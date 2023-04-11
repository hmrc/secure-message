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

package uk.gov.hmrc.securemessage.controllers

import org.joda.time.format.ISODateTimeFormat
import org.joda.time.{ DateTime, DateTimeZone }
import uk.gov.hmrc.auth.core.Enrolments
import uk.gov.hmrc.common.message.emailaddress.EmailAddress
import uk.gov.hmrc.common.message.model.TaxEntity
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.securemessage.SecureMessageError
import uk.gov.hmrc.securemessage.controllers.model.cdcm.read.ApiConversation
import uk.gov.hmrc.securemessage.controllers.model.cdcm.write.CaseworkerMessage
import uk.gov.hmrc.securemessage.controllers.model.cdsf.read.ApiLetter
import uk.gov.hmrc.securemessage.controllers.model.common.write.CustomerMessage
import uk.gov.hmrc.securemessage.controllers.model.{ ApiMessage, ClientName, MessageResourceResponse, MessageType }
import uk.gov.hmrc.securemessage.controllers.utils.IdCoder
import uk.gov.hmrc.securemessage.models.core.{ Conversation, Reference }
import uk.gov.hmrc.securemessage.models.v4.SecureMessage
import uk.gov.hmrc.securemessage.models.{ EmailRequest, QueryMessageRequest }

import scala.concurrent.ExecutionContext

trait Auditing {

  def auditConnector: AuditConnector

  protected val isoDtf = ISODateTimeFormat.basicDateTime()
  protected val txnName = "transactionName"
  protected val newConversationTxnName: (String, String) = txnName   -> "Create new query conversation"
  protected val retrieveEmailTxnName: (String, String) = txnName     -> "Retrieve Email Address"
  protected val emailSentTxnName: (String, String) = txnName         -> "Email Alert Sent"
  protected val caseworkerReplyTxnName: (String, String) = txnName   -> "Caseworker reply to query conversation"
  protected val customerReplyTxnName: (String, String) = txnName     -> "Customer reply to query conversation"
  protected val conversationReadTxnName: (String, String) = txnName  -> "Message is Read"
  protected val letterReadSuccessTxnName: (String, String) = txnName -> "Message is Read"
  protected val letterReadFailedTxnName: (String, String) = txnName  -> "Message not Read"
  protected val messageForwardedTxnName: (String, String) = txnName  -> "Message forwarded to caseworker"

  private val NotificationType = "notificationType"

  private def detailWithNotificationType(
    detail: Map[String, String],
    tags: Option[Map[String, String]]): Map[String, String] =
    detail ++ (for {
      m <- tags
      v <- m.get(NotificationType)
    } yield (NotificationType, v)).toMap

  def auditCreateConversation(
    txnStatus: String,
    conversation: Conversation,
    responseMessage: String,
    id: String,
    maybeReference: Option[Reference])(implicit hc: HeaderCarrier, ec: ExecutionContext): Unit = {
    val detail = detailWithNotificationType(
      Map(
        newConversationTxnName,
        "client"          -> conversation.client,
        "messageId"       -> conversation.id,
        "subject"         -> conversation.subject,
        "initialMessage"  -> conversation.messages.head.content,
        "responseMessage" -> responseMessage,
        "id"              -> id,
        "X-request-ID"    -> maybeReference.map(_.value).get
      ),
      conversation.tags
    )
    auditConnector.sendExplicitAudit(txnStatus, detail)
  }

  def auditRetrieveEmail(emailAddress: Option[EmailAddress])(implicit hc: HeaderCarrier, ec: ExecutionContext): Unit =
    emailAddress match {
      case Some(email) =>
        auditConnector
          .sendExplicitAudit("EmailExistsOrVerifiedSuccess", Map(retrieveEmailTxnName, "email" -> email.value))
      case _ =>
        auditConnector.sendExplicitAudit("EmailExistsOrVerifiedFailed", Map(retrieveEmailTxnName))
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

  def auditCaseworkerReply(
    txnStatus: String,
    client: ClientName,
    conversationId: String,
    cwm: CaseworkerMessage,
    id: String,
    maybeReference: Option[Reference])(implicit hc: HeaderCarrier, ec: ExecutionContext): Unit = {
    val detail = Map(
      caseworkerReplyTxnName,
      "client"       -> client.entryName,
      "messageId"    -> conversationId,
      "content"      -> cwm.content,
      "id"           -> id,
      "X-request-ID" -> maybeReference.map(_.value).get
    )
    auditConnector.sendExplicitAudit(txnStatus, detail)
  }

  def auditCustomerReply(
    txnStatus: String,
    encodedId: String,
    customerMessage: Option[CustomerMessage],
    id: String,
    maybeReference: Option[Reference])(implicit hc: HeaderCarrier, ec: ExecutionContext): Unit = {
    val detail =
      Map(
        customerReplyTxnName,
        "encodedId"    -> encodedId,
        "content"      -> customerMessage.map(_.content).getOrElse(""),
        "id"           -> id,
        "X-request-ID" -> maybeReference.map(_.value).get
      )
    auditConnector.sendExplicitAudit(txnStatus, detail)
  }

  /** TOOD: combine in this function [[auditConversationRead()]] && [[auditReadLetter()]]
    * */
  def auditMessageRead(apiMessage: ApiMessage, enrolments: Enrolments)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Unit =
    apiMessage match {
      case l: ApiLetter => auditReadLetter(l, enrolments)
      case c: ApiConversation =>
        auditConversationRead(ClientName.withNameOption(c.client), c.conversationId, enrolments)
      case mr: MessageResourceResponse => auditMessageResourceResponse(mr, enrolments)
    }

  private val QueryMessageReadSuccess = "QueryMessageReadSuccess"
  private val ConversationMessageType = ("messageType", "Conversation")

  /** TOOD: replace with with the common [[auditMessageRead()]]
    * */
  def auditConversationRead(client: Option[ClientName], conversationId: String, enrolments: Enrolments)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Unit = {
    val detail = Map(
      conversationReadTxnName,
      "client"    -> client.fold("")(_.entryName),
      "messageId" -> conversationId,
      ConversationMessageType,
      "enrolments" -> prettyPrintEnrolments(enrolments)
    )
    auditConnector.sendExplicitAudit(QueryMessageReadSuccess, detail)
  }

  private val letterMessageType = ("messageType", "Letter")
  private val LetterReadSuccess = "LetterReadSuccess"
  private val LetterReadFailed = "LetterReadFailed"
  private val zone: DateTimeZone = DateTimeZone.UTC

  /** TOOD: replace with with the common [[auditMessageRead()]]
    * */
  def auditReadLetter(letter: ApiLetter, enrolments: Enrolments)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Unit = {
    val detail = detailWithNotificationType(
      Map(
        letterReadSuccessTxnName,
        letter.identifier.name -> letter.identifier.value,
        "subject"              -> letter.subject,
        "readTime"             -> isoDtf.print(letter.readTime.getOrElse(DateTime.now.withZone(zone))),
        letterMessageType,
        "enrolments" -> prettyPrintEnrolments(enrolments)
      ),
      letter.tags
    )
    auditConnector.sendExplicitAudit(LetterReadSuccess, detail)
  }

  def auditMessageResourceResponse(mr: MessageResourceResponse, enrolments: Enrolments)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Unit = {
    val detail = detailWithNotificationType(
      Map(
        letterReadSuccessTxnName,
        "subject"  -> mr.subject,
        "readTime" -> isoDtf.print(mr.readTime.getOrElse(DateTime.now.withZone(zone))),
        letterMessageType,
        "enrolments" -> prettyPrintEnrolments(enrolments)
      ),
      None
    )
    auditConnector.sendExplicitAudit(LetterReadSuccess, detail)
  }

  private val ConversationReadFailed = "QueryMessageReadFailed"

  /** TOOD: replace with with the common [[auditMessageReadFailed()]]
    * */
  def auditConversationReadFailed(id: String, enrolments: Enrolments)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Unit = {
    val detail = Map(
      conversationReadTxnName,
      "messageId" -> id,
      ConversationMessageType,
      "enrolments" -> prettyPrintEnrolments(enrolments)
    )
    auditConnector.sendExplicitAudit(ConversationReadFailed, detail)
  }

  /** TOOD: replace with with the common [[auditMessageReadFailed()]]
    * */
  def auditReadLetterFail(id: String, enrolments: Enrolments)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Unit = {
    val detail = Map(
      letterReadFailedTxnName,
      "messageId" -> id,
      letterMessageType,
      "enrolments" -> prettyPrintEnrolments(enrolments)
    )
    auditConnector.sendExplicitAudit(LetterReadFailed, detail)
  }

  def auditMessageReadFailed(encodedId: String, error: SecureMessageError)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Unit = {
    val (messageType, decodedId, txName, queryMessage) = IdCoder.decodeId(encodedId) match {
      case Right((messageType, decodedId, _)) =>
        messageType match {
          case MessageType.Conversation =>
            (messageType.entryName, decodedId, conversationReadTxnName, ConversationReadFailed)
          case MessageType.Letter => (messageType.entryName, decodedId, letterReadFailedTxnName, LetterReadFailed)
        }
      case Left(_) => ("message", encodedId, conversationReadTxnName, ConversationReadFailed)
    }
    val detail = Map(
      txName,
      "messageId"   -> decodedId,
      "messageType" -> messageType,
      "encodedId"   -> encodedId,
      "error"       -> error.message
    )
    auditConnector.sendExplicitAudit(queryMessage, detail)
  }

  def auditMessageForwarded(txnStatus: String, qrw: QueryMessageRequest, eisResponseCode: Int)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Unit = {
    val detail = Map(
      txnName                    -> "Message forwarded to caseworker",
      "eisResponseCode"          -> eisResponseCode.toString,
      "messageId"                -> qrw.requestDetail.conversationId,
      "x-request-id"             -> qrw.requestDetail.id,
      "acknowledgementReference" -> qrw.requestCommon.acknowledgementReference,
      "message"                  -> qrw.requestDetail.message
    )
    auditConnector.sendExplicitAudit(txnStatus, detail)
  }

  def auditUpdatedMessageFor(m: SecureMessage, transactionName: String)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Unit = {
    val formId: Map[String, String] = m.details.map("formId" -> _.formId).toMap
    val detail: Map[String, String] = Map(
      "messageId"   -> m._id.toString,
      "source"      -> m.externalRef.source,
      "templateId"  -> m.alertDetails.templateId,
      "messageType" -> m.messageType) ++
      formId ++
      TaxEntity.forAudit(m.recipient)
    auditConnector.sendExplicitAudit(transactionName, detail)
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
