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

import play.api.libs.json.{ JsNumber, JsResultException, JsString, Json }
import uk.gov.hmrc.securemessage.SpecBase
import uk.gov.hmrc.securemessage.models.core.ConversationStatus.{ Closed, Open }

class ConversationStatusSpec extends SpecBase {

  "Json Reads" must {
    "read the json correctly" in {
      JsString("Open").as[ConversationStatus] mustBe Open
    }

    "throw exception for invalid json" in {
      intercept[JsResultException] {
        JsString("Unknown").as[ConversationStatus]
      }

      intercept[JsResultException] {
        JsNumber(100).as[ConversationStatus]
      }
    }
  }

  "Json Writes" must {
    "write the object correctly" in {
      Json.toJson(Open) mustBe JsString("open")
      Json.toJson(Closed) mustBe JsString("closed")
    }
  }
}
