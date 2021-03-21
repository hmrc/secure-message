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

import org.scalatest.DoNotDiscover
import play.api.test.Helpers._

@DoNotDiscover
@SuppressWarnings(Array("org.wartremover.warts.All"))
class GetIndividualConversationISpec extends ISpec {

  "A GET request to /secure-messaging/conversation/:client/:conversationId" should {

    "return a JSON body of api conversation with a list of api messages" in {
      createConversation map { _ =>
        val response =
          wsClient
            .url(resource("/secure-messaging/conversation/cdcm/D-80542-20201120"))
            .withHttpHeaders(buildEoriToken(VALID_EORI))
            .get()
            .futureValue
        response.body must include("""{"senderInformation":{"name":"CDS Exports Team"""")
      }
    }

    "return a JSON body of [No conversation found] when a conversationId does not match" in {
      createConversation map { _ =>
        val response =
          wsClient
            .url(resource("/secure-messaging/conversation/cdcm/D-80542-77777777"))
            .withHttpHeaders(buildEoriToken(VALID_EORI))
            .get()
            .futureValue
        response.body mustBe "\"No conversation found\""
      }
    }

    "return a JSON body of [No enrolment found] when auth session enrolments do not match a conversation's participants identifiers" in {
      val response =
        wsClient
          .url(resource("/secure-messaging/conversation/cdcm/D-80542-20201120"))
          .withHttpHeaders(buildNonEoriToken)
          .get()
          .futureValue
      response.status mustBe UNAUTHORIZED
      response.body mustBe "\"No enrolment found\""
    }
  }

}
