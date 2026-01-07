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

package uk.gov.hmrc.securemessage.controllers.model

import play.api.libs.json.{ JsNumber, JsResultException, JsString, Json }
import uk.gov.hmrc.securemessage.SpecBase
import uk.gov.hmrc.securemessage.controllers.model.MessageType.{ Conversation, Letter }

class MessageTypeSpec extends SpecBase {

  "Json Reads" must {
    import MessageType.format

    "read the json correctly" in {
      JsString("Conversation").as[MessageType] mustBe Conversation
    }

    "throw exception for invalid json" in {
      intercept[JsResultException] {
        JsString("UNKNOWN").as[MessageType]
      }

      intercept[JsResultException] {
        JsNumber(100).as[MessageType]
      }
    }
  }

  "Json Writes" must {
    "write the object correctly" in {
      Json.toJson(Conversation) mustBe JsString("conversation")
      Json.toJson(Letter) mustBe JsString("letter")
    }
  }

  "withName" must {
    "return correct MessageType for a valid input" in {
      MessageType.withName("Conversation") mustBe Conversation
    }

    "throw exception for invalid input" in {
      intercept[NoSuchElementException] {
        MessageType.withName("unknown")
      }.getMessage mustBe "unknown is not a valid MessageType"
    }
  }
}
