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

package uk.gov.hmrc.securemessage.handlers

import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.testkit.NoMaterializer
import org.mockito.ArgumentMatchers.{ any, eq => eqTo }
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.i18n.Messages
import play.api.libs.json.JsValue
import play.api.test.Helpers.stubMessages
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.Retrieval
import uk.gov.hmrc.auth.core.{ AuthConnector, Enrolments }
import uk.gov.hmrc.common.message.model.MessagesCount
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.securemessage.UnitTest
import uk.gov.hmrc.securemessage.controllers.model.common.read.MessageMetadata
import uk.gov.hmrc.securemessage.helpers.Resources
import uk.gov.hmrc.securemessage.models.core._
import uk.gov.hmrc.securemessage.services.SecureMessageServiceImpl

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ ExecutionContext, Future }

class CDSMessageRetrieverSpec extends PlaySpec with MockitoSugar with UnitTest {
  implicit val mat: Materializer = NoMaterializer
  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit val stubMsgs: Messages = stubMessages()

  "fetch messages" must {
    "return metadata for both conversations and letters" in new TestCase {
      val result = retriever.fetch(MessageRequestWrapper(None, Some(List(testEnrolment)), None), English)(hc, stubMsgs)
      result.map(_.as[List[MessageMetadata]] mustBe messagesMetadata)
    }
  }
  "getMessagesCount" must {
    "return count" in new TestCase() {
      val result = retriever.messageCount(MessageRequestWrapper(None, Some(List(testEnrolment)), None))(hc, stubMsgs)
      result.map(_.as[List[Count]] mustBe Count(1, 1))
    }
  }

  class TestCase {
    val mockSecureMessageService: SecureMessageServiceImpl = mock[SecureMessageServiceImpl]
    val mockAuthConnector: AuthConnector = mock[AuthConnector]

    val retriever = new CDSMessageRetriever(mockAuthConnector, mockSecureMessageService)

    val testEnrolment = CustomerEnrolment("HMRC-CUS-ORG", "EORINumber", "GB123456789")
    val authEnrolments: Set[CustomerEnrolment] = Set(testEnrolment)
    val customerEnrolments: Set[CustomerEnrolment] = Set(testEnrolment)

    val storedLetters: List[JsValue] = List(Resources.readJson("model/core/full-db-letter.json"))
    val storedConversations: List[JsValue] = List(Resources.readJson("model/core/full-db-conversation.json"))

    val conversations: List[Conversation] = storedConversations.map(_.as[Conversation])
    val conversationsMetadata: List[MessageMetadata] = List(
      Resources.readJson("model/core/full-db-conversation-metadata.json").as[MessageMetadata]
    )
    val letters: List[Letter] = storedLetters.map(_.as[Letter])
    val lettersMetadata: List[MessageMetadata] = List(
      Resources.readJson("model/core/full-db-letter-metadata.json").as[MessageMetadata]
    )
    val messages: List[Message] = conversations ++ letters
    val messagesMetadata: List[MessageMetadata] = conversationsMetadata ++ lettersMetadata
    when(
      mockSecureMessageService
        .getMessages(
          eqTo(authEnrolmentsFrom(authEnrolments)),
          eqTo(Filters(None, Some(customerEnrolments.toList), None))
        )(any[ExecutionContext])
    )
      .thenReturn(Future.successful(messages))

    when(
      mockSecureMessageService.getMessagesCount(
        eqTo(authEnrolmentsFrom(authEnrolments)),
        eqTo(Filters(None, Some(customerEnrolments.toList), None))
      )(any[ExecutionContext])
    )
      .thenReturn(Future.successful(MessagesCount(1, 1)))

    val enrolments: Enrolments = authEnrolmentsFrom(authEnrolments)

    when(
      mockAuthConnector
        .authorise(any[Predicate], any[Retrieval[Enrolments]])(any[HeaderCarrier], any[ExecutionContext])
    )
      .thenReturn(Future.successful(enrolments))
  }
}
