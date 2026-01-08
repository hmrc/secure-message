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
import uk.gov.hmrc.securemessage.TestData.{ TEST_KEY, TEST_KEY_VALUE }

class FilterTagSpec extends SpecBase {

  "Json Reads" should {
    import FilterTag.tagReads

    "read the json correctly" in new Setup {
      Json.parse(filterTagJsonString).as[FilterTag] mustBe filterTag
    }

    "throw exception for the invalid json" in new Setup {
      intercept[JsResultException] {
        Json.parse(filterTagInvalidJsonString).as[FilterTag]
      }
    }
  }

  "parse" should {
    "return the correct output" in new Setup {
      FilterTag.parse("test_key~test_key_value") must be(Right(filterTag))
    }

    "be unable to bind the incorrect value" in {
      FilterTag.parse("test_key") must be(Left("Unable to bind a Tag"))
    }
  }

  trait Setup {
    val filterTag: FilterTag = FilterTag(key = TEST_KEY, value = TEST_KEY_VALUE)

    val filterTagJsonString: String = """{"key":"test_key", "value":"test_key_value"}""".stripMargin
    val filterTagInvalidJsonString: String = """{"value":"test_key_value"}""".stripMargin
  }
}
