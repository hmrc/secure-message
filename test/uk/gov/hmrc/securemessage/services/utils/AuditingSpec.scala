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

package uk.gov.hmrc.securemessage.services.utils

import cats.data.NonEmptyList

import java.time.{ Instant, LocalDate, ZoneId, ZoneOffset }
import org.mockito.Mockito.verify
import org.mongodb.scala.bson.ObjectId
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.http.Status.{ ACCEPTED, INTERNAL_SERVER_ERROR, NO_CONTENT }
import uk.gov.hmrc.auth.core.{ Enrolment, EnrolmentIdentifier, Enrolments }
import uk.gov.hmrc.common.message.emailaddress.EmailAddress
import uk.gov.hmrc.common.message.model.Language
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.EventTypes
import uk.gov.hmrc.securemessage.controllers.Auditing
import uk.gov.hmrc.securemessage.controllers.model.ClientName.CDCM
import uk.gov.hmrc.securemessage.controllers.model.cdcm.write.CaseworkerMessage
import uk.gov.hmrc.securemessage.controllers.model.cdsf.read.{ ApiLetter, SenderInformation }
import uk.gov.hmrc.securemessage.controllers.model.common.write.CustomerMessage
import uk.gov.hmrc.securemessage.models.core.*
import uk.gov.hmrc.securemessage.models.{ EmailRequest, QueryMessageRequest, RequestCommon, RequestDetail }

import java.time.format.DateTimeFormatter
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global

class AuditingSpec extends PlaySpec with MockitoSugar with Auditing {

  override val auditConnector: AuditConnector = mock[AuditConnector]

  implicit val hc: HeaderCarrier = HeaderCarrier()

  private val conversationId = "D-80542-20210327"
  private val messageContent = "QmxhaCBibGFoIGJsYWg="
  private val xRequestId = "adsgr24frfvdc829r87rfsdf=="
  private val randomId = UUID.randomUUID().toString
  private val reference = Reference("X-Request-ID", xRequestId)

  "auditCreateConversation" must {
    val message = ConversationMessage(Some(randomId), 1, Instant.now, messageContent, Some(reference))
    val alert = Alert("", None)
    val conversation = Conversation(
      new ObjectId(),
      CDCM.toString,
      "D-80542-20210327",
      ConversationStatus.Open,
      None,
      "MRN: DMS7324874993",
      Language.English,
      List(),
      NonEmptyList.one(message),
      alert
    )

    "send correct audit details when new conversation created" in {
      val responseMessage = "Conversation Created"
      val _ = auditCreateConversation(
        "CreateQueryConversationSuccess",
        conversation,
        responseMessage,
        randomId,
        Some(reference)
      )
      verify(auditConnector).sendExplicitAudit(
        "CreateQueryConversationSuccess",
        Map[String, String](
          "subject"         -> "MRN: DMS7324874993",
          "messageId"       -> "D-80542-20210327",
          "responseMessage" -> responseMessage,
          "initialMessage"  -> messageContent,
          "client"          -> CDCM.toString,
          newConversationTxnName,
          "id"           -> randomId,
          "X-request-ID" -> xRequestId
        )
      )
    }

    "send correct audit details when new conversation not created" in {
      val responseMessage = "Conversation not found for identifier: 123"
      val _ = auditCreateConversation(
        "CreateNewQueryConversationFailed",
        conversation,
        responseMessage,
        randomId,
        Some(reference)
      )
      verify(auditConnector).sendExplicitAudit(
        "CreateNewQueryConversationFailed",
        Map[String, String](
          "subject"         -> "MRN: DMS7324874993",
          "messageId"       -> "D-80542-20210327",
          "responseMessage" -> responseMessage,
          "initialMessage"  -> messageContent,
          "client"          -> CDCM.toString,
          newConversationTxnName,
          "id"           -> randomId,
          "X-request-ID" -> xRequestId
        )
      )
    }
  }

  "auditRetrieveEmail" must {

    "send correct audit details when email retrieved" in {
      val emailAddress = Some(EmailAddress("test@test.com"))
      val _ = auditRetrieveEmail(emailAddress)
      verify(auditConnector)
        .sendExplicitAudit("EmailExistsOrVerifiedSuccess", Map(retrieveEmailTxnName, "email" -> "test@test.com"))
    }

    "send correct audit details when email was not retrieved" in {
      val emailAddress = None
      val _ = auditRetrieveEmail(emailAddress)
      verify(auditConnector)
        .sendExplicitAudit("EmailExistsOrVerifiedFailed", Map(retrieveEmailTxnName))
    }
  }

  "auditCaseworkerReply" must {

    val message =
      CaseworkerMessage(messageContent)

    "send correct audit details when the casework reply is sent" in {
      val _ = auditCaseworkerReply(
        "CaseWorkerReplyToConversationSuccess",
        CDCM,
        conversationId,
        message,
        randomId,
        Some(reference)
      )
      verify(auditConnector).sendExplicitAudit(
        "CaseWorkerReplyToConversationSuccess",
        Map(
          caseworkerReplyTxnName,
          "client"       -> CDCM.toString,
          "messageId"    -> conversationId,
          "content"      -> messageContent,
          "id"           -> randomId,
          "X-request-ID" -> xRequestId
        )
      )
    }

    "send correct audit details when the caseworker reply is not sent" in {
      val _ = auditCaseworkerReply(
        "CaseWorkerReplyToConversationFailed",
        CDCM,
        conversationId,
        message,
        randomId,
        Some(reference)
      )
      verify(auditConnector).sendExplicitAudit(
        "CaseWorkerReplyToConversationFailed",
        Map(
          caseworkerReplyTxnName,
          "client"       -> CDCM.toString,
          "messageId"    -> conversationId,
          "content"      -> messageContent,
          "id"           -> randomId,
          "X-request-ID" -> xRequestId
        )
      )
    }
  }

  "auditConversationRead" must {

    "send correct audit details when read time is recorded" in {
      val _ = auditConversationRead(
        Some(CDCM),
        conversationId,
        Enrolments(
          Set(
            Enrolment("HMRC-CUS-ORG", Seq(EnrolmentIdentifier("EORINumber", "GB1234567890")), "")
          )
        )
      )
      verify(auditConnector).sendExplicitAudit(
        "QueryMessageReadSuccess",
        Map(
          conversationReadTxnName,
          "client"      -> CDCM.toString,
          "messageId"   -> conversationId,
          "messageType" -> "Conversation",
          "enrolments"  -> "HMRC-CUS-ORG:EORINumber=GB1234567890"
        )
      )
    }

    "send correct audit details when read time is not recorded" in {
      val _ = auditConversationReadFailed(
        "convId",
        Enrolments(
          Set(
            Enrolment("HMRC-CUS-ORG", Seq(EnrolmentIdentifier("EORINumber", "GB1234567890")), "")
          )
        )
      )
      verify(auditConnector).sendExplicitAudit(
        "TxFailed",
        Map(
          conversationReadTxnName,
          "messageId"   -> "convId",
          "messageType" -> "Conversation",
          "enrolments"  -> "HMRC-CUS-ORG:EORINumber=GB1234567890"
        )
      )
    }
  }

  "auditReadLetter" must {
    val readTime = Instant.now.atOffset(ZoneOffset.UTC)
    val isoDtf = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ").withZone(ZoneId.from(ZoneOffset.UTC))
    val localDate = LocalDate.now()
    val enrolments = Enrolments(
      Set(
        Enrolment("HMRC-CUS-ORG", Seq(EnrolmentIdentifier("EORINumber", "GB1234567890")), "")
      )
    )

    "send correct audit details when letter is read" in {
      val _ = auditReadLetter(
        ApiLetter(
          "subject",
          "content",
          None,
          SenderInformation("sender", localDate),
          Identifier("id", "value", None),
          Some(readTime.toInstant),
          None
        ),
        enrolments
      )

      verify(auditConnector).sendExplicitAudit(
        "TxSucceeded",
        Map(
          letterReadSuccessTxnName,
          "id"          -> "value",
          "subject"     -> "subject",
          "readTime"    -> isoDtf.format(readTime.toInstant.atOffset(ZoneOffset.UTC)),
          "messageType" -> "Letter",
          "enrolments"  -> "HMRC-CUS-ORG:EORINumber=GB1234567890"
        )
      )
    }

    "send correct audit details with notification type when letter is read" in {
      val _ = auditReadLetter(
        ApiLetter(
          "subject",
          "content",
          None,
          SenderInformation("sender", localDate),
          Identifier("id", "value", None),
          Some(readTime.toInstant),
          Some(Map("notificationType" -> "Direct Debit"))
        ),
        enrolments
      )

      verify(auditConnector).sendExplicitAudit(
        "TxSucceeded",
        Map(
          letterReadSuccessTxnName,
          "id"               -> "value",
          "subject"          -> "subject",
          "readTime"         -> isoDtf.format(readTime.toInstant.atOffset(ZoneOffset.UTC)),
          "messageType"      -> "Letter",
          "enrolments"       -> "HMRC-CUS-ORG:EORINumber=GB1234567890",
          "notificationType" -> "Direct Debit"
        )
      )
    }

    "send correct audit details when there is a error reading the letter" in {
      val _ = auditReadLetterFail("someId", enrolments)

      verify(auditConnector).sendExplicitAudit(
        "TxFailed",
        Map(
          letterReadFailedTxnName,
          "messageId"   -> "someId",
          "messageType" -> "Letter",
          "enrolments"  -> "HMRC-CUS-ORG:EORINumber=GB1234567890"
        )
      )
    }

  }

  "auditCustomerReply" must {

    "send correct audit details when the customer reply is sent" in {
      val _ =
        auditCustomerReply(
          "CustomerReplyToConversationSuccess",
          conversationId,
          Some(CustomerMessage(messageContent)),
          randomId,
          Some(reference)
        )
      verify(auditConnector).sendExplicitAudit(
        "CustomerReplyToConversationSuccess",
        Map(
          customerReplyTxnName,
          "encodedId"    -> conversationId,
          "content"      -> messageContent,
          "id"           -> randomId,
          "X-request-ID" -> xRequestId
        )
      )
    }

    "send correct audit details when the customer reply is not sent" in {
      val _ =
        auditCustomerReply(
          "CustomerReplyToConversationFailed",
          conversationId,
          Some(CustomerMessage(messageContent)),
          randomId,
          Some(reference)
        )
      verify(auditConnector).sendExplicitAudit(
        "CustomerReplyToConversationFailed",
        Map(
          customerReplyTxnName,
          "encodedId"    -> conversationId,
          "content"      -> messageContent,
          "id"           -> randomId,
          "X-request-ID" -> xRequestId
        )
      )
    }
  }

  "auditEmailSent" must {

    val templateId = "template123"
    val emailAddress = "test@test.com"

    "send correct audit details when email sent" in {
      val _ = auditEmailSent(
        EventTypes.Succeeded,
        EmailRequest(
          List(EmailAddress(emailAddress)),
          templateId,
          Map("firstName" -> "Joe", "lastName" -> "Bloggs"),
          None
        ),
        ACCEPTED
      )
      verify(auditConnector).sendExplicitAudit(
        EventTypes.Succeeded,
        Map(
          emailSentTxnName,
          "templateId"        -> templateId,
          "sentTo"            -> emailAddress,
          "parameters"        -> "firstName:Joe|lastName:Bloggs",
          "emailResponseCode" -> "202"
        )
      )
    }

    "send correct audit details when email not sent" in {
      val _ = auditEmailSent(
        EventTypes.Failed,
        EmailRequest(
          List(EmailAddress(emailAddress)),
          templateId,
          Map("firstName" -> "Joe", "lastName" -> "Bloggs"),
          None
        ),
        ACCEPTED
      )
      verify(auditConnector).sendExplicitAudit(
        EventTypes.Failed,
        Map(
          emailSentTxnName,
          "templateId"        -> templateId,
          "sentTo"            -> emailAddress,
          "parameters"        -> "firstName:Joe|lastName:Bloggs",
          "emailResponseCode" -> "202"
        )
      )
    }
  }

  "auditMessageForwarded" must {

    val xRequestId = s"govuk-tax-${UUID.randomUUID()}"

    "send correct audit details when message forwarded" in {
      val _ = auditMessageForwarded(
        "MessageForwardedToCaseworkerSuccess",
        QueryMessageRequest(
          RequestCommon("dc-secure-message", Instant.now(), "acknowledgementReference"),
          RequestDetail(xRequestId, conversationId, messageContent)
        ),
        NO_CONTENT
      )
      verify(auditConnector).sendExplicitAudit(
        "MessageForwardedToCaseworkerSuccess",
        Map(
          messageForwardedTxnName,
          "eisResponseCode"          -> "204",
          "messageId"                -> conversationId,
          "x-request-id"             -> xRequestId,
          "message"                  -> messageContent,
          "acknowledgementReference" -> "acknowledgementReference"
        )
      )
    }

    "send correct audit details when message not forwarded" in {
      val _ = auditMessageForwarded(
        "MessageForwardedToCaseworkerFailed",
        QueryMessageRequest(
          RequestCommon("dc-secure-message", Instant.now(), "acknowledgementReference"),
          RequestDetail(xRequestId, conversationId, messageContent)
        ),
        INTERNAL_SERVER_ERROR
      )
      verify(auditConnector).sendExplicitAudit(
        "MessageForwardedToCaseworkerFailed",
        Map(
          messageForwardedTxnName,
          "eisResponseCode"          -> "500",
          "messageId"                -> conversationId,
          "x-request-id"             -> xRequestId,
          "message"                  -> messageContent,
          "acknowledgementReference" -> "acknowledgementReference"
        )
      )
    }
  }

}
