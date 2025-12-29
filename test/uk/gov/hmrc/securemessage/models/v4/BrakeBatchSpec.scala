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

package uk.gov.hmrc.securemessage.models.v4

import play.api.libs.json.{ JsResultException, Json }
import uk.gov.hmrc.securemessage.SpecBase
import uk.gov.hmrc.securemessage.TestData.{ TEST_CONTENT, TEST_DATE, TEST_EXT_REF_ID, TEST_IDENTIFIER_NAME, TEST_MSG_TYPE, TEST_SUBJECT, TEST_WELSH_CONTENT, TEST_WELSH_SUBJECT }

class BrakeBatchSpec extends SpecBase {

  "BrakeBatchMessage.brakeBatchMessageFormat" should {
    import BrakeBatchMessage.brakeBatchMessageFormat

    "read the json correctly" in new Setup {
      Json.parse(brakeBatchMessageJsonString).as[BrakeBatchMessage] mustBe brakeBatchMessage
    }

    "throw the exception for invalid json" in new Setup {
      intercept[JsResultException] {
        Json.parse(brakeBatchMessageInvalidJsonString).as[BrakeBatchMessage]
      }
    }

    "write the object correctly" in new Setup {
      Json.toJson(brakeBatchMessage) mustBe Json.parse(brakeBatchMessageJsonString)
    }
  }

  trait Setup {
    val brakeBatchMessage: BrakeBatchMessage = BrakeBatchMessage(
      subject = TEST_SUBJECT,
      welshSubject = TEST_WELSH_SUBJECT,
      content = TEST_CONTENT,
      welshContent = TEST_WELSH_CONTENT,
      externalRefId = TEST_EXT_REF_ID,
      messageType = TEST_MSG_TYPE,
      issueDate = TEST_DATE,
      taxIdentifierName = TEST_IDENTIFIER_NAME
    )

    val brakeBatchMessageJsonString: String =
      """{
        |"subject":"sub_test",
        |"welshSubject":"Nodyn atgoffa i ffeilio ffurflen Hunanasesiad",
        |"content":"adfg#1456hjftwer==",
        |"welshContent":"Q3lubnd5cyAtIDQyNTQxMDEzODQxNzQ5MTcxNDE=",
        |"externalRefId":"adfg1278",
        |"messageType":"letter",
        |"issueDate":"2025-12-20",
        |"taxIdentifierName":"test_name"
        |}""".stripMargin

    val brakeBatchMessageInvalidJsonString: String =
      """{
        |"welshSubject":"Nodyn atgoffa i ffeilio ffurflen Hunanasesiad",
        |"content":"adfg#1456hjftwer==",
        |"welshContent":"Q3lubnd5cyAtIDQyNTQxMDEzODQxNzQ5MTcxNDE=",
        |"externalRefId":"adfg1278",
        |"messageType":"letter",
        |"issueDate":"2025-12-20",
        |"taxIdentifierName":"test_name"
        |}""".stripMargin
  }
}
