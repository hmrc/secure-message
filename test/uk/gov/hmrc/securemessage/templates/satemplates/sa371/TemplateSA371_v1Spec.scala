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

package uk.gov.hmrc.securemessage.templates.satemplates.sa371

import play.api.libs.json.{ JsResultException, Json }
import uk.gov.hmrc.securemessage.SpecBase
import uk.gov.hmrc.securemessage.TestData.{ TEST_NAME, TEST_YEAR, TEST_YEAR_2026 }
import uk.gov.hmrc.securemessage.templates.satemplates.helpers.TaxYear

class TemplateSA371_v1Spec extends SpecBase {

  "SA371_v1ContentParams.formats" must {
    import SA371_v1ContentParams.formats

    "read the json correctly" in new Setup {
      Json.parse(sA371_v1ContentParamsJsonString).as[SA371_v1ContentParams] mustBe sA371_v1ContentParams
    }

    "throw the exception for invalid json" in new Setup {
      intercept[JsResultException] {
        Json.parse(sA371_v1ContentParamsInvalidJsonString).as[SA371_v1ContentParams]
      }
    }

    "write the object correctly" in new Setup {
      Json.toJson(sA371_v1ContentParams) mustBe Json.parse(sA371_v1ContentParamsJsonString)
    }
  }

  trait Setup {
    val sA371_v1ContentParams: SA371_v1ContentParams =
      SA371_v1ContentParams(
        taxYear = TaxYear(TEST_YEAR, TEST_YEAR_2026),
        penaltiesTotal = "100",
        partnershipName = TEST_NAME,
        showWhatToDoLateFiling = true,
        showLateFilingHeading = true,
        showRecentlyFiledInfo = true,
        lateFilingPenalties = Seq()
      )

    val sA371_v1ContentParamsJsonString: String =
      """{
        |"taxYear":{"start":2025,"end":2026},
        |"penaltiesTotal":"100",
        |"partnershipName":"test_name",
        |"showWhatToDoLateFiling":true,
        |"showLateFilingHeading":true,
        |"showRecentlyFiledInfo":true,
        |"lateFilingPenalties":[]
        |}""".stripMargin

    val sA371_v1ContentParamsInvalidJsonString: String =
      """{
        |"penaltiesTotal":"100",
        |"partnershipName":"test_name",
        |"showWhatToDoLateFiling":true,
        |"showLateFilingHeading":true,
        |"showRecentlyFiledInfo":true,
        |"lateFilingPenalties":[]
        |}""".stripMargin
  }
}
