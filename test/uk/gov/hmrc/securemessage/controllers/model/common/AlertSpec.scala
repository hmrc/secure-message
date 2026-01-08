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

package uk.gov.hmrc.securemessage.controllers.model.common

import uk.gov.hmrc.securemessage.SpecBase
import uk.gov.hmrc.securemessage.TestData.{ TEST_PARAMETERS, TEST_TEMPLATE_ID }
import play.api.libs.json.{ JsResultException, Json }

class AlertSpec extends SpecBase {

  "Json Reads" must {
    import Alert.alertReads

    "read the json correctly" in new Setup {
      Json.parse(alertJsonString).as[Alert] mustBe alert
    }

    "throw the exception for invalid json" in new Setup {
      intercept[JsResultException] {
        Json.parse(alertInvalidJsonString).as[Alert]
      }
    }
  }

  trait Setup {
    val alert: Alert = Alert(templateId = TEST_TEMPLATE_ID, parameters = Some(TEST_PARAMETERS))

    val alertJsonString: String =
      """{"templateId":"test_template_id","parameters":{"test_name":"test_value"}}""".stripMargin

    val alertInvalidJsonString: String = """{}""".stripMargin
  }
}
