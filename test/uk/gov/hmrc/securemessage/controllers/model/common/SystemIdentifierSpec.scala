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

package uk.gov.hmrc.securemessage.controllers.model.common

import org.scalatest.matchers.must.Matchers.mustBe
import play.api.libs.json.{ JsResultException, Json }
import uk.gov.hmrc.securemessage.SpecBase
import uk.gov.hmrc.securemessage.TestData.{ TEST_IDENTIFIER_NAME, TEST_IDENTIFIER_VALUE }

class SystemIdentifierSpec extends SpecBase {

  "Json Reads" should {
    import SystemIdentifier.identifierReads

    "read the json correctly" in new Setup {
      Json.parse(systemIdentifierJsonString).as[SystemIdentifier] mustBe systemIdentifier
    }

    "throw the exception for invalid json" in new Setup {
      intercept[JsResultException] {
        Json.parse(systemIdentifierInvalidJsonString).as[SystemIdentifier]
      }
    }
  }

  trait Setup {
    val systemIdentifier: SystemIdentifier =
      SystemIdentifier(name = TEST_IDENTIFIER_NAME, value = TEST_IDENTIFIER_VALUE)

    val systemIdentifierJsonString: String = """{"name":"test_name", "value":"test_value"}""".stripMargin
    val systemIdentifierInvalidJsonString: String = """{"name1":"test_name", "value":"test_value"}""".stripMargin
  }
}
