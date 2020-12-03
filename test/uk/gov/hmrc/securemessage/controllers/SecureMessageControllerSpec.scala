/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.securemessage.controllers

import akka.stream.Materializer
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.PlaySpec
import play.api.http.ContentTypes._
import play.api.http.HeaderNames._
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.test.Helpers.{ PUT, defaultAwaitTimeout, status }
import play.api.test.{ FakeHeaders, FakeRequest, Helpers, NoMaterializer }

class SecureMessageControllerSpec extends PlaySpec with ScalaFutures {

  implicit val mat: Materializer = NoMaterializer

  "Calling createConversation" should {
    "return CREATED (201) when sent a request with all optional fields populated" in {
      val controller = new SecureMessageController(Helpers.stubControllerComponents())
      val fakeRequest = FakeRequest(
        PUT,
        routes.SecureMessageController.createConversation("cdcm", "123").url,
        FakeHeaders(Seq(CONTENT_TYPE -> JSON)),
        Json.toJson(getFullConversationJson))
      val response = controller.createConversation("cdcm", "123")(fakeRequest)
      status(response) mustBe CREATED
    }

    "return CREATED (201) when sent a request with no optional fields populated" in {
      val controller = new SecureMessageController(Helpers.stubControllerComponents())
      val fakeRequest = FakeRequest(
        PUT,
        routes.SecureMessageController.createConversation("cdcm", "123").url,
        FakeHeaders(Seq(CONTENT_TYPE -> JSON)),
        Json.toJson(getMinimalConversationJson))
      val response = controller.createConversation("cdcm", "123")(fakeRequest)
      status(response) mustBe CREATED
    }

    "return BAD REQUEST (400) when sent a request with required fields missing" in {
      val controller = new SecureMessageController(Helpers.stubControllerComponents())
      val fakeRequest = FakeRequest(
        PUT,
        routes.SecureMessageController.createConversation("cdcm", "123").url,
        FakeHeaders(Seq(CONTENT_TYPE -> JSON)),
        Json.parse("""{"missing":"data"}""".stripMargin)
      )
      val response = controller.createConversation("cdcm", "123")(fakeRequest)
      status(response) mustBe BAD_REQUEST
    }
  }

  private def getFullConversationJson = Json.parse("""
                                                     |{
                                                     |    "sender": {"system": {
                                                     |        "name": "CDCM",
                                                     |        "parameters": {
                                                     |            "caseId": "12345",
                                                     |            "queryId": "ABC123"
                                                     |        },
                                                     |		    "display":"CDS Exports Team"
                                                     |    }},
                                                     |    "recipients": [{"customer": {
                                                     |        "enrolment": {
                                                     |            "key": "HMRC-CUS-ORG",
                                                     |            "name": "EORINumber",
                                                     |            "value": "GB1234567890"
                                                     |        },
                                                     |        "name": "Fred Smith",
                                                     |        "email": "fredsmith@test.com"
                                                     |    }}],
                                                     |    "alert": {
                                                     |        "templateId": "emailTemplateId",
                                                     |        "parameters": {
                                                     |          "param1": "value1",
                                                     |          "param2": "value2"
                                                     |        }
                                                     |    },
                                                     |    "tags": {
                                                     |        "sourceID": "123",
                                                     |        "caseId": "234",
                                                     |        "queryID": "345",
                                                     |        "mrn": "456",
                                                     |        "notificationType": "abc"
                                                     |    },
                                                     |    "subject": "Some subject",
                                                     |    "message": "QmxhaCBibGFoIGJsYWg=",
                                                     |    "language": "en"
                                                     |}
                                                     |""".stripMargin)

  private def getMinimalConversationJson = Json.parse("""
                                                        |{
                                                        |    "sender": {"system": {
                                                        |        "name": "CDCM",
                                                        |        "parameters": {
                                                        |            "caseId": "D-80542",
                                                        |            "conversationId": "D-80542-20201120"
                                                        |        },
                                                        |        "display":"CDS Exports Team"
                                                        |
                                                        |    }},
                                                        |    "recipients": [{"customer": {"enrolment": {
                                                        |        "key": "HMRC-CUS-ORG",
                                                        |        "name": "EORINumber",
                                                        |        "value": "GB1234567890"
                                                        |    }}}],
                                                        |    "alert": {"templateId": "emailTemplateId"},
                                                        |    "tags": {
                                                        |        "sourceID": "CDCM",
                                                        |        "caseId": "D-80542",
                                                        |        "conversationId": "D-80542-20201120",
                                                        |        "mrn": "DMS7324874993"
                                                        |    },
                                                        |    "subject": "D-80542-20201120",
                                                        |    "message": "QmxhaCBibGFoIGJsYWg="
                                                        |}
                                                        |""".stripMargin)

}
