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

package uk.gov.hmrc.securemessage.controllers.model.common.write

import play.api.libs.json.{ JsResultException, Json }
import uk.gov.hmrc.securemessage.SpecBase
import uk.gov.hmrc.securemessage.TestData.TEST_CONTENT

class CustomerMessageSpec extends SpecBase {

  "Json Reads" must {
    import CustomerMessage.customerMessageRequestReads

    "read the json correctly" in {
      val customerMessageJsonString = """{"content":"adfg#1456hjftwer=="}"""

      Json.parse(customerMessageJsonString).as[CustomerMessage] mustBe CustomerMessage(TEST_CONTENT)
    }

    "throw exception for invalid json" in {
      intercept[JsResultException] {
        Json.parse("""{}""").as[CustomerMessage]
      }
    }
  }
}
