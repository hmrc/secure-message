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

package uk.gov.hmrc.securemessage.models.core

import org.joda.time.DateTime
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{ JsObject, Json }
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.securemessage.controllers.model.cdsf.read.{ ApiLetter, FirstReaderInformation, SenderInformation }
import uk.gov.hmrc.securemessage.helpers.Resources
import uk.gov.hmrc.securemessage.models.core.Letter._

class LetterSpec extends PlaySpec {
  "Letter" must {
    "be parsed from json object" in {
      val objectID = BSONObjectID.generate
      val letterJson = Resources.readJson("model/core/letter.json").as[JsObject] +
        ("_id"         -> Json.toJson(objectID)) +
        ("lastUpdated" -> Json.toJson(DateTime.now())) +
        ("readTime"    -> Json.toJson(DateTime.now()))
      val letter = letterJson.validate[Letter].get
      val apiLetter = ApiLetter.fromCore(letter)
      apiLetter.subject mustBe (letter.subject)
      apiLetter.content mustBe (letter.content)
      apiLetter.senderInformation mustBe SenderInformation("HMRC", letter.validFrom)
      apiLetter.firstReaderInformation.get mustBe (FirstReaderInformation(None, letter.readTime.get))
    }

    "be parsed without readTime from json object" in {
      val objectID = BSONObjectID.generate
      val letterJson = Resources.readJson("model/core/letter.json").as[JsObject] +
        ("_id"         -> Json.toJson(objectID)) +
        ("lastUpdated" -> Json.toJson(DateTime.now()))
      val letter = letterJson.validate[Letter].get
      val apiLetter = ApiLetter.fromCore(letter)
      apiLetter.subject mustBe (letter.subject)
      apiLetter.content mustBe (letter.content)
      apiLetter.senderInformation mustBe SenderInformation("HMRC", letter.validFrom)
      apiLetter.firstReaderInformation mustBe None
    }
  }
}
