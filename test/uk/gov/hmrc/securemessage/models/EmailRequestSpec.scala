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

package uk.gov.hmrc.securemessage.models

import uk.gov.hmrc.securemessage.SpecBase
import uk.gov.hmrc.securemessage.TestData.{ TEST_EMAIL_ADDRESS, TEST_PARAMETERS, TEST_TAGS, TEST_TEMPLATE_ID }
import play.api.libs.json.Json

class EmailRequestSpec extends SpecBase {

  "Json Writes" should {
    "write the object correctly" in new Setup {
      Json.toJson(emailRequest) mustBe Json.parse(emailRequestJsonString)
    }
  }

  trait Setup {
    val emailRequest: EmailRequest = EmailRequest(
      to = List(TEST_EMAIL_ADDRESS),
      templateId = TEST_TEMPLATE_ID,
      parameters = TEST_PARAMETERS,
      tags = Some(TEST_TAGS)
    )

    val emailRequestJsonString: String =
      """{
        |"to":["test@test.com"],
        |"templateId":"test_template_id",
        |"parameters":{"test_name":"test_value"},
        |"tags":{"messageId":"1456hjftwer","source":"gmc","enrolment":"HMRC-ORG"},
        |"auditData":{}
        |}""".stripMargin
  }
}
