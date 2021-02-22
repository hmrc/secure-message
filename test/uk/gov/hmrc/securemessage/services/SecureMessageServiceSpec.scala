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
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.test.Helpers._
import play.api.test.NoMaterializer
import uk.gov.hmrc.auth.core.{AuthorisationException, Enrolment, EnrolmentIdentifier, Enrolments}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.securemessage.controllers.models.generic
import uk.gov.hmrc.securemessage.controllers.models.generic._
import uk.gov.hmrc.securemessage.helpers.ConversationUtil
import uk.gov.hmrc.securemessage.models.core.ConversationStatus.Open
import uk.gov.hmrc.securemessage.models.core.Language.English
import uk.gov.hmrc.securemessage.models.core._
import uk.gov.hmrc.securemessage.repository.ConversationRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

@SuppressWarnings(Array("org.wartremover.warts.All"))
class SecureMessageServiceSpec extends PlaySpec with ScalaFutures with MockitoSugar {

  implicit val mat: Materializer = NoMaterializer
  val listOfCoreConversation = List(ConversationUtil.getFullConversation("D-80542-20201120"))

  implicit val hc: HeaderCarrier = HeaderCarrier()

  "getConversations" must {

    "return a list of ConversationMetaData" in new TestCase {
      private val listOfCoreConversation = List(ConversationUtil.getFullConversation("D-80542-20201120"))
      when(mockRepository.getConversations(any[generic.CustomerEnrolment])(any[ExecutionContext]))
        .thenReturn(Future.successful(listOfCoreConversation))
      private val result = await(service.getConversations(CustomerEnrolment("HMRC-CUS_ORG", "EORIName", "GB7777777777")))
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

    "return a Some with ApiConversation" in new TestCase {
      when(mockRepository.getConversation(any[String], any[String], any[generic.CustomerEnrolment])(any[ExecutionContext]))
        .thenReturn(Future.successful(Some(ConversationUtil.getFullConversation("D-80542-20201120"))))
      private val result = await(
        service.getConversation("cdcm", "D-80542-20201120", CustomerEnrolment("HMRC-CUS_ORG", "EORIName", "GB7777777777")))
      result mustBe Some(
        ApiConversation(
          "cdcm",
          "D-80542-20201120",
          Open,
          Some(
            Map(
              "queryId"          -> "D-80542-20201120",
              "caseId"           -> "D-80542",
              "notificationType" -> "CDS Exports",
              "mrn"              -> "DMS7324874993",
              "sourceId"         -> "CDCM")),
          "MRN: 19GB4S24GC3PPFGVR7",
          English,
          NonEmptyList.one(ApiMessage(None, None, None, None, "QmxhaCBibGFoIGJsYWg="))
        ))
    }

    "return a None" in new TestCase {
      when(mockRepository.getConversation(any[String], any[String], any[generic.CustomerEnrolment])(any[ExecutionContext]))
        .thenReturn(Future.successful(None))
      private val result = await(
        service.getConversation("cdcm", "D-80542-20201120", CustomerEnrolment("HMRC-CUS_ORG", "EORIName", "GB7777777777")))
      result mustBe None
    }
  }

  "Adding a message to a conversation" must {

    "update the database when the customer has a participating enrolment" in new TestCase {
      private val customerMessage = CustomerMessageRequest("PGRpdj5IZWxsbzwvZGl2Pg==")
      private val mockEnrolments = mock[Enrolments]
      when(mockRepository.conversationExists(any[String], any[String])(any[ExecutionContext])).thenReturn(Future(true))
      when(mockEnrolments.enrolments).thenReturn(Set(Enrolment("HMRC-CUS-ORG", Seq(EnrolmentIdentifier("EORINumber", "GB123456789000000")), "")))
      when(mockRepository.getConversationParticipants(any[String], any[String])(any[ExecutionContext])).thenReturn(
        Future(Some(Participants(
          NonEmptyList.one(
            Participant(1, ParticipantType.Customer, Identifier("EORINumber", "GB123456789000000", Some("HMRC-CUS-ORG")), None, None, None, None))))))
      when(mockRepository.addMessageToConversation(any[String], any[String], any[Message])(any[ExecutionContext])).thenReturn(Future.successful(()))
      await(service.addMessageToConversation("cdcm", "D-80542-20201120", customerMessage, mockEnrolments))
      verify(mockRepository, times(1)).addMessageToConversation(any[String], any[String], any[Message])(any[ExecutionContext])
    }

    "throw an AuthorisationException if the customer does not have a participating enrolment" in new TestCase {
      private val customerMessage = CustomerMessageRequest("PGRpdj5IZWxsbzwvZGl2Pg==")
      private val mockEnrolments = mock[Enrolments]
      when(mockRepository.conversationExists(any[String], any[String])(any[ExecutionContext])).thenReturn(Future.successful(true))
      when(mockEnrolments.enrolments).thenReturn(Set(Enrolment("HMRC-CUS-ORG", Seq(EnrolmentIdentifier("EORINumber", "GB123456789000001")), "")))
      when(mockRepository.getConversationParticipants(any[String], any[String])(any[ExecutionContext])).thenReturn(
        Future(Some(Participants(
          NonEmptyList.one(Participant(1, ParticipantType.Customer, Identifier("EORINumber", "GB123456789000000",
            Some("HMRC-CUS-ORG")), None, None, None, None))))))
      assertThrows[AuthorisationException] {
        await(service.addMessageToConversation("cdcm", "D-80542-20201120", customerMessage, mockEnrolments))
      }
    }

    "throw an IllegalArgumentException if the conversation ID is not found" in new TestCase {
      private val customerMessage = CustomerMessageRequest("PGRpdj5IZWxsbzwvZGl2Pg==")
      private val mockEnrolments = mock[Enrolments]
      when(mockRepository.conversationExists(any[String], any[String])(any[ExecutionContext])).thenReturn(Future.successful(false))
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
    val service: SecureMessageService = new SecureMessageService(mockRepository)
    val enrolments: Enrolments = Enrolments(
      Set(Enrolment("HMRC-CUS-ORG", Vector(EnrolmentIdentifier("EORINumber", "GB7777777777")), "Activated", None)))

  }
}
