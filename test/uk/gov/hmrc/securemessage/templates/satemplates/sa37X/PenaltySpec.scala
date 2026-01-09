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

package uk.gov.hmrc.securemessage.templates.satemplates.sa37X

import play.api.libs.json.{ JsResultException, JsString, Json }
import uk.gov.hmrc.securemessage.SpecBase
import uk.gov.hmrc.securemessage.TestData.TEST_TEMPLATE_ID

class PenaltySpec extends SpecBase {
  "Json Reads" must {
    import Penalty.formats

    "read the json correctly" in new Setup {
      Json.parse(penaltyJsonString).as[Penalty] mustBe penalty
    }

    "throw exception for invalid json" in new Setup {
      intercept[JsResultException] {
        Json.parse(penaltyInvalidJsonString).as[Penalty]
      }
    }
  }

  "write the object correctly" in new Setup {
    Json.toJson(penalty) mustBe Json.parse(penaltyJsonString)
  }

  trait Setup {
    val testJsValue: JsString = JsString("test")
    val penalty: Penalty = Penalty(templateId = TEST_TEMPLATE_ID, contentParams = testJsValue)

    val penaltyJsonString: String = """{"templateId":"test_template_id","contentParams":"test"}""".stripMargin
    val penaltyInvalidJsonString: String = """{"contentParams":"test"}""".stripMargin
  }
}
