/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.securemessage

import org.scalatest.DoNotDiscover
import play.api.http.{ ContentTypes, HeaderNames }
import play.api.test.Helpers._

import java.io.File

@DoNotDiscover
@SuppressWarnings(Array("org.wartremover.warts.All"))
class GetMessagesISpec extends ISpec {

  "A GET request to /secure-messaging/messages for a filtered query" should {

    "return a JSON body of conversation metadata when no filters are provided by leveraging auth enrolments" in new TestClass {
      val response =
        wsClient
          .url(resource("/secure-messaging/messages"))
          .withHttpHeaders(buildEoriToken(VALID_EORI))
          .get()
          .futureValue

      response.status mustBe (200)
      response.body must include("""senderName":"CDS Exports Team""")
      response.body must include(""""count":1""")
      response.body must include(""""subject":"D-80542-20201120"""")
      response.body must include(""""client":"CDCM"""")
    }
  }

  class TestClass {
    wsClient
      .url(resource("/secure-messaging/conversation/CDCM/D-80542-20201120"))
      .withHttpHeaders((HeaderNames.CONTENT_TYPE, ContentTypes.JSON))
      .put(new File("./it/resources/cdcm/create-conversation.json"))
      .futureValue
      .status mustBe CREATED
  }
}
