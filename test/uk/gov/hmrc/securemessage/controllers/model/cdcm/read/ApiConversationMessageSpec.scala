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

package uk.gov.hmrc.securemessage.controllers.model.cdcm.read

import play.api.libs.json.{ JsResultException, Json }
import uk.gov.hmrc.securemessage.SpecBase
import uk.gov.hmrc.securemessage.TestData.{ TEST_CONTENT, TEST_NAME, TEST_TIME_INSTANT }

class ApiConversationMessageSpec extends SpecBase {

  "Json Reads" must {
    import ApiConversationMessage.messageFormat

    "read the json correctly" in new Setup {
      Json.parse(apiConversationMessageJsonString).as[ApiConversationMessage] mustBe apiConversationMessage
    }

    "throw exception for invalid json" in new Setup {
      intercept[JsResultException] {
        Json.parse(apiConversationMessageInvalidJsonString).as[ApiConversationMessage]
      }
    }
  }

  "Json Writes" must {
    "write the object correctly" in new Setup {
      Json.toJson(apiConversationMessage) mustBe Json.parse(apiConversationMessageJsonString)
    }
  }

  trait Setup {
    val apiConversationMessage: ApiConversationMessage = ApiConversationMessage(
      senderInformation = Some(SenderInformation(name = Some(TEST_NAME), sent = TEST_TIME_INSTANT, self = true)),
      firstReader = Some(FirstReaderInformation(name = Some(TEST_NAME), read = TEST_TIME_INSTANT)),
      content = TEST_CONTENT
    )

    val apiConversationMessageJsonString: String =
      """{
        |"senderInformation":{"name":"test_name","sent":"1970-01-01T00:13:09.245+0000","self":true},
        |"firstReader":{"name":"test_name","read":"1970-01-01T00:13:09.245+0000"},
        |"content":"adfg#1456hjftwer=="
        |}""".stripMargin

    val apiConversationMessageInvalidJsonString: String =
      """{
        |"senderInformation":{"name":"test_name","sent":"1970-01-01T00:13:09.245+0000","self":true},
        |"firstReader":{"name":"test_name","read":"1970-01-01T00:13:09.245+0000"}
        |}""".stripMargin
  }
}
