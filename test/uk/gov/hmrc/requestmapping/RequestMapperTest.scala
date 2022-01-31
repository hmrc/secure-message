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

package uk.gov.hmrc.requestmapping

import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.PlaySpec
import play.api.test.Helpers.{ await, defaultAwaitTimeout }
import uk.gov.hmrc.requestmapping.model.RequestMap
import uk.gov.hmrc.requestmapping.repository.RequestMappingRepo

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class RequestMapperTest extends PlaySpec with BeforeAndAfterEach with ScalaFutures {

  val mockRequestMapperRepo = mock[RequestMappingRepo]
  val requestMapper = new RequestMapper(mockRequestMapperRepo)

  "RequestMapper.findOrCreate" should {
    "successfully return a new id string to forward on to eis" in new TestSetup("oidg9vkxvklsdfjkwe8fd9rdklf==") {
      when(mockRequestMapperRepo.findRequestMap(any[String])).thenReturn(Future.successful(None))
      when(mockRequestMapperRepo.insertRequestMap(any[Option[RequestMap]], any[String]))
        .thenReturn(Future.successful(Some(RequestMap(xRequest = xRequestId, eisId = eisId, created = created))))
      await(requestMapper.findOrCreate(xRequestId)) mustBe eisId
    }

    "successfully return an already existing id if one if found in mongo" in new TestSetup(
      "sdfklsdfklv94309rfk9sdfklx==") {
      when(mockRequestMapperRepo.findRequestMap(any[String]))
        .thenReturn(Future.successful(Some(RequestMap(xRequest = xRequestId, eisId = eisId, created = created))))
      when(mockRequestMapperRepo.insertRequestMap(any[Option[RequestMap]], any[String]))
        .thenReturn(Future.successful(Some(RequestMap(xRequest = xRequestId, eisId = eisId, created = created))))
      await(requestMapper.findOrCreate(xRequestId)) mustBe eisId
    }
  }

  class TestSetup(requestId: String) {
    val xRequestId: String = requestId
    val eisId: String = UUID.randomUUID().toString
    val created: DateTime = DateTime.now()
  }

}
