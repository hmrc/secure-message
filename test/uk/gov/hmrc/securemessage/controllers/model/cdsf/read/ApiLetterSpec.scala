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

package uk.gov.hmrc.securemessage.controllers.model.cdsf.read

import play.api.libs.json.{ JsResultException, Json }
import uk.gov.hmrc.securemessage.SpecBase
import uk.gov.hmrc.securemessage.TestData.{ TEST_CONTENT, TEST_DATE, TEST_IDENTIFIER, TEST_IDENTIFIER_NAME, TEST_SUBJECT, TEST_TIME_INSTANT }

class ApiLetterSpec extends SpecBase {

  "Json Reads" should {
    "read the json correctly" in new Setup {
      Json.parse(apiLetterJsonString).as[ApiLetter] mustBe apiLetter
    }

    "throw the exception for invalid json" in new Setup {
      intercept[JsResultException] {
        Json.parse(apiLetterInvalidJsonString).as[ApiLetter]
      }
    }
  }

  "Json Writes" should {
    "write the object correctly" in new Setup {
      Json.toJson(apiLetter) mustBe Json.parse(apiLetterJsonString)
    }
  }

  trait Setup {
    val firstReaderInformation: FirstReaderInformation =
      FirstReaderInformation(name = Some(TEST_IDENTIFIER_NAME), read = TEST_TIME_INSTANT)

    val senderInformation: SenderInformation = SenderInformation(name = TEST_IDENTIFIER_NAME, sent = TEST_DATE)

    val apiLetter: ApiLetter = ApiLetter(
      subject = TEST_SUBJECT,
      content = TEST_CONTENT,
      firstReaderInformation = Some(firstReaderInformation),
      senderInformation = senderInformation,
      identifier = TEST_IDENTIFIER
    )

    val apiLetterJsonString: String =
      """{
        |"subject":"sub_test",
        |"content":"adfg#1456hjftwer==",
        |"firstReaderInformation":{"name":"test_name","read":"1970-01-01T00:13:09.245+0000"},
        |"senderInformation":{"name":"test_name","sent":"2025-12-20"},
        |"identifier":{"name":"test_name","value":"test_value","enrolment":"HMRC-CUS-ORG"}
        |}""".stripMargin

    val apiLetterInvalidJsonString: String =
      """{
        |"subject":"sub_test",
        |"content":"adfg#1456hjftwer==",
        |"firstReaderInformation":{"name":"test_name"},
        |"senderInformation":{"name":"test_name"},
        |"identifier":{"name":"test_name","value":"test_value","enrolment":"HMRC-CUS-ORG"}
        |}""".stripMargin
  }
}
