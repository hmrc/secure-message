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

package uk.gov.hmrc.securemessage.models.v4

import com.fasterxml.jackson.core.JsonParseException
import play.api.libs.json.{ JsResultException, Json }
import uk.gov.hmrc.securemessage.SpecBase

class AllowlistSpec extends SpecBase {

  "Json Reads" must {
    import Allowlist.format

    "read the json correctly" in new Setup {
      Json.parse(allowListJsonString).as[Allowlist] mustBe allowList
    }

    "throw exception for invalid json" in new Setup {
      intercept[JsResultException] {
        Json.parse(allowListInvalidJsonString1).as[Allowlist]
      }

      intercept[JsonParseException] {
        Json.parse(allowListInvalidJsonString).as[Allowlist]
      }
    }
  }

  "Json Writes" must {
    "write the object correctly" in new Setup {
      Json.toJson(allowList) mustBe Json.parse(allowListJsonString)
    }
  }

  trait Setup {
    val allowList: Allowlist = Allowlist(List("formId1", "formId2"))

    val allowListJsonString: String = """{"formIdList":["formId1","formId2"]}""".stripMargin
    val allowListInvalidJsonString: String = """{["formId1","formId2"]}""".stripMargin
    val allowListInvalidJsonString1: String = """{}""".stripMargin
  }
}
