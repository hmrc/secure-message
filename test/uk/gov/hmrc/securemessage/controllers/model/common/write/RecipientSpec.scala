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

package uk.gov.hmrc.securemessage.controllers.model.common.write

import play.api.libs.json.{ JsResultException, Json }
import uk.gov.hmrc.securemessage.SpecBase
import uk.gov.hmrc.securemessage.TestData.{ TEST_KEY, TEST_KEY_VALUE, TEST_NAME }
import uk.gov.hmrc.securemessage.models.core.CustomerEnrolment

class RecipientSpec extends SpecBase {
  "Recipient.recipientReads" must {
    import Recipient.recipientReads

    "read the json correctly" in new Setup {
      Json.parse(recipientJsonString).as[Recipient] mustBe recipient
    }

    "throw exception for invalid json" in new Setup {
      intercept[JsResultException] {
        Json.parse(recipientInvalidJsonString).as[Recipient] mustBe recipient
      }
    }
  }

  "Customer.customerReads" must {
    import Customer.customerReads

    "read the json correctly" in new Setup {
      Json.parse(customerJsonString).as[Customer] mustBe customer
    }

    "throw exception for invalid json" in new Setup {
      intercept[JsResultException] {
        Json.parse(customerInvalidJsonString).as[Customer]
      }
    }
  }

  trait Setup {
    val customerEnrolment: CustomerEnrolment =
      CustomerEnrolment(key = TEST_KEY, name = TEST_NAME, value = TEST_KEY_VALUE)

    val customer: Customer = Customer(customerEnrolment)
    val recipient: Recipient = Recipient(customer)

    val customerJsonString: String =
      """{"enrolment":{"key":"test_key","name":"test_name","value":"test_key_value"}}""".stripMargin

    val customerInvalidJsonString: String =
      """{"enrolment":{"name":"test_name","value":"test_key_value"}}""".stripMargin

    val recipientJsonString: String =
      """{"customer":{"enrolment":{"key":"test_key","name":"test_name","value":"test_key_value"}}}""".stripMargin
    val recipientInvalidJsonString: String =
      """{"customer":{"enrolment":{"name":"test_name","value":"test_key_value"}}}""".stripMargin
  }
}
