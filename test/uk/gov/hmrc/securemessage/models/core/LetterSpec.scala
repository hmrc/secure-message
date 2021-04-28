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

package uk.gov.hmrc.securemessage.models.core

import org.joda.time.DateTime
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{ JsObject, Json }
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.securemessage.helpers.Resources
import uk.gov.hmrc.securemessage.models.core.Letter._
//import uk.gov.hmrc.mongo.json.ReactiveMongoFormats.dateTimeFormats

class LetterSpec extends PlaySpec {

  "Letter" must {
    "be parsed from json object" in {

//      implicit val bsonDateWrites = new Writes[BSONDateTime] {
//        override def writes(bTime: BSONDateTime): JsValue = Json.obj("lastUpdated" -> new DateTime(bTime.value))
//      }

      val objectID = BSONObjectID.generate
      // val dateTime: BSONDateTime = BSONDateTime(DateTime.now().getMillis)

      // val test: BSONDateTime = new BSONDateTime(DateTime.now().getMillis)

      //   val test =BSONDateTime(DateTime.parse(("2021-04-26T15:28:36.622Z")).getMillis)

      val letterJson = Resources.readJson("model/core/letter.json").as[JsObject] +
        ("_id" -> Json.toJson(objectID)) + ("lastUpdated" -> Json.toJson(DateTime.now()))

      println(letterJson)

      val letter = letterJson.validate[Letter].get
      println(letter)
      //  letter mustBe Letter(objectID, "Message subject","2021-04-26","Lf=","DEFAULT","2021-04-26","succeeded", "<h2>Test content</h2>", false, "" )

    }

  }

}
