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

package uk.gov.hmrc.securemessage.templates.satemplates.sa251

import play.api.libs.json.{ JsResultException, Json }
import uk.gov.hmrc.securemessage.SpecBase
import uk.gov.hmrc.securemessage.TestData.{ TEST_DATE, TEST_YEAR, TEST_YEAR_2026 }
import uk.gov.hmrc.securemessage.templates.satemplates.helpers.TaxYear

class TemplateSA251Spec extends SpecBase {
  "SA251_v2ContentParams.jsonFormat" must {
    import SA251_v2ContentParams.jsonFormat

    "read the json correctly" in new Setup {
      Json.parse(sA251_v2ContentParamsJsonString).as[SA251_v2ContentParams] mustBe sA251_v2ContentParams
    }

    "throw the exception for invalid json" in new Setup {
      intercept[JsResultException] {
        Json.parse(sA251_v2ContentParamsInvalidJsonString).as[SA251_v2ContentParams]
      }
    }

    "write the object correctly" in new Setup {
      Json.toJson(sA251_v2ContentParams) mustBe Json.parse(sA251_v2ContentParamsJsonString)
    }
  }

  "SA251_v3ContentParams.jsonFormat" must {
    import SA251_v3ContentParams.jsonFormat

    "read the json correctly" in new Setup {
      Json.parse(sA251_v3ContentParamsJsonString).as[SA251_v3ContentParams] mustBe sA251_v3ContentParams
    }

    "throw the exception for invalid json" in new Setup {
      intercept[JsResultException] {
        Json.parse(sA251_v3ContentParamsInvalidJsonString).as[SA251_v3ContentParams]
      }
    }

    "write the object correctly" in new Setup {
      Json.toJson(sA251_v3ContentParams) mustBe Json.parse(sA251_v3ContentParamsJsonString)
    }
  }

  trait Setup {
    val sA251_v2ContentParams: SA251_v2ContentParams =
      SA251_v2ContentParams(
        lastYearToFile = TaxYear(TEST_YEAR, TEST_YEAR_2026),
        totalAmountDueToHmrc = 100,
        outstandingYears = Set(TaxYear(TEST_YEAR, TEST_YEAR_2026))
      )

    val sA251_v3ContentParams: SA251_v3ContentParams = SA251_v3ContentParams(
      lastYearToFile = TaxYear(TEST_YEAR, TEST_YEAR_2026),
      totalAmountDueToHmrc = 100,
      outstandingYears = Set(TaxYear(TEST_YEAR, TEST_YEAR_2026)),
      nextPaymentDueDate = Some(TEST_DATE)
    )

    val sA251_v2ContentParamsJsonString: String =
      """{
        |"lastYearToFile":{"start":2025,"end":2026},
        |"totalAmountDueToHmrc":100,
        |"outstandingYears":[{"start":2025,"end":2026}]
        |}""".stripMargin

    val sA251_v2ContentParamsInvalidJsonString: String =
      """{
        |"totalAmountDueToHmrc":100,
        |"outstandingYears":[{"start":2025,"end":2026}]
        |}""".stripMargin

    val sA251_v3ContentParamsJsonString: String =
      """{
        |"lastYearToFile":{"start":2025,"end":2026},
        |"totalAmountDueToHmrc":100,
        |"outstandingYears":[{"start":2025,"end":2026}],
        |"nextPaymentDueDate":"2025-12-20"
        |}""".stripMargin

    val sA251_v3ContentParamsInvalidJsonString: String =
      """{
        |"totalAmountDueToHmrc":100,
        |"outstandingYears":[{"start":2025,"end":2026}],
        |"nextPaymentDueDate":"2025-12-20"
        |}""".stripMargin
  }
}
