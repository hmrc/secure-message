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
import uk.gov.hmrc.securemessage.TestData.{ TEST_CONTENT, TEST_ID, TEST_KEY_VALUE, TEST_NAME, TEST_TIME_INSTANT }

class ConversationMessageSpec extends SpecBase {
  "Json Reads" must {
    import ConversationMessage.messageFormat

    "read the json correctly" in new Setup {
      Json.parse(conversationMessageJsonString).as[ConversationMessage] mustBe conversationMessage
    }

    "throw exception for invalid json" in new Setup {
      intercept[JsResultException] {
        Json.parse(conversationMessageInvalidJsonString).as[ConversationMessage]
      }

      intercept[JsResultException] {
        Json.parse(conversationMessageInvalidJsonString1).as[ConversationMessage]
      }
    }
  }

  "Json Writes" must {
    "write the object correctly" in new Setup {
      Json.toJson(conversationMessage) mustBe Json.parse(conversationMessageJsonString)
    }
  }

  "referencesFormat" must {
    import ConversationMessage.referencesFormat

    "read the json correctly" in new Setup {
      Json.parse(referenceJsonString).as[Reference] mustBe reference
    }

    "throw exception for invalid json" in new Setup {
      intercept[JsResultException] {
        Json.parse(referenceInvalidJsonString).as[Reference]
      }

      intercept[JsResultException] {
        Json.parse("""{}""").as[Reference]
      }
    }

    "write the object correctly" in new Setup {
      Json.toJson(reference) mustBe Json.parse(referenceJsonString)
    }
  }

  trait Setup {
    val reference: Reference = Reference(typeName = TEST_NAME, value = TEST_KEY_VALUE)

    val referenceJsonString: String = """{"typeName":"test_name", "value":"test_key_value"}""".stripMargin
    val referenceInvalidJsonString: String = """{"value":"test_key_value"}""".stripMargin

    val conversationMessage: ConversationMessage = ConversationMessage(
      id = Some(TEST_ID),
      senderId = 101,
      created = TEST_TIME_INSTANT,
      content = TEST_CONTENT,
      reference = Some(reference)
    )

    val conversationMessageJsonString: String =
      """{
        |"id":"test_id",
        |"senderId":101,
        |"created":"1970-01-01T00:13:09.245+0000",
        |"content":"adfg#1456hjftwer==",
        |"reference":{"typeName":"test_name","value":"test_key_value"}
        |}""".stripMargin

    val conversationMessageInvalidJsonString: String =
      """{
        |"id":"test_id",
        |"created":"1970-01-01T00:13:09.245+0000",
        |"content":"adfg#1456hjftwer==",
        |"reference":{"typeName":"test_name","value":"test_key_value"}
        |}""".stripMargin

    val conversationMessageInvalidJsonString1: String =
      """{
        |"senderId":101,
        |"content":"adfg#1456hjftwer==",
        |"reference":{"typeName":"test_name","value":"test_key_value"}
        |}""".stripMargin

  }
}
