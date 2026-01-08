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

package uk.gov.hmrc.securemessage.templates.satemplates.sa328d

import play.api.libs.json.{ JsResultException, Json }
import uk.gov.hmrc.securemessage.SpecBase
import uk.gov.hmrc.securemessage.TestData.{ TEST_DATE, TEST_NAME, TEST_YEAR, TEST_YEAR_2026 }
import uk.gov.hmrc.securemessage.templates.satemplates.helpers.TaxYear

class TemplateSA328D_v1Spec extends SpecBase {

  "SA328D_v1ContentParams.format" must {
    import SA328D_v1ContentParams.format

    "read the json correctly" in new Setup {
      Json.parse(sA328D_v1ContentParamsJsonString).as[SA328D_v1ContentParams] mustBe sA328D_v1ContentParams
    }

    "throw the exception for invalid json" in new Setup {
      intercept[JsResultException] {
        Json.parse(sA328D_v1ContentParamsInvalidJsonString).as[SA328D_v1ContentParams]
      }
    }

    "write the object correctly" in new Setup {
      Json.toJson(sA328D_v1ContentParams) mustBe Json.parse(sA328D_v1ContentParamsJsonString)
    }
  }

  trait Setup {
    val sA328D_v1ContentParams: SA328D_v1ContentParams =
      SA328D_v1ContentParams(
        taxYear = TaxYear(TEST_YEAR, TEST_YEAR_2026),
        penaltyDueDate = TEST_DATE,
        partnershipName = Some(TEST_NAME)
      )

    val sA328D_v1ContentParamsJsonString: String =
      """{
        |"taxYear":{"start":2025,"end":2026},
        |"penaltyDueDate":"2025-12-20",
        |"partnershipName":"test_name"
        |}""".stripMargin

    val sA328D_v1ContentParamsInvalidJsonString: String =
      """{
        |"penaltyDueDate":"2025-12-20",
        |"partnershipName":"test_name"
        |}""".stripMargin
  }
}
