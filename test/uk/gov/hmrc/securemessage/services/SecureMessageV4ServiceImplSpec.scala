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

package uk.gov.hmrc.securemessage.services

import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.http.Status.{ CONFLICT, CREATED, INTERNAL_SERVER_ERROR }
import play.api.libs.json.JsValue
import play.api.mvc.Result
import play.api.test.Helpers.{ contentAsJson, defaultAwaitTimeout, status }
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.securemessage.helpers.Resources
import uk.gov.hmrc.securemessage.models.core.MessageV4
import uk.gov.hmrc.securemessage.repository.MessageV4Repository

import scala.concurrent.{ ExecutionContext, Future }

class SecureMessageV4ServiceImplSpec extends PlaySpec with ScalaFutures {
  "SecureMessageV4ServiceImpl" must {
    "return the messageId when message is successfully created" in new TestCase {
      when(mockMessageRepository.save(any[MessageV4])).thenReturn(Future.successful(true))
      val response: Future[Result] = service.createMessage(validMessageJson)
      status(response) mustBe CREATED
      (contentAsJson(response) \ "id").isEmpty mustBe false
    }

    "return conflict when duplicate message found" in new TestCase {
      when(mockMessageRepository.save(any[MessageV4])).thenReturn(Future.successful(false))
      val response: Future[Result] = service.createMessage(validMessageJson)
      status(response) mustBe CONFLICT
      contentAsJson(response).toString() mustBe """{"reason":"Duplicate Message"}"""
    }

    "return error reason when message can not be created from json" in new TestCase {
      val response: Future[Result] = service.createMessage(inValidMessageJson)
      status(response) mustBe INTERNAL_SERVER_ERROR
      (contentAsJson(response) \ "reason").isEmpty mustBe false
    }
  }

  class TestCase extends MockitoSugar {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
    val mockMessageRepository: MessageV4Repository = mock[MessageV4Repository]
    val validMessageJson: JsValue = Resources.readJson("model/core/v4/valid_message.json")
    val inValidMessageJson: JsValue = Resources.readJson("model/core/v4/missing_mandatory_fields.json")
    val messageV4Captor: ArgumentCaptor[MessageV4] = ArgumentCaptor.forClass(classOf[MessageV4])
    protected val service: SecureMessageV4ServiceImpl = new SecureMessageV4ServiceImpl(mockMessageRepository)
  }
}
