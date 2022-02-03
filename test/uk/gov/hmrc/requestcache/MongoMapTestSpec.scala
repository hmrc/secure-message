/*
 * Copyright 2020 HM Revenue & Customs
 *
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
