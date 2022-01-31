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

package uk.gov.hmrc.requestmapping.repository

import org.joda.time.DateTime
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.PlaySpec
import play.api.test.Helpers.{ await, defaultAwaitTimeout }
import uk.gov.hmrc.requestmapping.model.RequestMap
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global

class RequestMappingRepoTest extends PlaySpec with MongoSpecSupport with BeforeAndAfterEach with ScalaFutures {

  val mockServicesConfig = mock[ServicesConfig]
  val repository: RequestMappingRepo = new RequestMappingRepo(mockServicesConfig)

  override def beforeEach(): Unit =
    repository.removeAll().map(_ => ()).futureValue

  "insert" should {
    "successfully insert a RequestMap into the mongo collection" in {
      println(s">>>>>>>>>>>>>>>>${repository.ttlExpiry}<<<<<<<<<<<<<<<<<<")
      val testUUID: String = UUID.randomUUID().toString
      val testRequestMap: RequestMap =
        RequestMap("asdasdiiosdfsdfeuis8dcx0934==", testUUID, DateTime.now)
      await(repository.insertRequestMap(None, testRequestMap.xRequest)) mustBe (())
    }

    "successfully insert a RequestMap and then make sure it's removed after 2 second" in {
      val testUUID: String = UUID.randomUUID().toString
      val testRequestMap: RequestMap =
        RequestMap("asdasdiiofsdfserwuis8dcx0934==", testUUID, DateTime.now)
      await(repository.insertRequestMap(None, testRequestMap.xRequest)) mustBe (())
      //TODO: add thread sleep when index working.
//      Thread.sleep(5000)
      await(repository.findRequestMap(testRequestMap.xRequest)) mustBe None
    }
  }

  "find" should {
    "return an already existing RequestMap from mongo" in {
      val testUUID: String = UUID.randomUUID().toString
      val testRequestMap: RequestMap =
        RequestMap("sduigdffgogjhiiofsdfserwuis8dcx0934==", testUUID, DateTime.now)
      await(repository.insertRequestMap(None, testRequestMap.xRequest)) mustBe (())
      await(repository.findRequestMap(testRequestMap.xRequest)) mustBe (Some(testRequestMap))
    }
  }

}
