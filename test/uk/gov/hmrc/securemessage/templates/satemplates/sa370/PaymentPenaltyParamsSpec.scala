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

package uk.gov.hmrc.securemessage.templates.satemplates.sa370

import play.api.libs.json.{ JsResultException, Json }
import uk.gov.hmrc.securemessage.SpecBase

class PaymentPenaltyParamsSpec extends SpecBase {

  "Json Reads" should {
    import PaymentPenaltyParams.formats

    "read the json correctly" in new Setup {
      Json
        .parse(paymentPenaltyParamsJsonString)
        .as[PaymentPenaltyParams] mustBe paymentPenaltyParams
    }

    "throw the exception for invalid json" in new Setup {
      intercept[JsResultException] {
        Json.parse(paymentPenaltyParamsInvalidJsonString).as[PaymentPenaltyParams]
      }
    }
  }

  "Json Writes" should {
    "write the object correctly" in new Setup {
      Json.toJson(paymentPenaltyParams) mustBe Json.parse(paymentPenaltyParamsJsonString)
    }
  }

  "templates" should {
    "correct map of templates" in {
      PaymentPenaltyParams.templates.size must be(6)
    }
  }

  trait Setup {
    val paymentPenaltyParams: PaymentPenaltyParams = PaymentPenaltyParams(
      latePaymentPenaltyRate = "5",
      amountOutstanding = "100",
      latePaymentTriggerDate = "2025-12-28",
      toPay = "800"
    )

    val paymentPenaltyParamsJsonString: String =
      """{
        |"latePaymentPenaltyRate":"5",
        |"amountOutstanding":"100",
        |"latePaymentTriggerDate":"2025-12-28",
        |"toPay":"800"
        |}""".stripMargin

    val paymentPenaltyParamsInvalidJsonString: String =
      """{
        |"amountOutstanding":"100",
        |"latePaymentTriggerDate":"2025-12-28",
        |"toPay":"800"
        |}""".stripMargin
  }
}
