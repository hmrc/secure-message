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

package uk.gov.hmrc.securemessage.controllers.model

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{ JsResultException, Json }
import uk.gov.hmrc.securemessage.TestData.{ TEST_SERVICE_NAME, TEST_URL }
import uk.gov.hmrc.securemessage.helpers.Resources
import uk.gov.hmrc.securemessage.models.core.{ Letter, RenderUrl }

class MessageResourceResponseSpec extends PlaySpec {

  "MessageResourceResponse" must {

    "should update renderUrl service name for 'message' " in {
      val letterFromDb = Resources.readJson("model/core/full-db-letter.json").as[Letter]
      MessageResourceResponse.from(letterFromDb).renderUrl.service mustBe "secure-message"
    }

    "should not update renderUrl service name for 'two-way-message'" in {
      val letterFromDb = Resources.readJson("model/core/full-db-letter-two-way-message.json").as[Letter]
      MessageResourceResponse.from(letterFromDb).renderUrl.service mustBe "two-way-message"
    }

    "should not update renderUrl service name for 'sa-message-renderer' " in {
      val letterFromDb = Resources.readJson("model/core/full-db-letter-sa-message-renderer.json").as[Letter]
      MessageResourceResponse.from(letterFromDb).renderUrl.service mustBe "sa-message-renderer"
    }
  }

  "ServiceUrl.fmt" must {

    import ServiceUrl.fmt

    "read the json correctly" in new Setup {
      Json.parse(serviceUrlJsonString).as[ServiceUrl] mustBe serviceUrl
    }

    "throw the exception for invalid json" in new Setup {
      intercept[JsResultException] {
        Json.parse(serviceUrlInvalidJsonString).as[ServiceUrl]
      }
    }

    "write the object correctly" in new Setup {
      Json.toJson(serviceUrl) mustBe Json.parse(serviceUrlJsonString)
    }
  }

  "ServiceUrl.fromRenderUrl" must {
    "produce ServiceUrl from RenderUrl" in new Setup {
      ServiceUrl.fromRenderUrl(renderUrl) mustBe serviceUrl
    }
  }

  trait Setup {
    val renderUrl: RenderUrl = RenderUrl(service = TEST_URL, url = TEST_SERVICE_NAME)
    val serviceUrl: ServiceUrl = ServiceUrl(service = TEST_URL, url = TEST_SERVICE_NAME)

    val serviceUrlJsonString: String = """{"service":"test@test.com","url":"test_service"}""".stripMargin
    val serviceUrlInvalidJsonString: String = """{}""".stripMargin
  }
}
