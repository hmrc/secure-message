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

import java.text.ParseException

import akka.stream.Materializer
import cats.data.NonEmptyList
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{ times, verify, when }
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.test.Helpers._
import play.api.test.NoMaterializer
import uk.gov.hmrc.auth.core.{ AuthorisationException, Enrolment, EnrolmentIdentifier, Enrolments }
import uk.gov.hmrc.emailaddress.EmailAddress
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.securemessage.connectors.EmailConnector
import uk.gov.hmrc.securemessage.controllers.models.generic
import uk.gov.hmrc.securemessage.controllers.models.generic._
import uk.gov.hmrc.securemessage.helpers.ConversationUtil
import uk.gov.hmrc.securemessage.models.EmailRequest
import uk.gov.hmrc.securemessage.models.core._
import uk.gov.hmrc.securemessage.repository.ConversationRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ ExecutionContext, Future }

@SuppressWarnings(Array("org.wartremover.warts.All"))
class SecureMessageServiceSpec extends PlaySpec with ScalaFutures with MockitoSugar {

  implicit val mat: Materializer = NoMaterializer
  val listOfCoreConversation = List(
    ConversationUtil.getFullConversation("D-80542-20201120", "HMRC-CUS-ORG", "EORINumber", "GB1234567890"))

  implicit val hc: HeaderCarrier = HeaderCarrier()

  "createConversation" must {

    "return CREATED (201) when an email address is provided" in new TestCase {
      when(mockRepository.insertIfUnique(any[Conversation])(any[ExecutionContext])).thenReturn(Future(true))
      private val result =
        service.createConversation(ConversationUtil.getConversationRequest(true, "QmxhaCBibGFoIGJsYWg="), "cdcm", "123")
      status(result) mustBe CREATED
    }
    "return BAD REQUEST (400) when no email address is provided" in new TestCase {
      when(mockRepository.insertIfUnique(any[Conversation])(any[ExecutionContext])).thenReturn(Future(true))
      private val result =
        service.createConversation(ConversationUtil.getConversationRequest(false, "base64 content"), "cdcm", "123")
      status(result) mustBe BAD_REQUEST
    }
    "return CONFLICT (409) when a conversation already exists for this client and conversation ID" in new TestCase {
      when(mockRepository.insertIfUnique(any[Conversation])(any[ExecutionContext])).thenReturn(Future(false))
      private val result =
        service.createConversation(ConversationUtil.getConversationRequest(true, "base64 content"), "cdcm", "123")
      status(result) mustBe CONFLICT
    }

    "return BAD REQUEST (400) if message content is not Base64 encoded" in new TestCase {
      when(mockRepository.insertIfUnique(any[Conversation])(any[ExecutionContext])).thenReturn(Future(true))
      private val result = service
        .createConversation(ConversationUtil.getConversationRequest(false, "aGV%sb-G8sIHdvcmxkIQ=="), "cdcm", "123")
      status(result) mustBe BAD_REQUEST
      contentAsString(result) mustBe "Not valid base64 content"
    }

    "return BAD REQUEST (400) if message content is empty" in new TestCase {
      when(mockRepository.insertIfUnique(any[Conversation])(any[ExecutionContext])).thenReturn(Future(true))
      private val result = service.createConversation(ConversationUtil.getConversationRequest(false, ""), "cdcm", "123")
      status(result) mustBe BAD_REQUEST
    }

    "return BAD REQUEST (400)if message content is not valid HTML" in new TestCase {
      when(mockRepository.insertIfUnique(any[Conversation])(any[ExecutionContext])).thenReturn(Future(true))
      private val result = service.createConversation(
        ConversationUtil.getConversationRequest(false, "PG1hdHQ+Q2FuIEkgaGF2ZSBteSB0YXggbW9uZXkgcGxlYXNlPzwvbWF0dD4="),
        "cdcm",
        "123")
      status(result) mustBe BAD_REQUEST
      contentAsString(result) mustBe "Not valid HTML content"
    }
  }

  "getConversations" must {

    "return a list of ConversationMetaData" in new TestCase {
      private val listOfCoreConversation =
        List(ConversationUtil.getFullConversation("D-80542-20201120", "HMRC-CUS-ORG", "EORINumber", "GB1234567890"))
      when(mockRepository.getConversations(any[generic.CustomerEnrolment])(any[ExecutionContext]))
        .thenReturn(Future.successful(listOfCoreConversation))
      private val result =
        await(service.getConversations(CustomerEnrolment("HMRC-CUS_ORG", "EORIName", "GB7777777777")))
      result mustBe
        List(
          ConversationMetadata(
            "cdcm",
            "D-80542-20201120",
            "MRN: 19GB4S24GC3PPFGVR7",
            DateTime.parse("2020-11-10T15:00:01.000"),
            Some("CDS Exports Team"),
            unreadMessages = false,
            1))
    }
  }

  "getConversationsFiltered" must {

    "return a list of ConversationMetaData when presented with one customer enrolment and no tags for a filter" in new TestCase {
      private val listOfCoreConversation =
        List(ConversationUtil.getFullConversation("D-80542-20201120", "HMRC-CUS-ORG", "EORINumber", "GB1234567890"))
      when(
        mockRepository.getConversationsFiltered(
          ArgumentMatchers.eq(Set(CustomerEnrolment("HMRC-CUS_ORG", "EORIName", "GB7777777777"))),
          ArgumentMatchers.eq(None))(any[ExecutionContext]))
        .thenReturn(Future.successful(listOfCoreConversation))
      private val result = await(
        service.getConversationsFiltered(Set(CustomerEnrolment("HMRC-CUS_ORG", "EORIName", "GB7777777777")), None))
      private val metadata: ConversationMetadata = ConversationMetadata(
        "cdcm",
        "D-80542-20201120",
        "MRN: 19GB4S24GC3PPFGVR7",
        DateTime.parse("2020-11-10T15:00:01.000"),
        Some("CDS Exports Team"),
        unreadMessages = false,
        1)
      result mustBe
        List(metadata)
    }

    "return a list of ConversationMetaData when presented with one customer enrolment and one tag for a filter" in new TestCase {
      private val listOfCoreConversation =
        List(ConversationUtil.getFullConversation("D-80542-20201120", "HMRC-CUS-ORG", "EORINumber", "GB1234567890"))
      when(
        mockRepository.getConversationsFiltered(
          ArgumentMatchers.eq(Set(CustomerEnrolment("HMRC-CUS_ORG", "EORIName", "GB7777777777"))),
          ArgumentMatchers.eq(Some(List(Tag("notificationType", "CDS Exports"))))
        )(any[ExecutionContext]))
        .thenReturn(Future.successful(listOfCoreConversation))
      private val result = await(
        service.getConversationsFiltered(
          Set(CustomerEnrolment("HMRC-CUS_ORG", "EORIName", "GB7777777777")),
          Some(List(Tag("notificationType", "CDS Exports")))))
      result mustBe
        List(
          ConversationMetadata(
            "cdcm",
            "D-80542-20201120",
            "MRN: 19GB4S24GC3PPFGVR7",
            DateTime.parse("2020-11-10T15:00:01.000"),
            Some("CDS Exports Team"),
            unreadMessages = false,
            1))
    }
  }

  "getConversation" must {

    "return a message with ApiConversation" in new TestCase {
      when(
        mockRepository.getConversation(any[String], any[String], any[generic.CustomerEnrolment])(any[ExecutionContext]))
        .thenReturn(Future.successful(
          Some(ConversationUtil.getFullConversation("D-80542-20201120", "HMRC-CUS-ORG", "EORINumber", "GB1234567890"))))
      private val result = await(
        service
          .getConversation("cdcm", "D-80542-20201120", CustomerEnrolment("HMRC-CUS_ORG", "EORIName", "GB7777777777")))
      result.get.client mustBe "cdcm"
      result.get.messages.size mustBe 1
      result.get.subject mustBe "MRN: 19GB4S24GC3PPFGVR7"
    }

    "return a None" in new TestCase {
      when(
        mockRepository.getConversation(any[String], any[String], any[generic.CustomerEnrolment])(any[ExecutionContext]))
        .thenReturn(Future.successful(None))
      private val result = await(
        service
          .getConversation("cdcm", "D-80542-20201120", CustomerEnrolment("HMRC-CUS_ORG", "EORIName", "GB7777777777")))
      result mustBe None
    }
  }

  "Adding a customer message to a conversation" must {

    "update the database when the customer has a participating enrolment" in new TestCase {
      private val customerMessage = CustomerMessageRequest("PGRpdj5IZWxsbzwvZGl2Pg==")
      when(mockRepository.conversationExists(any[String], any[String])(any[ExecutionContext])).thenReturn(Future(true))
      when(mockEnrolments.enrolments)
        .thenReturn(Set(Enrolment("HMRC-CUS-ORG", Seq(EnrolmentIdentifier("EORINumber", "GB123456789000000")), "")))
      when(mockRepository.getConversationParticipants(any[String], any[String])(any[ExecutionContext]))
        .thenReturn(Future(Some(Participants(NonEmptyList(participant, List(customerParticipant))))))
      when(mockRepository.addMessageToConversation(any[String], any[String], any[Message])(any[ExecutionContext]))
        .thenReturn(Future.successful(true))
      await(service.addCustomerMessageToConversation("cdcm", "D-80542-20201120", customerMessage, mockEnrolments))
      verify(mockRepository, times(1))
        .addMessageToConversation(any[String], any[String], any[Message])(any[ExecutionContext])
    }

    "throw an AuthorisationException if the customer does not have a participating enrolment" in new TestCase {
      private val customerMessage = CustomerMessageRequest("PGRpdj5IZWxsbzwvZGl2Pg==")
      when(mockRepository.conversationExists(any[String], any[String])(any[ExecutionContext]))
        .thenReturn(Future.successful(true))
      when(mockEnrolments.enrolments)
        .thenReturn(Set(Enrolment("HMRC-CUS-ORG", Seq(EnrolmentIdentifier("EORINumber", "GB123456789000001")), "")))
      when(mockRepository.getConversationParticipants(any[String], any[String])(any[ExecutionContext]))
        .thenReturn(Future(Some(Participants(NonEmptyList.one(participant)))))
      assertThrows[AuthorisationException] {
        await(service.addCustomerMessageToConversation("cdcm", "D-80542-20201120", customerMessage, mockEnrolments))
      }
    }

    "throw an IllegalArgumentException if the conversation ID is not found" in new TestCase {
      private val customerMessage = CustomerMessageRequest("PGRpdj5IZWxsbzwvZGl2Pg==")
      when(mockRepository.conversationExists(any[String], any[String])(any[ExecutionContext]))
        .thenReturn(Future.successful(false))
      assertThrows[IllegalArgumentException] {
        await(service.addCustomerMessageToConversation("cdcm", "D-80542-20201120", customerMessage, mockEnrolments))
      }
    }

    "throw an Exception if message content is not Base64 encoded" in new TestCase {
      private val customerMessage = CustomerMessageRequest("aGV%sb-G8sIHdvcmxkIQ==")
      when(mockRepository.conversationExists(any[String], any[String])(any[ExecutionContext])).thenReturn(Future(true))
      when(mockRepository.getConversationParticipants(any[String], any[String])(any[ExecutionContext]))
        .thenReturn(Future(None))
      assertThrows[ParseException] {
        await(service.addCustomerMessageToConversation("cdcm", "D-80542-20201120", customerMessage, mockEnrolments))
      }
    }

    "throw an Exception if message content is not valid HTML" in new TestCase {
      private val customerMessage =
        CustomerMessageRequest("PG1hdHQ+Q2FuIEkgaGF2ZSBteSB0YXggbW9uZXkgcGxlYXNlPzwvbWF0dD4=")
      when(mockRepository.conversationExists(any[String], any[String])(any[ExecutionContext])).thenReturn(Future(true))
      when(mockRepository.getConversationParticipants(any[String], any[String])(any[ExecutionContext]))
        .thenReturn(Future(None))
      assertThrows[ParseException] {
        await(service.addCustomerMessageToConversation("cdcm", "D-80542-20201120", customerMessage, mockEnrolments))
      }
    }

    "throw an Exception if message content is an empty string" in new TestCase {
      private val customerMessage = CustomerMessageRequest("")
      when(mockRepository.conversationExists(any[String], any[String])(any[ExecutionContext])).thenReturn(Future(true))
      when(mockRepository.getConversationParticipants(any[String], any[String])(any[ExecutionContext]))
        .thenReturn(Future(None))
      assertThrows[ParseException] {
        await(service.addCustomerMessageToConversation("cdcm", "D-80542-20201120", customerMessage, mockEnrolments))
      }
    }
  }

  "Adding a caseworker message to a conversation" must {

    "update the database and send a nudge email when a caseworker message has successfully been added to the db" in new TestCase {
      private val caseworkerMessage = CaseworkerMessageRequest(
        CaseworkerMessageRequest.Sender(
          CaseworkerMessageRequest.System(CaseworkerMessageRequest.SystemIdentifier("cdcm", "D-80542-20201120"))),
        "PHA+Q2FuIEkgaGF2ZSBteSB0YXggbW9uZXkgcGxlYXNlPzwvcD4="
      )
      when(mockRepository.conversationExists(any[String], any[String])(any[ExecutionContext])).thenReturn(Future(true))
      when(mockRepository.getConversationParticipants(any[String], any[String])(any[ExecutionContext])).thenReturn(
        Future(Some(Participants(NonEmptyList(
          Participant(1, ParticipantType.System, Identifier("cdcm", "D-80542-20201120", None), None, None, None, None),
          List(
            Participant(
              2,
              ParticipantType.Customer,
              Identifier("EORINumber", "GB123456789000000", Some("HMRC-CUS-ORG")),
              None,
              Some(EmailAddress("test@test.com")),
              None,
              None))
        )))))
      when(mockRepository.addMessageToConversation(any[String], any[String], any[Message])(any[ExecutionContext]))
        .thenReturn(Future.successful(true))
      when(mockEmailConnector.send(any[EmailRequest])(any[HeaderCarrier])).thenReturn(Future.successful(()))
      await(service.addCaseWorkerMessageToConversation("cdcm", "D-80542-20201120", caseworkerMessage))
      verify(mockRepository, times(1))
        .addMessageToConversation(any[String], any[String], any[Message])(any[ExecutionContext])
    }

    "throw an AuthorisationException if the caseworker does not have a participating enrolment" in new TestCase {
      private val caseworkerMessage = CaseworkerMessageRequest(
        CaseworkerMessageRequest.Sender(
          CaseworkerMessageRequest.System(CaseworkerMessageRequest.SystemIdentifier("cdcm", "D-80542-20201120"))),
        "PHA+Q2FuIEkgaGF2ZSBteSB0YXggbW9uZXkgcGxlYXNlPzwvcD4=="
      )
      when(mockRepository.conversationExists(any[String], any[String])(any[ExecutionContext])).thenReturn(Future(true))
      when(mockRepository.getConversationParticipants(any[String], any[String])(any[ExecutionContext]))
        .thenReturn(Future(None))
      assertThrows[AuthorisationException] {
        await(service.addCaseWorkerMessageToConversation("cdcm", "D-80542-20201120", caseworkerMessage))
      }
    }

    "throw an IllegalArgumentException if the conversation ID is not found" in new TestCase {
      private val caseworkerMessage = CaseworkerMessageRequest(
        CaseworkerMessageRequest.Sender(
          CaseworkerMessageRequest.System(CaseworkerMessageRequest.SystemIdentifier("cdcm", "D-80542-20201120"))),
        "QmxhaCBibGFoIGJsYWg="
      )
      when(mockRepository.conversationExists(any[String], any[String])(any[ExecutionContext])).thenReturn(Future(false))
      assertThrows[IllegalArgumentException] {
        await(service.addCaseWorkerMessageToConversation("cdcm", "D-80542-20201120", caseworkerMessage))
      }
    }

    "throw an Exception if message content is valid Base64 encoded" in new TestCase {
      private val caseworkerMessage = CaseworkerMessageRequest(
        CaseworkerMessageRequest.Sender(
          CaseworkerMessageRequest.System(CaseworkerMessageRequest.SystemIdentifier("cdcm", "D-80542-20201120"))),
        "aGV%sb-G8sIHdvcmxkIQ=="
      )
      when(mockRepository.conversationExists(any[String], any[String])(any[ExecutionContext])).thenReturn(Future(true))
      when(mockRepository.getConversationParticipants(any[String], any[String])(any[ExecutionContext]))
        .thenReturn(Future(None))
      assertThrows[ParseException] {
        await(service.addCaseWorkerMessageToConversation("cdcm", "D-80542-20201120", caseworkerMessage))
      }
    }

    "throw an Exception if message content is not valid HTML" in new TestCase {
      private val caseworkerMessage = CaseworkerMessageRequest(
        CaseworkerMessageRequest.Sender(
          CaseworkerMessageRequest.System(CaseworkerMessageRequest.SystemIdentifier("cdcm", "D-80542-20201120"))),
        "PG1hdHQ+Q2FuIEkgaGF2ZSBteSB0YXggbW9uZXkgcGxlYXNlPzwvbWF0dD4="
      )
      when(mockRepository.conversationExists(any[String], any[String])(any[ExecutionContext])).thenReturn(Future(true))
      when(mockRepository.getConversationParticipants(any[String], any[String])(any[ExecutionContext]))
        .thenReturn(Future(None))
      assertThrows[ParseException] {
        await(service.addCaseWorkerMessageToConversation("cdcm", "D-80542-20201120", caseworkerMessage))
      }
    }

    "throw an Exception if message content is an empty string" in new TestCase {
      private val caseworkerMessage = CaseworkerMessageRequest(
        CaseworkerMessageRequest.Sender(
          CaseworkerMessageRequest.System(CaseworkerMessageRequest.SystemIdentifier("cdcm", "D-80542-20201120"))),
        "")
      when(mockRepository.conversationExists(any[String], any[String])(any[ExecutionContext])).thenReturn(Future(true))
      when(mockRepository.getConversationParticipants(any[String], any[String])(any[ExecutionContext]))
        .thenReturn(Future(None))
      assertThrows[ParseException] {
        await(service.addCaseWorkerMessageToConversation("cdcm", "D-80542-20201120", caseworkerMessage))
      }
    }
  }

  "updateReadTime" should {

    "return true when a readTime has been added to the db" in new TestCase {
      when(mockRepository.getConversationParticipants(any[String], any[String])(any[ExecutionContext]))
        .thenReturn(Future.successful(Some(Participants(NonEmptyList(
          Participant(
            2,
            ParticipantType.Customer,
            Identifier("EORINumber", "GB7777777777", Some("HMRC-CUS-ORG")),
            None,
            None,
            None,
            Some(List(DateTime.parse("2021-02-16T17:31:55.940"), DateTime.parse("2021-02-16T17:31:55.940")))
          ),
          List()
        )))))
      when(
        mockRepository.updateConversationWithReadTime(any[String], any[String], any[Int], any[DateTime])(
          any[ExecutionContext])).thenReturn(Future.successful(true))
      private val result =
        await(
          service.updateReadTime(
            "cdcm",
            "D-80542-20201120",
            enrolments,
            DateTime.parse("2020-11-10T15:00:18.000+0000")
          ))
      result mustBe true
    }

    "return false when something went wrong with adding a readTime to the db" in new TestCase {
      when(mockRepository.getConversationParticipants(any[String], any[String])(any[ExecutionContext]))
        .thenReturn(Future.successful(Some(Participants(NonEmptyList(
          Participant(
            2,
            ParticipantType.Customer,
            Identifier("EORINumber", "GB7777777777", Some("HMRC-CUS-ORG")),
            None,
            None,
            None,
            Some(List(DateTime.parse("2021-02-16T17:31:55.940"), DateTime.parse("2021-02-16T17:31:55.940")))
          ),
          List()
        )))))
      when(
        mockRepository.updateConversationWithReadTime(any[String], any[String], any[Int], any[DateTime])(
          any[ExecutionContext])).thenReturn(Future.successful(false))
      private val result =
        await(
          service
            .updateReadTime("cdcm", "D-80542-20201120", enrolments, DateTime.parse("2020-11-10T15:00:18.000+0000")))
      result mustBe false
    }
  }

  trait TestCase {
    import uk.gov.hmrc.auth.core.Enrolment
    val mockRepository: ConversationRepository = mock[ConversationRepository]
    val mockEmailConnector: EmailConnector = mock[EmailConnector]
    val service: SecureMessageService = new SecureMessageService(mockRepository, mockEmailConnector)
    val enrolments: Enrolments = Enrolments(
      Set(Enrolment("HMRC-CUS-ORG", Vector(EnrolmentIdentifier("EORINumber", "GB7777777777")), "Activated", None)))
    val identifier: Identifier = Identifier("EORINumber", "GB123456789000000", Some("HMRC-CUS-ORG"))
    val systemIdentifier: Identifier = Identifier("CDCM", "D-80542-20201120", None)
    val participant: Participant = Participant(1, ParticipantType.System, identifier, None, None, None, None)
    val customerParticipant: Participant = Participant(
      2,
      ParticipantType.Customer,
      Identifier("EORINumber", "GB123456789000000", Some("HMRC-CUS-ORG")),
      None,
      Some(EmailAddress("test@test.com")),
      None,
      None)
    val mockEnrolments: Enrolments = mock[Enrolments]
  }
}
