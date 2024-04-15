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

package uk.gov.hmrc.securemessage.models

import java.time.format.DateTimeFormatter
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import uk.gov.hmrc.securemessage.helpers.Resources

import java.time.{ Instant, ZoneOffset }

class queryMessageWrapperSpec extends PlaySpec {

  "Validating a queryMessageWrapper" must {

    "be successful when generating a valid JSON payload" in {

      val dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
      val dt = Instant.parse("2021-04-01T14:32:48Z")
      val queryMessageRequest = QueryMessageWrapper(
        QueryMessageRequest(
          requestCommon = RequestCommon(
            originatingSystem = "dc-secure-message",
            receiptDate = dt,
            acknowledgementReference = "acknowledgementReference"
          ),
          requestDetail = RequestDetail(
            id = "govuk-tax-random-unique-id",
            conversationId = "D-26675-20210401",
            message = "dGhpcyBpcyBhIHRlc3Q="
          )
        )
      )

      val expectedJson = Resources
        .readString("model/eisRequest.json")
        .replace("{{dateTime}}", dt.atOffset(ZoneOffset.UTC).format(dtf))
      val generatedJson = Json.prettyPrint(Json.toJson[QueryMessageWrapper](queryMessageRequest))
      generatedJson mustBe expectedJson
    }
  }
}
