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

package uk.gov.hmrc.securemessage.controllers

import org.joda.time.format.ISODateTimeFormat
import org.joda.time.{ DateTime, LocalDate }
import org.mongodb.scala.bson.ObjectId
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{ JsObject, Json }
import uk.gov.hmrc.common.message.model.MessagesCount
import uk.gov.hmrc.securemessage.controllers.model.MessagesResponse
import uk.gov.hmrc.securemessage.helpers.Resources
import uk.gov.hmrc.securemessage.models.core.Letter
import uk.gov.hmrc.securemessage.models.core.Letter._

class MessagesResponseSpec extends PlaySpec {

  def testLetter: Letter = {
    val objectID = new ObjectId()
    val letterJson = Resources.readJson("model/core/letter_for_message.json").as[JsObject] +
      ("_id"         -> Json.toJson(objectID)) +
      ("lastUpdated" -> Json.toJson(DateTime.now()))
    letterJson.validate[Letter].get
  }

  "messageHeaders" must {

    "be rendered correctly if only count is provided" in {
      Json.toJson(MessagesResponse(None, MessagesCount(823, 343))) mustBe Json.parse(
        """
          |{
          |   "count": {
          |     "total": 823,
          |     "unread": 343
          |   }
          |}
        """.stripMargin
      )
    }

    "be rendered correctly if single message is provided" in {

      val message1 = testLetter
      Json.toJson(MessagesResponse.fromMessages(Seq(message1))) mustBe Json.parse(
        s"""
           |{
           |   "count": {
           |     "total": 1,
           |     "unread": 1
           |   },
           |   "items": [
           |     ${expectedJsonListItemFor(message1)}
           |   ]
           |}
        """.stripMargin
      )
    }

    "be rendered correctly if read and unread messages are provided in newest first order" in {

      val message1 = testLetter
      val message2 = testLetter
      val message3 = testLetter.copy(readTime = Some(DateTime.now()))
      Json.toJson(
        MessagesResponse.fromMessages(
          Seq(message1, message2, message3)
        )
      ) mustBe Json.parse(
        s"""
           |{
           |   "count": {
           |     "total": 3,
           |     "unread": 2
           |   },
           |   "items": [
           |     ${expectedJsonListItemFor(message3)},
           |     ${expectedJsonListItemFor(message2)},
           |     ${expectedJsonListItemFor(message1)}
           |   ]
           |}
        """.stripMargin
      )
    }

    "filter list correctly with 2wsm and replies" in {
      val message1 = testLetter
      val message2 = testLetter
      val message3 = testLetter.copy(replyTo = Some(message1._id.toString))
      val message4 = testLetter.copy(replyTo = Some(message2._id.toString))
      val message5 = testLetter.copy(replyTo = Some(message3._id.toString))

      val msgLst = MessagesResponse.fromMessages(message1 :: message2 :: message3 :: message4 :: message5 :: Nil)

      val conv = msgLst.toConversations
      conv.count.total mustBe (2)
    }

    "filter list correctly without 2wsm and replies" in {
      val message1 = testLetter
      val message2 = testLetter

      val msgLst = MessagesResponse.fromMessages(message1 :: message2 :: Nil)
      val item1 = msgLst.items.get.find(_.id == message1._id.toString).get
      val item2 = msgLst.items.get.find(_.id == message2._id.toString).get

      val conv = msgLst.toConversations
      conv.items.get must be(List(item2, item1))
      conv.items.get.length mustBe (2)
    }

    "filter list correctly with nested replies" in {
      val message1 = testLetter
      val message2 = testLetter.copy(replyTo = Some(message1._id.toString))
      val message3 = testLetter.copy(replyTo = Some(message2._id.toString))

      val msgLst = MessagesResponse.fromMessages(message1 :: message2 :: message3 :: Nil)
      val item3 = msgLst.items.get.find(_.id == message3._id.toString).get

      val conv = msgLst.toConversations
      conv.items.get must be(List(item3.copy(counter = Some(3))))
      conv.items.get.length mustBe (1)
    }

    "filter list correctly with missing parent replies" in {
      val message1 = testLetter.copy(replyTo = Some("missing"), readTime = Some(DateTime.now()))

      val msgLst = MessagesResponse.fromMessages(message1 :: Nil)
      val item1 = msgLst.items.get.find(_.id == message1._id.toString).get

      val conv = msgLst.toConversations
      conv.items.get must be(List(item1.copy(counter = Some(1))))
      conv.items.get.length mustBe (1)
    }

    "filter list correctly with mixture of standlone and nested convesations" in {
      val message1 = testLetter
      val message2 = testLetter
      val message3 = testLetter.copy(replyTo = Some(message1._id.toString))
      val message4 = testLetter.copy(replyTo = Some(message2._id.toString))
      val message5 = testLetter.copy(replyTo = Some(message3._id.toString))
      val message6 = testLetter
      val message7 = testLetter.copy(replyTo = Some(message5._id.toString))

      val msgLst = MessagesResponse.fromMessages(
        message1 :: message2 :: message3 :: message4 :: message5 :: message6 :: message7 :: Nil
      )
      val item4 = msgLst.items.get.find(_.id == message4._id.toString).get
      val item6 = msgLst.items.get.find(_.id == message6._id.toString).get
      val item7 = msgLst.items.get.find(_.id == message7._id.toString).get

      val conv = msgLst.toConversations
      conv.items.get must be(List(item7.copy(counter = Some(4)), item6, item4.copy(counter = Some(2))))
      conv.count.total mustBe (3)
      conv.items.get.length mustBe (3)
    }

    "filter list correctly with 2wsm and replies when empty list" in {
      val msgLst = MessagesResponse.fromMessages(Nil)

      val conv = msgLst.toConversations
      conv.count.total mustBe (0)
      conv.items.get.length mustBe (0)
    }

  }

  def formatDate(date: LocalDate) = "%04d-%02d-%02d".format(date.getYear, date.getMonthOfYear, date.getDayOfMonth)
  def formatTime(time: DateTime) = ISODateTimeFormat.dateTime.withZoneUTC.print(time)

  def expectedJsonListItemFor(letter: Letter) = {
    val readTimeElement = letter.readTime.fold("")(time => s""""readTime": "${formatTime(time)}",""")

    val jsonString =
      s"""
         | {
         |   "id" : "${letter._id.toString}",
         |   "subject" : "${letter.subject}",
         |   "validFrom": "${formatDate(letter.validFrom)}",
         |   $readTimeElement
         |   "sentInError": false
         | }""".stripMargin

    Json.parse(jsonString).as[JsObject]
  }

}
