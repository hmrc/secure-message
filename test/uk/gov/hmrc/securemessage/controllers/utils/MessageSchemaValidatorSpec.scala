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

package uk.gov.hmrc.securemessage.controllers.utils

import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{ JsObject, JsSuccess }
import uk.gov.hmrc.securemessage.helpers.Resources

class MessageSchemaValidatorSpec extends PlaySpec with ScalaFutures with MessageSchemaValidator {

  "MessageSchemaValidator.isValidJson" must {

    "return success for the valid json " in {
      val messageJson = Resources.readJson("model/core/v4/valid_message.json").as[JsObject]
      isValidJson(messageJson) mustBe JsSuccess(messageJson)
    }

    "return error for the missing fields " in {
      val messageJson = Resources.readJson("model/core/v4/missing_mandatory_fields.json").as[JsObject]
      val result = isValidJson(messageJson)
      result.isError mustBe true
      val errorList = result.asEither.left.get.map(e => e._2.head.message)
      errorList mustBe List(
        "Property content missing.",
        "Property messageType missing.",
        "Property externalRef missing.")
    }
  }
}
