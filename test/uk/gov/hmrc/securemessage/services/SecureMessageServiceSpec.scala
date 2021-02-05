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

import akka.stream.Materializer
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.test.Helpers._
import play.api.test.NoMaterializer
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.securemessage.controllers.models.generic
import uk.gov.hmrc.securemessage.controllers.models.generic.{ ApiConversation, ApiMessage, ConversationMetaData, Enrolment }
import uk.gov.hmrc.securemessage.helpers.ConversationUtil
import uk.gov.hmrc.securemessage.models.core.ConversationStatus.Open
import uk.gov.hmrc.securemessage.models.core.Language.English
import uk.gov.hmrc.securemessage.repository.ConversationRepository
import uk.gov.hmrc.securemessage.services.SecureMessageService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ ExecutionContext, Future }

@SuppressWarnings(Array("org.wartremover.warts.All"))
class SecureMessageServiceSpec extends PlaySpec with ScalaFutures with MockitoSugar {

  implicit val mat: Materializer = NoMaterializer
  val listOfCoreConversation = List(ConversationUtil.getFullConversation("D-80542-20201120"))

  implicit val hc: HeaderCarrier = HeaderCarrier()

  "getConversations" should {

    "return a list of ConversationMetaData" in new TestCase {
      private val service = new SecureMessageService(mockRepository)
      val listOfCoreConversation = List(ConversationUtil.getFullConversation("D-80542-20201120"))
      when(mockRepository.getConversations(any[generic.Enrolment])(any[ExecutionContext]))
        .thenReturn(Future.successful(listOfCoreConversation))
      val result = await(service.getConversations(Enrolment("HMRC-CUS_ORG", "EORIName", "GB7777777777")))
      result === List(
        ConversationMetaData(
          "D-80542-20201120",
          "D-80542-20201120",
          Some(DateTime.parse("2020-11-10T15:00:01.000Z")),
          Some("CDS Exports Team"),
          false,
          1))
    }
  }

  "getConversation" should {

    "return a Some with ApiConversation" in new TestCase {
      private val service = new SecureMessageService(mockRepository)
      when(mockRepository.getConversation(any[String], any[String], any[generic.Enrolment])(any[ExecutionContext]))
        .thenReturn(Future.successful(Some(ConversationUtil.getFullConversation("D-80542-20201120"))))
      val result = await(
        service.getConversation("cdcm", "D-80542-20201120", Enrolment("HMRC-CUS_ORG", "EORIName", "GB7777777777")))
      result === Some(
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
          "D-80542-20201120",
          English,
          List(ApiMessage(None, None, None, None, "QmxhaCBibGFoIGJsYWg="))
        ))
    }

    "return a None" in new TestCase {
      private val service = new SecureMessageService(mockRepository)
      when(mockRepository.getConversation(any[String], any[String], any[generic.Enrolment])(any[ExecutionContext]))
        .thenReturn(Future.successful(None))
      val result = await(
        service.getConversation("cdcm", "D-80542-20201120", Enrolment("HMRC-CUS_ORG", "EORIName", "GB7777777777")))
      result mustBe None
    }
  }

  trait TestCase {
    val mockRepository: ConversationRepository = mock[ConversationRepository]
  }
}
