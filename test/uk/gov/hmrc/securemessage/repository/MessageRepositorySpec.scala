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

package uk.gov.hmrc.securemessage.repository

import org.joda.time.DateTime
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.JodaWrites.{ JodaDateTimeWrites => _ }
import play.api.libs.json.{ JsObject, Json }
import play.api.test.Helpers.{ await, defaultAwaitTimeout }
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.securemessage.LetterNotFound
import uk.gov.hmrc.securemessage.helpers.Resources
import uk.gov.hmrc.securemessage.models.core.Letter._
import uk.gov.hmrc.securemessage.models.core.{ Identifier, Letter }
import scala.concurrent.ExecutionContext.Implicits.global

class MessageRepositorySpec extends PlaySpec with MongoSpecSupport with BeforeAndAfterEach {

  "A letter" should {
    "be returned for a participating enrolment" in new TestContext() {
      val result =
        await(
          repository.getLetter(objectID.stringify, Set(Identifier("EORINumber", "GB1234567890", Some("HMRC-CUS-ORG")))))
      result.right.get mustBe letter
    }

    "be returned for a participating enrolment with no name" in new TestContext() {
      val result =
        await(repository.getLetter(objectID.stringify, Set(Identifier("", "GB1234567890", Some("HMRC-CUS-ORG")))))
      result.right.get mustBe letter
    }

    "be returned for a participating enrolment without readTime timestamp" in new TestContextWithOutReadTime {
      val result =
        await(
          repository.getLetter(objectID.stringify, Set(Identifier("EORINumber", "GB1234567890", Some("HMRC-CUS-ORG")))))
      result.right.get.readTime mustBe None
    }

    "not be returned for a participating enrolment with no enrolment HMRC-CUS-ORG" in new TestContext() {
      val result =
        await(repository.getLetter(objectID.stringify, Set(Identifier("EORINumber", "GB1234567890", enrolment = None))))
      result.left.get.message mustBe "Letter not found"
    }

    "not be returned for a participating enrolment with different Enrolment" in {
      val objectID: BSONObjectID = BSONObjectID.generate()
      val letterJsonWithOutEnrolment = Resources.readJson("model/core/letterWithOutHmrcCusOrg.json").as[JsObject] +
        ("_id"         -> Json.toJson(objectID)) +
        ("lastUpdated" -> Json.toJson(DateTime.now()))
      val repository: MessageRepository = new MessageRepository()
      val letterWithoutEnrolment = letterJsonWithOutEnrolment.validate[Letter].get
      await(repository.insert(letterWithoutEnrolment))

      val result =
        await(
          repository.getLetter(objectID.stringify, Set(Identifier("EORINumber", "GB1234567890", Some("HMRC-CUS-ORG")))))

      result.left.get.message mustBe "Letter not found"
    }

    "not be returned if the enrolment is not a recipient" in new TestContextWithOutReadTime() {
      val result =
        await(
          repository.getLetter(objectID.stringify, Set(Identifier("EORINumber", "GB1234567891", Some("HMRC-CUS-ORG")))))
      result.left.get mustBe LetterNotFound("Letter not found")
    }

    "not be returned and BsonInInvalid" in new TestContext() {
      val result =
        await(repository.getLetter("12345678", Set()))
      result.left.get.message must include("Invalid BsonId")
    }

    "Update letter with new read time" should {
      "update readTime only if its empty" in new TestContextWithOutReadTime() {
        await(repository.addReadTime(objectID.stringify))
        val result =
          await(
            repository
              .getLetter(objectID.stringify, Set(Identifier("EORINumber", "GB1234567890", Some("HMRC-CUS-ORG")))))
        result.right.get.readTime must not be empty
      }
      "not update readTime if it already exists" in new TestContext(DateTime.parse("2020-11-10T15:00:01.000")) {
        await(repository.addReadTime(objectID.stringify))
        val result =
          await(
            repository
              .getLetter(objectID.stringify, Set(Identifier("EORINumber", "GB1234567890", Some("HMRC-CUS-ORG")))))
        result.right.get.readTime.get mustBe DateTime.parse("2020-11-10T15:00:01.000Z")
      }

      "return left if Bsonid is invalid" in new TestContext() {
        val result = await(repository.addReadTime("not valid id"))
        result.left.get.message must include("Wrong ObjectId")
      }

    }
  }

  class TestContext(val readTime: DateTime = DateTime.now()) {
    val objectID = BSONObjectID.generate()
    val letterJson = Resources.readJson("model/core/letter.json").as[JsObject] +
      ("_id"         -> Json.toJson(objectID)) +
      ("lastUpdated" -> Json.toJson(DateTime.now())) +
      ("readTime"    -> Json.toJson(readTime))
    val repository: MessageRepository = new MessageRepository()
    val letter = letterJson.validate[Letter].get
    await(repository.insert(letter))
  }

  class TestContextWithOutReadTime() {
    val objectID = BSONObjectID.generate()
    val letterJson = Resources.readJson("model/core/letter.json").as[JsObject] +
      ("_id"         -> Json.toJson(objectID)) +
      ("lastUpdated" -> Json.toJson(DateTime.now()))
    val repository: MessageRepository = new MessageRepository()
    val letter = letterJson.validate[Letter].get
    await(repository.insert(letter))
  }
}
