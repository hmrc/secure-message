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
import uk.gov.hmrc.securemessage.TestData.{ TEST_EMAIL_ADDRESS, TEST_ID, TEST_IDENTIFIER, TEST_NAME }

class ParticipantSpec extends SpecBase {

  "Json Reads" must {
    "read the json correctly" in new Setup {
      Json.parse(participantJsonString).as[Participant] mustBe participant
    }

    "throw exception for invalid json" in new Setup {
      intercept[JsResultException] {
        Json.parse(participantInvalidJsonString).as[Participant]
      }
    }
  }

  "Json Writes" must {
    "write the object correctly" in new Setup {
      Json.toJson(participant) mustBe Json.parse(participantJsonString)
    }
  }

  trait Setup {
    val participant: Participant = Participant(
      id = 1,
      participantType = ParticipantType.Customer,
      identifier = TEST_IDENTIFIER,
      name = Some(TEST_NAME),
      email = Some(TEST_EMAIL_ADDRESS),
      parameters = None,
      readTimes = None
    )

    val participantJsonString: String =
      """{
        |"id":1,
        |"participantType":"customer",
        |"identifier":{"name":"test_name","value":"test_value","enrolment":"HMRC-CUS-ORG"},
        |"name":"test_name",
        |"email":"test@test.com"
        |}""".stripMargin

    val participantInvalidJsonString: String =
      """{
        |"participantType":"customer",
        |"identifier":{"name":"test_name","value":"test_value","enrolment":"HMRC-CUS-ORG"},
        |"name":"test_name",
        |"email":"test@test.com"
        |}""".stripMargin
  }
}
