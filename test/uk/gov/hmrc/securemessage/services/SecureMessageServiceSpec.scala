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
import uk.gov.hmrc.securemessage.models.EmailRequest
import play.api.http.Status.CONFLICT
import uk.gov.hmrc.securemessage.repository.ConversationRepository
import uk.gov.hmrc.emailaddress.EmailAddress
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.securemessage.{ DuplicateConversationError, EmailLookupError, SecureMessageError }
import uk.gov.hmrc.securemessage.controllers.models.generic
import uk.gov.hmrc.securemessage.controllers.models.generic._
import uk.gov.hmrc.securemessage.helpers.ConversationUtil
import uk.gov.hmrc.securemessage.models.core._
import uk.gov.hmrc.securemessage.connectors.{ ChannelPreferencesConnector, EmailConnector }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ ExecutionContext, Future }

@SuppressWarnings(Array("org.wartremover.warts.All"))
class SecureMessageServiceSpec extends PlaySpec with ScalaFutures with MockitoSugar {

  implicit val mat: Materializer = NoMaterializer
  val listOfCoreConversation = List(
    ConversationUtil.getFullConversation("D-80542-20201120", "HMRC-CUS-ORG", "EORINumber", "GB1234567890"))
  val cnvWithEmail = ConversationUtil.getConversationRequest(true).asConversation("cdcm", "123")
  val cnvWithNoEmail: Conversation = ConversationUtil.getConversationRequest(false).asConversation("cdcm", "123")

  implicit val hc: HeaderCarrier = HeaderCarrier()

  "createConversation" must {

    s"return CREATED ($CREATED) when an email address is provided" in new TestCase {
      when(mockRepository.insertIfUnique(cnvWithEmail)(global)).thenReturn(Future(Right(true)))
      private val result = service.createConversation(cnvWithEmail)
      result.futureValue mustBe Right(CREATED)
    }
    "return SecureMessageException when no email address is provided and cannot be found in cds" in new TestCase {
      when(mockRepository.insertIfUnique(cnvWithNoEmail)(global)).thenReturn(Future(Right(true)))
      when(mockChannelPreferencesConnector.getEmailForEnrolment(any[Identifier])(any[HeaderCarrier]))
        .thenReturn(Future(Left(EmailLookupError(""))))
      private val result = service.createConversation(cnvWithNoEmail).futureValue
      result.swap.toOption.get.message mustBe "Verified email address could not be found"
    }
    s"return CREATED ($CREATED) when no email address is provided but is found in the CDS lookup" in new TestCase {
      val cnv = cnvWithNoEmail.copy(participants = cnvWithNoEmail.participants.map(p =>
        if (p.id == 2) p.copy(email = Some(EmailAddress("joeblogs@yahoo.com"))) else p))
      when(mockRepository.insertIfUnique(cnv)(global)).thenReturn(Future(Right(true)))
      when(mockChannelPreferencesConnector.getEmailForEnrolment(any[Identifier])(any[HeaderCarrier]))
        .thenReturn(Future(Right(EmailAddress("joeblogs@yahoo.com"))))
      private val result = service.createConversation(cnvWithNoEmail).futureValue
      result.toOption.get mustBe CREATED
    }
    s"return CONFLICT ($CONFLICT) when a conversation already exists for this client and conversation ID" in new TestCase {
      when(mockRepository.insertIfUnique(any[Conversation])(any[ExecutionContext]))
        .thenReturn(Future(Left(DuplicateConversationError("errMsg", None))))
      private val result: Either[SecureMessageError, Int] = service.createConversation(cnvWithEmail).futureValue
      (result.swap.toOption.get match {
        case DuplicateConversationError(m, _) => m
        case _                                => ""
      }) mustBe "errMsg"
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

  "Adding a message to a conversation" must {

    "update the database when the customer has a participating enrolment" in new TestCase {
      private val customerMessage = CustomerMessageRequest("PGRpdj5IZWxsbzwvZGl2Pg==")
      private val mockEnrolments = mock[Enrolments]
      when(mockRepository.conversationExists(any[String], any[String])(any[ExecutionContext])).thenReturn(Future(true))
      when(mockEnrolments.enrolments)
        .thenReturn(Set(Enrolment("HMRC-CUS-ORG", Seq(EnrolmentIdentifier("EORINumber", "GB123456789000000")), "")))
      private val identifier: Identifier = Identifier("EORINumber", "GB123456789000000", Some("HMRC-CUS-ORG"))
      private val participant = Participant(1, ParticipantType.Customer, identifier, None, None, None, None)
      when(mockRepository.getConversationParticipants(any[String], any[String])(any[ExecutionContext]))
        .thenReturn(Future(Some(Participants(NonEmptyList.one(participant)))))
      when(mockRepository.addMessageToConversation(any[String], any[String], any[Message])(any[ExecutionContext]))
        .thenReturn(Future.successful(()))
      await(service.addMessageToConversation("cdcm", "D-80542-20201120", customerMessage, mockEnrolments))
      verify(mockRepository, times(1))
        .addMessageToConversation(any[String], any[String], any[Message])(any[ExecutionContext])
    }

    "throw an AuthorisationException if the customer does not have a participating enrolment" in new TestCase {
      private val customerMessage = CustomerMessageRequest("PGRpdj5IZWxsbzwvZGl2Pg==")
      private val mockEnrolments = mock[Enrolments]
      when(mockRepository.conversationExists(any[String], any[String])(any[ExecutionContext]))
        .thenReturn(Future.successful(true))
      when(mockEnrolments.enrolments)
        .thenReturn(Set(Enrolment("HMRC-CUS-ORG", Seq(EnrolmentIdentifier("EORINumber", "GB123456789000001")), "")))
      private val eORINumber: Identifier = Identifier("EORINumber", "GB123456789000000", Some("HMRC-CUS-ORG"))
      private val participant = Participant(1, ParticipantType.Customer, eORINumber, None, None, None, None)
      when(mockRepository.getConversationParticipants(any[String], any[String])(any[ExecutionContext]))
        .thenReturn(Future(Some(Participants(NonEmptyList.one(participant)))))
      assertThrows[AuthorisationException] {
        await(service.addMessageToConversation("cdcm", "D-80542-20201120", customerMessage, mockEnrolments))
      }
    }

    "throw an IllegalArgumentException if the conversation ID is not found" in new TestCase {
      private val customerMessage = CustomerMessageRequest("PGRpdj5IZWxsbzwvZGl2Pg==")
      private val mockEnrolments = mock[Enrolments]
      when(mockRepository.conversationExists(any[String], any[String])(any[ExecutionContext]))
        .thenReturn(Future.successful(false))
      assertThrows[IllegalArgumentException] {
        await(service.addMessageToConversation("cdcm", "D-80542-20201120", customerMessage, mockEnrolments))
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
    when(mockEmailConnector.send(any[EmailRequest])(any[HeaderCarrier])).thenReturn(Future.successful(Right(CREATED)))
    val mockChannelPreferencesConnector: ChannelPreferencesConnector = mock[ChannelPreferencesConnector]
    val service: SecureMessageService =
      new SecureMessageService(mockRepository, mockEmailConnector, mockChannelPreferencesConnector)
    val enrolments: Enrolments = Enrolments(
      Set(Enrolment("HMRC-CUS-ORG", Vector(EnrolmentIdentifier("EORINumber", "GB7777777777")), "Activated", None)))
  }
}
