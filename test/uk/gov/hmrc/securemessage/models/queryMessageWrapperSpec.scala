/*
 * Copyright 2022 HM Revenue & Customs
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

import org.joda.time.format.DateTimeFormat
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import uk.gov.hmrc.securemessage.helpers.Resources

class queryMessageWrapperSpec extends PlaySpec {

  "Validating a queryMessageWrapper" must {

    "be successful when generating a valid JSON payload" in {

      val dtf = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
      val dt = dtf.parseDateTime("2021-04-01T14:32:48Z")
      val queryMessageRequest = QueryMessageWrapper(
        QueryMessageRequest(
          requestCommon = RequestCommon(
            originatingSystem = "dc-secure-message",
            receiptDate = dt,
            acknowledgementReference = "acknowledgementReference"),
          requestDetail = RequestDetail(
            id = "govuk-tax-random-unique-id",
            conversationId = "D-26675-20210401",
            message = "dGhpcyBpcyBhIHRlc3Q=")
        ))

      val expectedJson = Resources
        .readString("model/eisRequest.json")
        .replaceAllLiterally("{{dateTime}}", dt.toString("yyyy-MM-dd'T'HH:mm:ss'Z'"))
      val generatedJson = Json.prettyPrint(Json.toJson[QueryMessageWrapper](queryMessageRequest))
      generatedJson mustBe expectedJson
    }
  }
}
