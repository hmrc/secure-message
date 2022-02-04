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

package uk.gov.hmrc.requestcache

import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.PlaySpec
import play.api.test.Helpers.{ await, defaultAwaitTimeout }
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global

class MongoMapTestSpec extends PlaySpec with MongoSpecSupport {

  val mockServicesConfig: ServicesConfig = mock[ServicesConfig]
  val mongoMap = new MongoMap(mockServicesConfig)

  "MongoMap" should {
    "get successfully return an empty string" in {
      val xRequestId = "13425t54efdxs5w4rewer34rsfxd=="
      await(mongoMap.get(xRequestId)) mustBe ""
    }

    "successfully create a new map and then return an already existing string from mongo" in {
      val xRequestId = "234t5gsdfsfder5trf4rsdsrw343=="
      val randomId = UUID.randomUUID().toString
      await(mongoMap.insert(xRequestId, randomId)) mustBe true
      await(mongoMap.get(xRequestId)) mustBe randomId
    }
  }

}
