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

package uk.gov.hmrc.securemessage.controllers.model.cdcm

import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import org.mongodb.scala.bson.ObjectId
import org.scalatestplus.play.PlaySpec
import play.api.libs.json._
import uk.gov.hmrc.securemessage.controllers.model.cdcm.write.CdcmConversation
import uk.gov.hmrc.securemessage.helpers.Resources
import uk.gov.hmrc.securemessage.models.core.Conversation._
import uk.gov.hmrc.securemessage.models.core.{ Conversation, CustomerEnrolment, Reference }

class CdcmConversationSpec extends PlaySpec {

  "A full conversation API request model" should {
    val objectID = new ObjectId()

    "be converted correctly to a core conversation model" in {
      val xRequestId = "adsgr24frfvdc829r87rfsdf=="
      val randomId = "6e78776f-48ff-45bd-9da2-926e35519803"
      val reference = Reference("X-Request-ID", xRequestId)
      val formatter = ISODateTimeFormat.dateTime()
      val dateInString = "2020-11-10T15:00:01.000Z"
      val dateTime = DateTime.parse(dateInString, formatter)
      val fullConversationRequestJson: JsValue = Resources
        .readJson("model/api/cdcm/write/create-conversation.json")
        .as[JsObject] +
        ("_id" -> Json.toJson(objectID))
      fullConversationRequestJson.validate[CdcmConversation] match {
        case s: JsSuccess[CdcmConversation] =>
          val conversationRequest: CdcmConversation = s.getOrElse(fail("Unable to get conversation"))
          val conversation = conversationRequest
            .asConversationWithCreatedDate("CDCM", "D-80542-20201120", dateTime, randomId, Some(reference))
          val expectedConversationJson: JsValue = Resources
            .readJson("model/core/conversation.json")
            .as[JsObject] + ("_id" -> Json.toJson(objectID))
          expectedConversationJson.validate[Conversation] match {
            case success: JsSuccess[Conversation] =>
              val expectedConversation = success.getOrElse(fail("Unable to get conversation"))
              conversation.copy(_id = objectID) mustEqual expectedConversation
            case _: JsError => fail("There was a problem reading the core model JSON file")
          }
        case _: JsError => fail("There was a problem reading the api model JSON file")
      }
    }
  }

  "CustomerEnrolment request model" should {
    "parse a URL parameter for enrolment into its 3 part constituents" in {
      CustomerEnrolment.parse("HMRC-CUS-ORG~EoriNumber~GB1234567") mustEqual Right(
        CustomerEnrolment("HMRC-CUS-ORG", "EoriNumber", "GB1234567"))
    }

    "return an error for an incorrect URL enrolment parameter" in {
      CustomerEnrolment.parse("HMRC-CUS-ORGEoriNumberGB1234567") mustEqual Left("Unable to bind a CustomerEnrolment")
    }
    "parse a URL parameter for enrolment with 2 constituent parts" in {
      CustomerEnrolment.parse("HMRC-CUS-ORG~EoriNumberGB1234567") mustEqual Left("Unable to bind a CustomerEnrolment")
    }

  }

}
