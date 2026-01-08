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

import play.api.libs.json.{ JsResultException, Json }
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.securemessage.SpecBase
import uk.gov.hmrc.securemessage.TestData.TEST_TEMPLATE_ID

class MobileNotificationSpec extends SpecBase {

  "Json Reads" should {
    import MobileNotification.mobileNotificationFormats

    "read the json correctly" in new Setup {
      Json.parse(mobileNotificationWithTaxIdJsonString).as[MobileNotification] mustBe mobileNotification
    }

    "throw the exception for invalid json" in new Setup {
      intercept[JsResultException] {
        Json.parse(mobileNotificationInvalidJsonString).as[MobileNotification]
      }
    }
  }

  "Json Writes" should {
    "write the object correctly" in new Setup {
      Json.toJson(mobileNotification) mustBe Json.parse(mobileNotificationJsonString)
    }
  }

  trait Setup {
    val mobileNotification: MobileNotification =
      MobileNotification(identifier = Nino("SJ123456A"), templateId = TEST_TEMPLATE_ID)

    val mobileNotificationJsonString: String =
      """{"identifier":{"nino":"SJ123456A"},"templateId":"test_template_id"}""".stripMargin

    val mobileNotificationWithTaxIdJsonString: String =
      """{"identifier":{"name":"nino","value":"SJ123456A"},"templateId":"test_template_id"}""".stripMargin

    val mobileNotificationInvalidJsonString: String =
      """{"templateId":"test_template_id"}""".stripMargin
  }
}
