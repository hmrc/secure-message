/*
 * Copyright 2022 HM Revenue & Customs
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

import akka.stream.Materializer
import akka.stream.testkit.NoMaterializer
import org.mockito.ArgumentMatchers.{ any, eq => eqTo }
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.i18n.Messages
import play.api.libs.json.JsValue
import play.api.test.Helpers.stubMessages
import uk.gov.hmrc.common.message.model.MessagesCount
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.domain.TaxIds.TaxIdWithName
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.securemessage.UnitTest
import uk.gov.hmrc.securemessage.connectors.AuthIdentifiersConnector
import uk.gov.hmrc.securemessage.controllers.model.common.read.MessageMetadata
import uk.gov.hmrc.securemessage.helpers.Resources
import uk.gov.hmrc.securemessage.models.core._
import uk.gov.hmrc.securemessage.services.SecureMessageServiceImpl

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ ExecutionContext, Future }

class NonCDSMessageRetrieverSpec extends PlaySpec with MockitoSugar with UnitTest {
  implicit val mat: Materializer = NoMaterializer
  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit val stubMsgs: Messages = stubMessages()

  "fetch messages" must {
    "return metadata for both conversations and letters" in new TestCase {
      val result = retriever.fetch(MessageRequestWrapper(None, None, None, MessageFilter(List("nino"))))(hc, stubMsgs)
      result.map(_.as[List[MessageMetadata]] mustBe letters)
    }
  }
  "getMessagesCount" must {
    "return count" in new TestCase() {
      val result =
        retriever.messageCount(MessageRequestWrapper(None, None, None, MessageFilter(List("nino"))))(hc, stubMsgs)
      result.map(_.as[List[Count]] mustBe Count(1, 1))
    }
  }

  class TestCase {
    val mockSecureMessageService: SecureMessageServiceImpl = mock[SecureMessageServiceImpl]
    val mockAuthConnector: AuthIdentifiersConnector = mock[AuthIdentifiersConnector]

    val retriever = new NonCDSMessageRetriever(mockAuthConnector, mockSecureMessageService)

    val authTaxIds: Set[TaxIdWithName] = Set(Nino("SJ123456A"))

    val storedLetters: List[JsValue] = List(Resources.readJson("model/core/letter_for_message.json"))
    val letters: List[Letter] = storedLetters.map(_.as[Letter])

    val messageFilter: MessageFilter = MessageFilter(List("nino"))

    when(
      mockSecureMessageService
        .getMessagesList(eqTo(authTaxIds))(any[ExecutionContext], any[HeaderCarrier], eqTo(messageFilter)))
      .thenReturn(Future.successful(letters))

    when(mockSecureMessageService.getMessagesCount(eqTo(authTaxIds))(any[HeaderCarrier], eqTo(messageFilter)))
      .thenReturn(Future.successful(MessagesCount(1, 1)))

    when(mockAuthConnector.currentEffectiveTaxIdentifiers(any[HeaderCarrier])).thenReturn(Future.successful(authTaxIds))
  }
}
