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

package uk.gov.hmrc.securemessage.services.utils

import cats.data.NonEmptyList
import org.joda.time.DateTime
import org.mockito.Mockito.verify
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.http.Status.{ ACCEPTED, INTERNAL_SERVER_ERROR, NO_CONTENT }
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.auth.core.{ Enrolment, EnrolmentIdentifier, Enrolments }
import uk.gov.hmrc.emailaddress.EmailAddress
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.EventTypes
import uk.gov.hmrc.securemessage.controllers.model.ClientName.CDCM
import uk.gov.hmrc.securemessage.controllers.model.cdcm.write.CaseworkerMessage
import uk.gov.hmrc.securemessage.controllers.model.common.write.CustomerMessage
import uk.gov.hmrc.securemessage.models.core._
import uk.gov.hmrc.securemessage.models.{ EmailRequest, QueryMessageRequest, RequestCommon, RequestDetail }
import uk.gov.hmrc.securemessage.services.Auditing

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global

class AuditingSpec extends PlaySpec with MockitoSugar with Auditing {

  override val auditConnector: AuditConnector = mock[AuditConnector]

  implicit val hc: HeaderCarrier = HeaderCarrier()

  private val conversationId = "D-80542-20210327"
  private val messageContent = "QmxhaCBibGFoIGJsYWg="

  "auditCreateConversation" must {
    val message = Message(1, DateTime.now, messageContent)
    val alert = Alert("", None)
    val conversation = Conversation(
      BSONObjectID.generate,
      CDCM.entryName,
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
      val _ = auditCreateConversation(EventTypes.Succeeded, conversation)
      verify(auditConnector).sendExplicitAudit(
        EventTypes.Succeeded,
        Map[String, String](
          "subject"        -> "MRN: DMS7324874993",
          "id"             -> "D-80542-20210327",
          "initialMessage" -> messageContent,
          "client"         -> CDCM.entryName,
          newConversationTxnName,
        )
      )
    }

    "send correct audit details when new conversation not created" in {
      val _ = auditCreateConversation(EventTypes.Failed, conversation)
      verify(auditConnector).sendExplicitAudit(
        EventTypes.Failed,
        Map[String, String](
          "subject"        -> "MRN: DMS7324874993",
          "id"             -> "D-80542-20210327",
          "initialMessage" -> messageContent,
          "client"         -> CDCM.entryName,
          newConversationTxnName,
        )
      )
    }
  }

  "auditRetrieveEmail" must {

    "send correct audit details when email retrieved" in {
      val emailAddress = Some(EmailAddress("test@test.com"))
      val _ = auditRetrieveEmail(emailAddress)
      verify(auditConnector)
        .sendExplicitAudit(EventTypes.Succeeded, Map(retrieveEmailTxnName, "email" -> "test@test.com"))
    }

    "send correct audit details when email was not retrieved" in {
      val emailAddress = None
      val _ = auditRetrieveEmail(emailAddress)
      verify(auditConnector)
        .sendExplicitAudit(EventTypes.Failed, Map(retrieveEmailTxnName))
    }
  }

  "auditCaseworkerReply" must {

    val message =
      CaseworkerMessage(messageContent)

    "send correct audit details when the casework reply is sent" in {
      val _ = auditCaseworkerReply(EventTypes.Succeeded, CDCM, conversationId, message)
      verify(auditConnector).sendExplicitAudit(
        EventTypes.Succeeded,
        Map(
          caseworkerReplyTxnName,
          "client"         -> CDCM.entryName,
          "conversationId" -> conversationId,
          "content"        -> messageContent))
    }

    "send correct audit details when the caseworker reply is not sent" in {
      val _ = auditCaseworkerReply(EventTypes.Failed, CDCM, conversationId, message)
      verify(auditConnector).sendExplicitAudit(
        EventTypes.Failed,
        Map(
          caseworkerReplyTxnName,
          "client"         -> CDCM.entryName,
          "conversationId" -> conversationId,
          "content"        -> messageContent))
    }
  }

  "auditConversationRead" must {

    val readTime = DateTime.now

    "send correct audit details when read time is recorded" in {
      val _ = auditConversationRead(
        EventTypes.Succeeded,
        CDCM,
        conversationId,
        readTime,
        Enrolments(
          Set(
            Enrolment("HMRC-CUS-ORG", Seq(EnrolmentIdentifier("EORINumber", "GB1234567890")), "")
          ))
      )
      verify(auditConnector).sendExplicitAudit(
        EventTypes.Succeeded,
        Map(
          messageReadTxnName,
          "client"         -> CDCM.entryName,
          "conversationId" -> conversationId,
          "readTime"       -> isoDtf.print(readTime),
          "enrolments"     -> "HMRC-CUS-ORG:EORINumber=GB1234567890"
        )
      )
    }

    "send correct audit details when read time is not recorded" in {
      val _ = auditConversationRead(
        EventTypes.Failed,
        CDCM,
        conversationId,
        readTime,
        Enrolments(
          Set(
            Enrolment("HMRC-CUS-ORG", Seq(EnrolmentIdentifier("EORINumber", "GB1234567890")), "")
          ))
      )
      verify(auditConnector).sendExplicitAudit(
        EventTypes.Failed,
        Map(
          messageReadTxnName,
          "client"         -> CDCM.entryName,
          "conversationId" -> conversationId,
          "readTime"       -> isoDtf.print(readTime),
          "enrolments"     -> "HMRC-CUS-ORG:EORINumber=GB1234567890"
        )
      )
    }
  }

  "auditCustomerReply" must {

    "send correct audit details when the customer reply is sent" in {
      val _ = auditCustomerReply(EventTypes.Succeeded, CDCM, conversationId, CustomerMessage(messageContent))
      verify(auditConnector).sendExplicitAudit(
        EventTypes.Succeeded,
        Map(
          customerReplyTxnName,
          "client"         -> CDCM.entryName,
          "conversationId" -> conversationId,
          "content"        -> messageContent))
    }

    "send correct audit details when the customer reply is not sent" in {
      val _ = auditCustomerReply(EventTypes.Failed, CDCM, conversationId, CustomerMessage(messageContent))
      verify(auditConnector).sendExplicitAudit(
        EventTypes.Failed,
        Map(
          customerReplyTxnName,
          "client"         -> CDCM.entryName,
          "conversationId" -> conversationId,
          "content"        -> messageContent))
    }
  }

  "auditEmailSent" must {

    val templateId = "template123"
    val emailAddress = "test@test.com"

    "send correct audit details when email sent" in {
      val _ = auditEmailSent(
        EventTypes.Succeeded,
        EmailRequest(List(EmailAddress(emailAddress)), templateId, Map("firstName" -> "Joe", "lastName" -> "Bloggs")),
        ACCEPTED)
      verify(auditConnector).sendExplicitAudit(
        EventTypes.Succeeded,
        Map(
          emailSentTxnName,
          "templateId"        -> templateId,
          "sentTo"            -> emailAddress,
          "parameters"        -> "firstName:Joe|lastName:Bloggs",
          "emailResponseCode" -> "202")
      )
    }

    "send correct audit details when email not sent" in {
      val _ = auditEmailSent(
        EventTypes.Failed,
        EmailRequest(List(EmailAddress(emailAddress)), templateId, Map("firstName" -> "Joe", "lastName" -> "Bloggs")),
        ACCEPTED)
      verify(auditConnector).sendExplicitAudit(
        EventTypes.Failed,
        Map(
          emailSentTxnName,
          "templateId"        -> templateId,
          "sentTo"            -> emailAddress,
          "parameters"        -> "firstName:Joe|lastName:Bloggs",
          "emailResponseCode" -> "202")
      )
    }
  }

  "auditMessageForwarded" must {

    val xRequestId = s"govuk-tax-${UUID.randomUUID()}"

    "send correct audit details when message forwarded" in {
      val _ = auditMessageForwarded(
        EventTypes.Succeeded,
        QueryMessageRequest(
          RequestCommon("dc-secure-message", DateTime.now(), "acknowledgementReference"),
          RequestDetail(xRequestId, conversationId, messageContent)),
        NO_CONTENT
      )
      verify(auditConnector).sendExplicitAudit(
        EventTypes.Succeeded,
        Map(
          messageForwardedTxnName,
          "eisResponseCode" -> "204",
          "conversationId"  -> conversationId,
          "x-request-id"    -> xRequestId,
          "message"         -> messageContent)
      )
    }

    "send correct audit details when message not forwarded" in {
      val _ = auditMessageForwarded(
        EventTypes.Failed,
        QueryMessageRequest(
          RequestCommon("dc-secure-message", DateTime.now(), "acknowledgementReference"),
          RequestDetail(xRequestId, conversationId, messageContent)),
        INTERNAL_SERVER_ERROR
      )
      verify(auditConnector).sendExplicitAudit(
        EventTypes.Failed,
        Map(
          messageForwardedTxnName,
          "eisResponseCode" -> "500",
          "conversationId"  -> conversationId,
          "x-request-id"    -> xRequestId,
          "message"         -> messageContent)
      )
    }
  }

}
