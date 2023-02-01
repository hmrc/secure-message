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

package uk.gov.hmrc.securemessage.controllers.model

import org.mongodb.scala.bson.ObjectId
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import uk.gov.hmrc.common.message.model.MessagesCount
import uk.gov.hmrc.http.controllers.RestFormats
import uk.gov.hmrc.securemessage.controllers.model.common.read.MessageMetadata
import uk.gov.hmrc.securemessage.helpers.Resources
import uk.gov.hmrc.securemessage.models.core.Letter

//ToDo Add more tests for items in MessagesResponse
class MessagesResponseSpec extends PlaySpec with RestFormats {

  "MessagesResponse" must {
    val objectId = new ObjectId
    val letter1 = Resources.readJson("model/core/full-db-letter.json").as[Letter]
    val letter2 = letter1.copy(_id = objectId)
    val letters: Seq[Letter] = List(letter1, letter2)
    val messagesCount = MessagesCount(123, 23)
    val letterMetadata: MessageMetadata = Json.parse("""{
                                                       |"messageType": "letter",
                                                       |"id": "bGV0dGVyLzYwOWE1YmQ1MDEwMDAwNmMxODAwMjcyZA==",
                                                       |"subject": "Test have subjects11",
                                                       |"issueDate": "2021-04-26T11:00:00.000+0100",
                                                       |"senderName": "HMRC",
                                                       |"unreadMessages": false,
                                                       |"count": 1
                                                       |}""".stripMargin).as[MessageMetadata]

    "be rendered correctly if only count is provided" in {
      Json.toJson(MessagesResponse(None, messagesCount)) mustBe Json.parse(
        """
          |{
          |   "count": {
          |     "total": 123,
          |     "unread": 23
          |   }
          |}
        """.stripMargin
      )
    }

    "be rendered correctly if items & count is provided" in {
      Json.toJson(MessagesResponse(Some(List(letterMetadata)), messagesCount)) mustBe Json.parse(
        """
          |{
          |   "count": {
          |     "total": 123,
          |     "unread": 23
          |   },
          |  "items": [
          |    {
          |      "messageType": "letter",
          |      "id": "bGV0dGVyLzYwOWE1YmQ1MDEwMDAwNmMxODAwMjcyZA==",
          |      "subject": "Test have subjects11",
          |      "issueDate": "2021-04-26T11:00:00.000+0100",
          |      "senderName": "HMRC",
          |      "unreadMessages": false,
          |      "count": 1
          |    }
          |  ]
          |}
        """.stripMargin
      )
    }

    "creates message response from letters" in {
      Json.toJson(MessagesResponse.fromMessages(letters)) mustBe Json.parse(
        s"""
           |{
           |  "count": {
           |    "total": 2,
           |    "unread": 0
           |  },
           |  "items": [
           |    {
           |      "messageType": "letter",
           |      "id": "${objectId.toString}",
           |      "subject": "Test have subjects11",
           |      "issueDate": "2021-04-26T00:00:00.000+0000",
           |      "senderName": "HMRC",
           |      "unreadMessages": false,
           |      "count": 1,
           |      "taxpayerName": {
           |        "title": "Dr",
           |        "forename": "Bruce",
           |        "secondForename": "Hulk",
           |        "surname": "Banner",
           |        "honours": "Green",
           |        "line1": "Line1"
           |      },
           |      "validFrom": "2021-04-26",
           |      "readTime": "2021-05-11T10:26:29.509+0000",
           |      "sentInError": false
           |    },
           |    {
           |      "messageType": "letter",
           |      "id": "609a5bd50100006c1800272d",
           |      "subject": "Test have subjects11",
           |      "issueDate": "2021-04-26T00:00:00.000+0000",
           |      "senderName": "HMRC",
           |      "unreadMessages": false,
           |      "count": 1,
           |      "taxpayerName": {
           |        "title": "Dr",
           |        "forename": "Bruce",
           |        "secondForename": "Hulk",
           |        "surname": "Banner",
           |        "honours": "Green",
           |        "line1": "Line1"
           |      },
           |      "validFrom": "2021-04-26",
           |      "readTime": "2021-05-11T10:26:29.509+0000",
           |      "sentInError": false
           |    }
           |  ]
           |}
           |""".stripMargin
      )
    }

    "creates message response from count" in {
      Json.toJson(MessagesResponse.fromMessagesCount(messagesCount)) mustBe Json.parse(
        """
          |{
          |  "count": {
          |    "total": 123,
          |    "unread": 23
          |  }
          |}
          |""".stripMargin
      )
    }

    "convert into conversations from message response" in {
      val conversationResponse = MessagesResponse.fromMessages(letters).toConversations
      Json.toJson(conversationResponse) mustBe Json.parse(
        s"""
           |{
           |  "count": {
           |    "total": 2,
           |    "unread": 0
           |  },
           |  "items": [
           |    {
           |      "messageType": "letter",
           |      "id": "${objectId.toString}",
           |      "subject": "Test have subjects11",
           |      "issueDate": "2021-04-26T00:00:00.000+0000",
           |      "senderName": "HMRC",
           |      "unreadMessages": false,
           |      "count": 1,
           |      "taxpayerName": {
           |        "title": "Dr",
           |        "forename": "Bruce",
           |        "secondForename": "Hulk",
           |        "surname": "Banner",
           |        "honours": "Green",
           |        "line1": "Line1"
           |      },
           |      "validFrom": "2021-04-26",
           |      "readTime": "2021-05-11T10:26:29.509+0000",
           |      "sentInError": false
           |    },
           |    {
           |      "messageType": "letter",
           |      "id": "609a5bd50100006c1800272d",
           |      "subject": "Test have subjects11",
           |      "issueDate": "2021-04-26T00:00:00.000+0000",
           |      "senderName": "HMRC",
           |      "unreadMessages": false,
           |      "count": 1,
           |      "taxpayerName": {
           |        "title": "Dr",
           |        "forename": "Bruce",
           |        "secondForename": "Hulk",
           |        "surname": "Banner",
           |        "honours": "Green",
           |        "line1": "Line1"
           |      },
           |      "validFrom": "2021-04-26",
           |      "readTime": "2021-05-11T10:26:29.509+0000",
           |      "sentInError": false
           |    }
           |  ]
           |}
           |""".stripMargin
      )
    }
  }
}
