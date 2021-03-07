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

package uk.gov.hmrc.securemessage.controllers.models.generic

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{ JsValue, Json }
import uk.gov.hmrc.securemessage.helpers.Resources
import uk.gov.hmrc.securemessage.models.core._

@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
class ApiConversationSpec extends PlaySpec {

  "ApiConversation" must {
    "Convert core conversation to ApiConversation details when hmrc sends first message to customer" in {
      val identifier = Identifier(name = "EORINumber", value = "GB1234567890", enrolment = Some("HMRC-CUS-ORG"))
      val conversationJson: JsValue = Resources.readJson("model/core/hmrc-sent-first-message-core.json")
      val coreConversation: Conversation = conversationJson.validate[Conversation].get
      val apiConversation = ApiConversation.coreConversationToApiConversation(coreConversation, identifier)
      apiConversation mustBe a[ApiConversation]
      Json.toJson(apiConversation) mustBe Json.parse("""{
                                                       |    "client": "cdcn",
                                                       |    "conversationId": "1111",
                                                       |    "status": "open",
                                                       |    "tags": {
                                                       |        "queryId": "D-80542-20201120",
                                                       |        "caseId": "D-80542",
                                                       |        "notificationType": "CDS Exports",
                                                       |        "mrn": "DMS7324874993",
                                                       |        "sourceId": "CDCM"
                                                       |    },
                                                       |    "subject": "Subject content",
                                                       |    "language": "en",
                                                       |    "messages": [
                                                       |        {
                                                       |            "senderInformation": {
                                                       |                "name": "CDS Exports Team",
                                                       |                "sent": "2021-03-02T08:36:34.604+0000",
                                                       |                "self": false
                                                       |            },
                                                       |            "content": "QmxhaCBibGFoIGJsYWg="
                                                       |        }
                                                       |    ]
                                                       |}
                                                       |""".stripMargin)

    }
    "Convert core conversation to ApiConversation details with read time by same user" in {
      val identifier = Identifier(name = "EORINumber", value = "GB1234567890", enrolment = Some("HMRC-CUS-ORG"))
      val conversationJson: JsValue = Resources.readJson("model/core/customer-first-read-core.json")
      val coreConversation: Conversation = conversationJson.validate[Conversation].get
      val apiConversation = ApiConversation.coreConversationToApiConversation(coreConversation, identifier)
      apiConversation mustBe a[ApiConversation]
      Json.toJson(apiConversation) mustBe Json.parse("""{
                                                       |    "client": "cdcm",
                                                       |    "conversationId": "1111",
                                                       |    "status": "open",
                                                       |    "subject": "D-80542-20201120",
                                                       |    "language": "en",
                                                       |    "messages": [
                                                       |        {
                                                       |            "senderInformation": {
                                                       |                "name": "CDS Exports Team",
                                                       |                "sent": "2021-03-02T08:56:57.542+0000",
                                                       |                "self": false
                                                       |            },
                                                       |            "content": "QmxhaCBibGFoIGJsYWg="
                                                       |        }
                                                       |    ]
                                                       |}
                                                       |""".stripMargin)

    }
    "Convert core conversation to ApiConversation details with 2 messages that includes customer message" in {
      val identifier = Identifier(name = "EORINumber", value = "GB1234567890", enrolment = Some("HMRC-CUS-ORG"))
      val conversationJson: JsValue = Resources.readJson("model/core/customer-sent-message-core.json")
      val coreConversation: Conversation = conversationJson.validate[Conversation].get
      val apiConversation = ApiConversation.coreConversationToApiConversation(coreConversation, identifier)
      apiConversation mustBe a[ApiConversation]
      Json.toJson(apiConversation) mustBe Json.parse("""{
                                                       |    "client": "cdcm",
                                                       |    "conversationId": "1111",
                                                       |    "status": "open",
                                                       |    "subject": "D-80542-20201120",
                                                       |    "language": "en",
                                                       |    "messages": [
                                                       |        {
                                                       |            "senderInformation": {
                                                       |                "name": "CDS Exports Team",
                                                       |                "sent": "2021-03-02T08:56:57.542+0000",
                                                       |                "self": false
                                                       |            },
                                                       |            "content": "QmxhaCBibGFoIGJsYWg="
                                                       |        },
                                                       |        {
                                                       |            "senderInformation": {
                                                       |                "sent": "2021-03-02T09:18:45.308+0000",
                                                       |                "self": true
                                                       |            },
                                                       |            "content": "customer message content!!"
                                                       |        }
                                                       |    ]
                                                       |}
                                                       |""".stripMargin)
    }

    "Convert core conversation to ApiConversation details when first read by different user" in {

      val identifier = Identifier(name = "EORINumber", value = "GB1234567890", enrolment = Some("HMRC-CUS-ORG"))
      val conversationJson: JsValue = Resources.readJson("model/core/read-by-other-user-core.json")
      val coreConversation: Conversation = conversationJson.validate[Conversation].get
      val apiConversation = ApiConversation.coreConversationToApiConversation(coreConversation, identifier)
      apiConversation mustBe a[ApiConversation]
      Json.toJson(apiConversation) mustBe Json.parse("""{
                                                       |    "client": "cdcm",
                                                       |    "conversationId": "1111",
                                                       |    "status": "open",
                                                       |    "subject": "D-80542-20201120",
                                                       |    "language": "en",
                                                       |    "messages": [
                                                       |        {
                                                       |            "senderInformation": {
                                                       |                "name": "CDS Exports Team",
                                                       |                "sent": "2021-02-02T08:56:57.542+0000",
                                                       |                "self": false
                                                       |            },
                                                       |            "firstReader": {
                                                       |                "read": "2021-03-03T12:52:09.011+0000"
                                                       |            },
                                                       |            "content": "QmxhaCBibGFoIGJsYWg="
                                                       |        },
                                                       |        {
                                                       |            "senderInformation": {
                                                       |                "sent": "2021-02-02T09:18:45.308+0000",
                                                       |                "self": false
                                                       |            },
                                                       |            "firstReader": {
                                                       |                "name": "CDS Exports Team",
                                                       |                "read": "2021-03-03T12:52:09.011+0000"
                                                       |            },
                                                       |            "content": "customer message content!!"
                                                       |        },
                                                       |        {
                                                       |            "senderInformation": {
                                                       |                "sent": "2021-02-03T09:18:45.308+0000",
                                                       |                "self": true
                                                       |            },
                                                       |            "content": "customer message content!!"
                                                       |        }
                                                       |    ]
                                                       |}
                                                       |""".stripMargin)

    }
    "Convert core conversation to ApiConversation details that has random messages from different participants" in {
      val identifier = Identifier(name = "EORINumber", value = "GB1234567890", enrolment = Some("HMRC-CUS-ORG"))
      val conversationJson: JsValue = Resources.readJson("model/core/conversation-full-extender.json")
      val coreConversation: Conversation = conversationJson.validate[Conversation].get
      val apiConversation = ApiConversation.coreConversationToApiConversation(coreConversation, identifier)
      apiConversation mustBe a[ApiConversation]
      Json.toJson(apiConversation) mustBe Json.parse("""{
                                                       |  "client": "cdcm",
                                                       |  "conversationId": "D-80542-20201120",
                                                       |  "status": "open",
                                                       |  "tags": {
                                                       |    "queryId": "D-80542-20201120",
                                                       |    "caseId": "D-80542",
                                                       |    "notificationType": "CDS Exports",
                                                       |    "mrn": "DMS7324874993",
                                                       |    "sourceId": "CDCM"
                                                       |  },
                                                       |  "subject": "MRN: 19GB4S24GC3PPFGVR7",
                                                       |  "language": "en",
                                                       |  "messages": [
                                                       |    {
                                                       |      "senderInformation": {
                                                       |        "name": "CDS Exports Team",
                                                       |        "sent": "2020-11-10T15:01:00.000+0000",
                                                       |        "self": false
                                                       |      },
                                                       |      "firstReader": {
                                                       |        "name": "Joe Bloggs",
                                                       |        "read": "2020-11-10T15:01:02.000+0000"
                                                       |      },
                                                       |      "content": "QmxhaCBibGFoIGJsYWg="
                                                       |    },
                                                       |    {
                                                       |      "senderInformation": {
                                                       |        "name": "Joe Bloggs",
                                                       |        "sent": "2020-11-10T15:05:00.000+0000",
                                                       |        "self": true
                                                       |      },
                                                       |      "content": "QmxhaCBibGFoIGJsYWg="
                                                       |    },
                                                       |    {
                                                       |      "senderInformation": {
                                                       |        "name": "James Smith",
                                                       |        "sent": "2020-11-10T15:12:00.000+0000",
                                                       |        "self": false
                                                       |      },
                                                       |      "firstReader": {
                                                       |        "name": "Joe Bloggs",
                                                       |        "read": "2020-11-10T15:13:01.000+0000"
                                                       |      },
                                                       |      "content": "QmxhaCBibGFoIGJsYWg="
                                                       |    },
                                                       |    {
                                                       |      "senderInformation": {
                                                       |        "name": "Joe Bloggs",
                                                       |        "sent": "2020-11-10T15:18:00.000+0000",
                                                       |        "self": true
                                                       |      },
                                                       |      "content": "QmxhaCBibGFoIGJsYWg="
                                                       |    },
                                                       |    {
                                                       |      "senderInformation": {
                                                       |        "name": "Joe Bloggs",
                                                       |        "sent": "2020-11-10T15:30:00.000+0000",
                                                       |        "self": true
                                                       |      },
                                                       |      "content": "QmxhaCBibGFoIGJsYWg="
                                                       |    },
                                                       |    {
                                                       |      "senderInformation": {
                                                       |        "name": "James Smith",
                                                       |        "sent": "2020-11-10T15:35:00.000+0000",
                                                       |        "self": false
                                                       |      },
                                                       |      "content": "QmxhaCBibGFoIGJsYWg="
                                                       |    },
                                                       |    {
                                                       |      "senderInformation": {
                                                       |        "name": "CDS Exports Team",
                                                       |        "sent": "2020-11-10T15:42:00.000+0000",
                                                       |        "self": false
                                                       |      },
                                                       |      "content": "QmxhaCBibGFoIGJsYWg="
                                                       |    },
                                                       |    {
                                                       |      "senderInformation": {
                                                       |        "name": "James Smith",
                                                       |        "sent": "2020-11-10T16:00:00.000+0000",
                                                       |        "self": false
                                                       |      },
                                                       |      "content": "QmxhaCBibGFoIGJsYWg="
                                                       |    }
                                                       |  ]
                                                       |}""".stripMargin)
    }
  }
}
