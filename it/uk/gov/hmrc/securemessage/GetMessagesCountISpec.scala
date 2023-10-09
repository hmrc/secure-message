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

package uk.gov.hmrc.securemessage

import org.mongodb.scala.bson.ObjectId
import org.scalatest.DoNotDiscover
import play.api.http.{ ContentTypes, HeaderNames }
import play.api.test.Helpers._

import java.io.File

@DoNotDiscover
class GetMessagesCountISpec extends ISpec {

  "A GET request to /secure-messaging/messages/count for a filtered query" should {

    "return a JSON body of count of one unread message when no filters are provided by leveraging auth enrolments" in new TestClass {
      val response =
        wsClient
          .url(resource(s"/secure-messaging/messages/count?enrolment=HMRC-CUS-ORG%7EEoriNumber%7E$VALID_EORI"))
          .withHttpHeaders(buildEoriToken(VALID_EORI))
          .get()
          .futureValue

      response.status mustBe (200)
      response.body must be("""{"total":1,"unread":1}""")
    }

    "return a JSON body of count of no unread messages when no filters are provided by leveraging auth enrolments" in {
      val id = new ObjectId()
      val encodedId = encodeId(id)
      insertConversation(id)
      wsClient
        .url(resource(s"/secure-messaging/messages/$encodedId"))
        .withHttpHeaders(buildEoriToken(VALID_EORI))
        .get()
        .futureValue
        .status mustBe (200)

      val response =
        wsClient
          .url(resource(s"/secure-messaging/messages/count?enrolment=HMRC-CUS-ORG%7EEoriNumber%7E$VALID_EORI"))
          .withHttpHeaders(buildEoriToken(VALID_EORI))
          .get()
          .futureValue

      response.status mustBe (200)
      response.body must be("""{"total":1,"unread":0}""")
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
