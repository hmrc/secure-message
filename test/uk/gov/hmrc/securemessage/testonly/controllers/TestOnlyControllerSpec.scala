/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.securemessage.testonly.controllers

import com.mongodb.client.result.DeleteResult
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.mongodb.scala.{ MongoCollection, SingleObservable }
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.http.Status.*
import play.api.mvc.Result
import play.api.test.Helpers.{ contentAsString, defaultAwaitTimeout, status }
import play.api.test.{ FakeRequest, Helpers }
import uk.gov.hmrc.securemessage.models.core.Conversation
import uk.gov.hmrc.securemessage.models.v4.*
import uk.gov.hmrc.securemessage.repository.{ ConversationRepository, MessageRepository, SecureMessageRepository }

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ ExecutionContext, Future }

class TestOnlyControllerSpec extends PlaySpec with ScalaFutures with MockitoSugar {
  val conversationRepository: ConversationRepository = mock[ConversationRepository]
  val messageRepository: MessageRepository = mock[MessageRepository]
  val secureMessageRepository: SecureMessageRepository = mock[SecureMessageRepository]
  val collection: MongoCollection[Conversation] = mock[MongoCollection[Conversation]]
  val controller = new TestOnlyController(
    Helpers.stubControllerComponents(),
    conversationRepository,
    messageRepository,
    secureMessageRepository
  )
  val fakeRequest = FakeRequest()

  "TestOnlyController" must {
    "delete all conversations - acknowledged" in {
      val brakeBatchDetails = BrakeBatchDetails("batchId", "formId", LocalDate.now(), "templateId", 1)
      when(conversationRepository.collection).thenReturn(collection)
      when(collection.deleteMany(any)).thenReturn(SingleObservable(DeleteResult.acknowledged(2)))

      val response: Future[Result] = controller.deleteConversations()(fakeRequest)
      status(response) mustBe OK
      contentAsString(response) mustBe "Conversations deleted successfully 2"
    }

    "delete all conversations - unacknowledged" in {
      val brakeBatchDetails = BrakeBatchDetails("batchId", "formId", LocalDate.now(), "templateId", 1)
      when(conversationRepository.collection).thenReturn(collection)
      when(collection.deleteMany(any)).thenReturn(SingleObservable(DeleteResult.unacknowledged()))

      val response: Future[Result] = controller.deleteConversations()(fakeRequest)
      status(response) mustBe NOT_MODIFIED
    }
  }
}
