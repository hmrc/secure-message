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

package uk.gov.hmrc.securemessage.templates.satemplates.sa359

import play.api.libs.json.{ JsResultException, Json }
import uk.gov.hmrc.securemessage.TestData.{ TEST_DATE, TEST_YEAR, TEST_YEAR_2026 }
import uk.gov.hmrc.securemessage.SpecBase
import uk.gov.hmrc.securemessage.templates.satemplates.helpers.TaxYear

class TemplateSA359Spec extends SpecBase {

  "SA359_v1ContentParams.formats" must {
    import SA359_v1ContentParams.formats

    "read the json correctly" in new Setup {
      Json.parse(sA359_v1ContentParamsJsonString).as[SA359_v1ContentParams] mustBe sA359_v1ContentParams
    }

    "throw the exception for invalid json" in new Setup {
      intercept[JsResultException] {
        Json.parse(sA359_v1ContentParamsInvalidJsonString).as[SA359_v1ContentParams]
      }
    }

    "write the object correctly" in new Setup {
      Json.toJson(sA359_v1ContentParams) mustBe Json.parse(sA359_v1ContentParamsJsonString)
    }
  }

  "SA359_v2ContentParams.formats" must {
    import SA359_v2ContentParams.formats

    "read the json correctly" in new Setup {
      Json.parse(sA359_v2ContentParamsJsonString).as[SA359_v2ContentParams] mustBe sA359_v2ContentParams
    }

    "throw the exception for invalid json" in new Setup {
      intercept[JsResultException] {
        Json.parse(sA359_v2ContentParamsInvalidJsonString).as[SA359_v2ContentParams]
      }
    }

    "write the object correctly" in new Setup {
      Json.toJson(sA359_v2ContentParams) mustBe Json.parse(sA359_v2ContentParamsJsonString)
    }
  }

  trait Setup {
    val sA359_v1ContentParams: SA359_v1ContentParams =
      SA359_v1ContentParams(
        taxYear = TaxYear(TEST_YEAR, TEST_YEAR_2026),
        onlineDeadline = Some(TEST_DATE)
      )

    val sA359_v2ContentParams: SA359_v2ContentParams =
      SA359_v2ContentParams(
        taxYear = TaxYear(TEST_YEAR, TEST_YEAR_2026),
        onlineDeadline = Some(TEST_DATE)
      )

    val sA359_v1ContentParamsJsonString: String =
      """{
        |"taxYear":{"start":2025,"end":2026},
        |"onlineDeadline":"2025-12-20"
        |}""".stripMargin

    val sA359_v1ContentParamsInvalidJsonString: String =
      """{
        |"onlineDeadline":"2025-12-20"
        |}""".stripMargin

    val sA359_v2ContentParamsJsonString: String =
      """{
        |"taxYear":{"start":2025,"end":2026},
        |"onlineDeadline":"2025-12-20"
        |}""".stripMargin

    val sA359_v2ContentParamsInvalidJsonString: String =
      """{
        |"onlineDeadline":"2025-12-20"
        |}""".stripMargin
  }
}
