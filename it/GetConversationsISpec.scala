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
class GetConversationsISpec extends ISpec {

  "A GET request to /secure-messaging/conversations for a filtered query" should {

    "return a JSON body of conversation metadata when no filters are provided by leveraging auth enrolments" in {
      createConversation map { _ =>
        val response =
          wsClient
            .url(resource("/secure-messaging/conversations"))
            .withHttpHeaders(buildEoriToken(VALID_EORI))
            .get()
            .futureValue
        response.body must include("""senderName":"CDS Exports Team""")
        response.body must include("\"count\":1")
      }
    }

    "return a JSON body of conversation metadata when filtered by multiple tags" in {
      createConversation map { _ =>
        val response =
          wsClient
            .url(resource("/secure-messaging/conversations?tag=notificationType~CDS%20Exports&tag=sourceId~CDCM"))
            .withHttpHeaders(buildEoriToken(VALID_EORI))
            .get()
            .futureValue
        response.body must include("""senderName":"CDS Exports Team""")
        response.body must include("\"count\":1")
      }
    }

    "return a JSON body of conversation metadata when filtered by a single enrolment key" in {
      createConversation map { _ =>
        val response =
          wsClient
            .url(resource("/secure-messaging/conversations?enrolmentKey=HMRC-CUS-ORG"))
            .withHttpHeaders(buildEoriToken(VALID_EORI))
            .get()
            .futureValue
        response.body must include("""senderName":"CDS Exports Team""")
        response.body must include("\"count\":1")
      }
    }

    "return a JSON body of conversation metadata when filtered by multiple enrolment keys" in {
      createConversation map { _ =>
        val response =
          wsClient
            .url(resource("/secure-messaging/conversations?enrolmentKey=HMRC-CUS-ORG&enrolmentKey=SOME_ENROLMENT"))
            .withHttpHeaders(buildEoriToken(VALID_EORI))
            .get()
            .futureValue
        response.body must include("""senderName":"CDS Exports Team""")
        response.body must include("\"count\":1")
      }
    }

    "return a JSON body of conversation metadata when filtered by a single enrolment" in {
      createConversation map { _ =>
        val response =
          wsClient
            .url(resource("/secure-messaging/conversations?enrolment=HMRC-CUS-ORG~EORINumber~GB1234567890"))
            .withHttpHeaders(buildEoriToken(VALID_EORI))
            .get()
            .futureValue
        response.body must include("""senderName":"CDS Exports Team""")
        response.body must include("\"count\":1")
      }
    }

    "return a JSON body of conversation metadata when filtered by a multiple enrolments" in {
      createConversation map { _ =>
        val response =
          wsClient
            .url(resource(
              "/secure-messaging/conversations?enrolment=HMRC-CUS-ORG~EORINumber~GB1234567890&enrolment=SOMETHING~SomeName~A1233455646"))
            .withHttpHeaders(buildEoriToken(VALID_EORI))
            .get()
            .futureValue
        response.body must include("""senderName":"CDS Exports Team""")
        response.body must include("\"count\":1")
      }
    }

    "return a JSON body of conversation metadata when filtered by a single enrolment and a single enrolment key" in {
      createConversation map { _ =>
        val response =
          wsClient
            .url(resource(
              "/secure-messaging/conversations?enrolmentKey=HMRC-CUS-ORG&enrolment=HMRC-CUS-ORG~EORINumber~GB1234567890"))
            .withHttpHeaders(buildEoriToken(VALID_EORI))
            .get()
            .futureValue
        response.body must include("""senderName":"CDS Exports Team""")
        response.body must include("\"count\":1")
      }
    }

    "return an empty list JSON body when there's an auth session, but no enrolment matching the enrolment key filter" in {
      val response =
        wsClient
          .url(resource("/secure-messaging/conversations?enrolmentKey=SOME_ENROLMENT"))
          .withHttpHeaders(buildNonEoriToken)
          .get()
          .futureValue
      response.body must include("[]")
      response.status mustBe OK
    }

    "return an empty list JSON body when there's an auth session, but no enrolment matching the enrolment filter" in {
      val response =
        wsClient
          .url(resource("/secure-messaging/conversations?enrolment=SOME_ENROLMENT~SomeIdentifierName~A123456789"))
          .withHttpHeaders(buildNonEoriToken)
          .get()
          .futureValue
      response.body must include("[]")
      response.status mustBe OK
    }

    "return an empty list JSON body when there's an auth session, but no enrolment matching an enrolment nor enrolment key filter" in {
      val response =
        wsClient
          .url(resource(
            "/secure-messaging/conversations?enrolmentKey=SOME_ENROLMENT&enrolment=SOME_ENROLMENT~SomeIdentifierName~A123456789"))
          .withHttpHeaders(buildNonEoriToken)
          .get()
          .futureValue
      response.body must include("[]")
      response.status mustBe OK
    }

    "return a JSON body of [Invalid query parameter(s)] when there's an invalid parameter supplied in the query string" in {
      val response =
        wsClient
          .url(resource("/secure-messaging/conversations?abc=SOME_VALUE&a=1&b=2&c=3&d=4&e=5&f=6"))
          .withHttpHeaders(buildNonEoriToken)
          .get()
          .futureValue
      response.body mustBe "\"Invalid query parameter(s) found: [a, abc, b, c, d, e, f]\""
      response.status mustBe BAD_REQUEST
    }
  }

}
