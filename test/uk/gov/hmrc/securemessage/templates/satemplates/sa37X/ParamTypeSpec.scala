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

package uk.gov.hmrc.securemessage.templates.satemplates.sa37X

import uk.gov.hmrc.securemessage.SpecBase
import play.api.libs.json.{ JsResultException, Json }

class ParamTypeSpec extends SpecBase {

  "Filing12MonthsMinimumPenaltyParams.formats" must {
    import Filing12MonthsMinimumPenaltyParams.formats

    "read the json correctly" in new Setup {
      Json
        .parse(filing12MonthsMinimumPenaltyParamsJsonString)
        .as[Filing12MonthsMinimumPenaltyParams] mustBe filing12MonthsMinimumPenaltyParams
    }

    "throw exception for invalid json" in new Setup {
      intercept[JsResultException] {
        Json
          .parse(filing12MonthsMinimumPenaltyParamsInvalidJsonString)
          .as[Filing12MonthsMinimumPenaltyParams]
      }
    }

    "write the object correctly" in new Setup {
      Json.toJson(filing12MonthsMinimumPenaltyParams) mustBe Json.parse(filing12MonthsMinimumPenaltyParamsJsonString)
    }
  }

  "FilingFirst3MonthsPenaltyParams.formats" must {
    import FilingFirst3MonthsPenaltyParams.formats

    "read the json correctly" in new Setup {
      Json
        .parse(filingFirst3MonthsPenaltyParamsJsonString)
        .as[FilingFirst3MonthsPenaltyParams] mustBe filingFirst3MonthsPenaltyParams
    }

    "throw exception for invalid json" in new Setup {
      intercept[JsResultException] {
        Json
          .parse(filingFirst3MonthsPenaltyParamsInvalidJsonString)
          .as[FilingFirst3MonthsPenaltyParams]
      }
    }

    "write the object correctly" in new Setup {
      Json.toJson(filingFirst3MonthsPenaltyParams) mustBe Json.parse(filingFirst3MonthsPenaltyParamsJsonString)
    }
  }

  "Filing6MonthsMinimumPenaltyParams.formats" must {
    import Filing6MonthsMinimumPenaltyParams.formats

    "read the json correctly" in new Setup {
      Json
        .parse(filing6MonthsMinimumPenaltyParamsJsonString)
        .as[Filing6MonthsMinimumPenaltyParams] mustBe filing6MonthsMinimumPenaltyParams
    }

    "throw exception for invalid json" in new Setup {
      intercept[JsResultException] {
        Json
          .parse(filing6MonthsMinimumPenaltyParamsInvalidJsonString)
          .as[Filing6MonthsMinimumPenaltyParams]
      }
    }

    "write the object correctly" in new Setup {
      Json.toJson(filing6MonthsMinimumPenaltyParams) mustBe Json.parse(filing6MonthsMinimumPenaltyParamsJsonString)
    }
  }

  trait Setup {
    val filing12MonthsMinimumPenaltyParams: Filing12MonthsMinimumPenaltyParams = Filing12MonthsMinimumPenaltyParams(
      "6000"
    )

    val filingFirst3MonthsPenaltyParams: FilingFirst3MonthsPenaltyParams =
      FilingFirst3MonthsPenaltyParams(dailyPenalty = "100", maxValue = "200")

    val filing6MonthsMinimumPenaltyParams: Filing6MonthsMinimumPenaltyParams =
      Filing6MonthsMinimumPenaltyParams(minimumPenalty = "100")

    val filing12MonthsMinimumPenaltyParamsJsonString: String = """{"minimumPenalty":"6000"}""".stripMargin
    val filing12MonthsMinimumPenaltyParamsInvalidJsonString: String = """{}""".stripMargin

    val filingFirst3MonthsPenaltyParamsJsonString: String = """{"dailyPenalty":"100","maxValue":"200"}""".stripMargin
    val filingFirst3MonthsPenaltyParamsInvalidJsonString: String = """{"maxValue":"200"}""".stripMargin

    val filing6MonthsMinimumPenaltyParamsJsonString: String = """{"minimumPenalty":"100"}""".stripMargin
    val filing6MonthsMinimumPenaltyParamsInvalidJsonString: String = """{}""".stripMargin
  }
}
