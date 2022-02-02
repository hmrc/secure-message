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

package uk.gov.hmrc.securemessage.repository

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.PlaySpec
import play.api.test.Helpers.{ await, defaultAwaitTimeout }
import reactivemongo.bson.BSONDocument
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.requestmapping.repository.CacheStoreRepo

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CustomerMessageCacheStoreSpec extends PlaySpec with MongoSpecSupport with BeforeAndAfterEach with ScalaFutures {

  val mockServicesConfig: ServicesConfig = mock[ServicesConfig]
  val mockCacheStore: CacheStoreRepo[String, UUID] = mock[CacheStoreRepo[String, UUID]]
  val cacheStore = new CustomerMessageCacheStore(mockServicesConfig)

  "CustomerMessageCacheStore.findOrCreate" should {
    "successfully return a new id string to forward on to eis" in {
      val xRequestId: String = "oidg9vkxvklsdfjkwe8fd9rdklf=="
      when(mockCacheStore.findValue(any[String])).thenReturn(Future.successful(None))
      when(mockCacheStore.createMap(any[String], any[UUID])).thenReturn(Future.successful(true))
      await(cacheStore.findOrCreate(xRequestId).map(res => res)) mustNot be(xRequestId)
    }

    "successfully return an already existing id if one if found in mongo" in {
      val xRequestId: String = "sdfklsdfklv94309rfk9sdfklx=="
      val eisId: String = UUID.fromString("781974aa-a482-4530-8066-c0b4de79d1c9").toString
      when(mockCacheStore.findValue(any[String]))
        .thenReturn(Future.successful(Some(BSONDocument(s"$xRequestId" -> s"$eisId"))))
      await(cacheStore.findOrCreate(xRequestId)) mustBe eisId
    }
  }
}
