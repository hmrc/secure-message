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

package uk.gov.hmrc.securemessage.models

import uk.gov.hmrc.securemessage.{ SpecBase, TestData }
import play.api.libs.json.{ JsResultException, Json }
import uk.gov.hmrc.securemessage.TestData.{ TEST_HMRC_MTD_ITSA_VALUE, TEST_ID, TEST_NINO, TEST_SAUTR }

class TaxIdSpec extends SpecBase {

  "Json Reads" must {
    "read the json correctly" in new Setup {
      Json
        .parse(taxIdJsonString1)
        .as[TaxId] mustBe taxId
    }

    "throw exception for invalid json" in new Setup {
      intercept[JsResultException] {
        Json
          .parse(taxIdInvalidJsonString)
          .as[TaxId]
      }
    }
  }

  "Json Writes" must {
    "write the object correctly" in new Setup {
      Json.toJson(taxId) mustBe Json.parse(taxIdJsonString)
    }
  }

  trait Setup {
    val taxId: TaxId = TaxId(
      _id = TEST_ID,
      sautr = Some(TEST_SAUTR),
      nino = Some(TEST_NINO),
      hmrcMtdItsa = Some(TEST_HMRC_MTD_ITSA_VALUE)
    )

    val taxIdJsonString: String =
      """{"_id":"test_id","sautr":"1234567890","nino":"SJ123456A","hmrcMtdItsa":"X99999999999"}""".stripMargin

    val taxIdJsonString1: String =
      """{"_id":"test_id","sautr":"1234567890","nino":"SJ123456A","HMRC-MTD-IT":"X99999999999"}""".stripMargin

    val taxIdInvalidJsonString: String =
      """{"sautr":"1234567890","nino":"SJ123456A","hmrcMtdItsa":"X99999999999"}""".stripMargin
  }
}
