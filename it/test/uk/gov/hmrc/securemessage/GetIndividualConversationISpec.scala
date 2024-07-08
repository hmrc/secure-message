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
import play.api.libs.ws.readableAsString

@DoNotDiscover
class GetIndividualConversationISpec extends ISpec {

  "A GET request to /secure-messaging/conversation/:client/:conversationId" should {
    "return a JSON body of api conversation with a list of api messages" in new TestCase {
      val response =
        wsClient
          .url(resource(s"/secure-messaging/messages/$encodedId"))
          .withHttpHeaders(buildEoriToken(VALID_EORI))
          .get()
          .futureValue
      response.status mustBe 200
      response.body must include(""""name":"CDS Exports Team"""")
    }

    "return a JSON body of [No conversation found] when id doesn't exist" in {
      val id = new ObjectId()
      val encodedId = encodeId(id)
      createConversation map { _ =>
        val response =
          wsClient
            .url(resource(s"/secure-messaging/messages/$encodedId"))
            .withHttpHeaders(buildEoriToken(VALID_EORI))
            .get()
            .futureValue
        response.body mustBe "\"No conversation found\""
      }
    }

    "return a JSON body of [No enrolment found] when auth session enrolments do not match a conversation's participants identifiers" in new TestCase {
      val response =
        wsClient
          .url(resource(s"/secure-messaging/messages/$encodedId"))
          .withHttpHeaders(buildNonEoriToken)
          .get()
          .futureValue
      response.status mustBe 401
    }
  }

  class TestCase() {
    val id = new ObjectId()
    val encodedId = encodeId(id)
    insertConversation(id)
  }
}
