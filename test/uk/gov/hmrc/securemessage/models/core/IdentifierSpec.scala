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

package uk.gov.hmrc.securemessage.models.core

import play.api.libs.json.{ JsResultException, Json }
import uk.gov.hmrc.securemessage.SpecBase
import uk.gov.hmrc.securemessage.TestData.{ TEST_ENROLMENT_VALUE, TEST_KEY_VALUE, TEST_NAME }

class IdentifierSpec extends SpecBase {

  "Json Reads" must {
    import Identifier.identifierFormat

    "read the json correctly" in new Setup {
      Json.parse(identifierJsonString).as[Identifier] mustBe identifier
    }

    "throw exception for invalid json" in new Setup {
      intercept[JsResultException] {
        Json.parse(identifierInvalidJsonString).as[Identifier]
      }
    }
  }

  "Json Writes" must {
    "write the object correctly" in new Setup {
      Json.toJson(identifier) mustBe Json.parse(identifierJsonString)
    }
  }

  trait Setup {
    val identifier: Identifier =
      Identifier(name = TEST_NAME, value = TEST_KEY_VALUE, enrolment = Some(TEST_ENROLMENT_VALUE))

    val identifierJsonString: String =
      """{"name":"test_name","value":"test_key_value","enrolment":"HMRC-ORG"}""".stripMargin

    val identifierInvalidJsonString: String =
      """{"value":"test_key_value","enrolment":"HMRC-ORG"}""".stripMargin
  }
}
