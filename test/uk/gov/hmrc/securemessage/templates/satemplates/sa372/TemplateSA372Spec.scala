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

package uk.gov.hmrc.securemessage.templates.satemplates.sa372

import play.api.libs.json.{ JsResultException, Json }
import uk.gov.hmrc.securemessage.SpecBase
import uk.gov.hmrc.securemessage.TestData.{ TEST_DATE, TEST_YEAR, TEST_YEAR_2026 }
import uk.gov.hmrc.securemessage.templates.satemplates.helpers.TaxYear

class TemplateSA372Spec extends SpecBase {

  "SA372_ContentParams.formats" must {
    import SA372_ContentParams.formats

    "read the json correctly" in new Setup {
      Json.parse(sA372_ContentParamsJsonString).as[SA372_ContentParams] mustBe sA372_ContentParams
    }

    "throw the exception for invalid json" in new Setup {
      intercept[JsResultException] {
        Json.parse(sA372_ContentParamsInvalidJsonString).as[SA372_ContentParams]
      }
    }

    "write the object correctly" in new Setup {
      Json.toJson(sA372_ContentParams) mustBe Json.parse(sA372_ContentParamsJsonString)
    }
  }

  trait Setup {
    val sA372_ContentParams: SA372_ContentParams =
      SA372_ContentParams(
        taxYear = TaxYear(TEST_YEAR, TEST_YEAR_2026),
        onlineFilingDate = Some(TEST_DATE),
        showWhatToDoLateFiling = true,
        showPenaltyFor30Variant = true
      )

    val sA372_ContentParamsJsonString: String =
      """{
        |"taxYear":{"start":2025,"end":2026},
        |"onlineFilingDate":"2025-12-20",
        |"showWhatToDoLateFiling":true,
        |"showPenaltyFor30Variant":true
        |}""".stripMargin

    val sA372_ContentParamsInvalidJsonString: String =
      """{
        |"onlineFilingDate":"2025-12-20",
        |"showWhatToDoLateFiling":true,
        |"showPenaltyFor30Variant":true
        |}""".stripMargin
  }
}
