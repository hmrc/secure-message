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

package uk.gov.hmrc.securemessage.templates.satemplates.sa370

import play.api.libs.json.{ JsResultException, Json }
import uk.gov.hmrc.securemessage.SpecBase

class PaymentIncreasedPenaltySpec extends SpecBase {

  "PaymentIncreasedPenaltyParams.formats" should {
    import PaymentIncreasedPenaltyParams.formats

    "read the json correctly" in new Setup {
      Json
        .parse(paymentIncreasedPenaltyParamsJsonString)
        .as[PaymentIncreasedPenaltyParams] mustBe paymentIncreasedPenaltyParams
    }

    "throw the exception for invalid json" in new Setup {
      intercept[JsResultException] {
        Json.parse(paymentIncreasedPenaltyParamsInvalidJsonString).as[PaymentIncreasedPenaltyParams]
      }
    }

    "write the object correctly" in new Setup {
      Json.toJson(paymentIncreasedPenaltyParams) mustBe Json.parse(paymentIncreasedPenaltyParamsJsonString)
    }
  }

  "PaymentIncreasedPenaltyParams.convertParams" should {
    "return correct js value for the converted params" in new Setup {
      PaymentIncreasedPenaltyParams.convertParams(
        Json.parse(paymentIncreasedPenaltyParamsJsonString)
      ) mustBe paymentIncreasedPenaltyParams
    }
  }

  trait Setup {
    val paymentIncreasedPenaltyParams: PaymentIncreasedPenaltyParams = PaymentIncreasedPenaltyParams(
      latePaymentPenaltyRate = "5",
      revisedAmount = "100",
      latePaymentTriggerDate = "2025-12-28",
      previouslyCharged = "50",
      toPay = "800"
    )

    val paymentIncreasedPenaltyParamsJsonString: String =
      """{
        |"latePaymentPenaltyRate":"5",
        |"revisedAmount":"100",
        |"latePaymentTriggerDate":"2025-12-28",
        |"previouslyCharged":"50",
        |"toPay":"800"
        |}""".stripMargin

    val paymentIncreasedPenaltyParamsInvalidJsonString: String =
      """{
        |"revisedAmount":"100",
        |"latePaymentTriggerDate":"2025-12-28",
        |"previouslyCharged":"50",
        |"toPay":"800"
        |}""".stripMargin
  }
}
