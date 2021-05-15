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
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{ JsObject, JsValue, Json }
import play.api.test.Helpers.{ await, defaultAwaitTimeout }
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.securemessage.{ MessageNotFound, SecureMessageError }
import uk.gov.hmrc.securemessage.helpers.Resources
import uk.gov.hmrc.securemessage.models.core.Letter._
import uk.gov.hmrc.securemessage.models.core.{ Count, FilterTag, Identifier, Letter }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class MessageRepositorySpec
    extends PlaySpec with MongoSpecSupport with BeforeAndAfterEach with ScalaFutures with StaticTestData {

  val repository: MessageRepository = new MessageRepository()

  override def beforeEach(): Unit =
    repository.removeAll().map(_ => ()).futureValue

  "A letter" should {
    "be returned for a participating enrolment" in new TestContext() {
      val result: Future[Either[SecureMessageError, Letter]] =
        repository.getLetter(objectID.stringify, identifiers)
      result.futureValue mustBe Right(letters.head)
    }

    "be returned for a participating enrolment with no name" in new TestContext() {
      val result: Either[SecureMessageError, Letter] =
        await(repository.getLetter(objectID.stringify, Set(Identifier("", "GB1234567890", Some("HMRC-CUS-ORG")))))
      result.right.get mustBe letters.head
    }

    "be returned for a participating enrolment without readTime timestamp" in new TestContext(
      coreLetters = lettersWithoutReadTime
    ) {
      val result: Either[SecureMessageError, Letter] =
        await(
          repository.getLetter(objectID.stringify, Set(Identifier("EORINumber", "GB1234567890", Some("HMRC-CUS-ORG")))))
      result.right.get.readTime mustBe None
    }

    "not be returned for a participating enrolment with no enrolment HMRC-CUS-ORG" in new TestContext() {
      val result: Either[SecureMessageError, Letter] =
        await(repository.getLetter(objectID.stringify, Set(Identifier("EORINumber", "GB1234567890", enrolment = None))))
      result.left.get.message mustBe "Letter not found for identifiers: Set(Identifier(EORINumber,GB1234567890,None))"
    }

    "not be returned for a participating enrolment with different Enrolment" in new TestContext(
      coreLetters = List(Resources.readJson("model/core/letterWithOutHmrcCusOrg.json").add(timeFields))) {
      val result: Either[SecureMessageError, Letter] = repository.getLetter(objectID.stringify, identifiers).futureValue

      result.left.get.message mustBe "Letter not found for identifiers: Set(Identifier(EORINumber,GB1234567890,Some(HMRC-CUS-ORG)))"
    }

    "not be returned if the enrolment is not a recipient" in new TestContext(
      coreLetters = List(Resources.readJson("model/core/letter.json").add(Seq(lastUpdatedField)))
    ) {
      val result: Either[SecureMessageError, Letter] =
        await(
          repository.getLetter(objectID.stringify, Set(Identifier("EORINumber", "GB1234567891", Some("HMRC-CUS-ORG")))))
      result.left.get mustBe MessageNotFound(
        "Letter not found for identifiers: Set(Identifier(EORINumber,GB1234567891,Some(HMRC-CUS-ORG)))")
    }

    "not be returned and BsonInInvalid" in new TestContext() {
      val result: Either[SecureMessageError, Letter] =
        await(repository.getLetter("12345678", Set()))
      result.left.get.message must include("Invalid BsonId")
    }
  }

  "Update letter with new read time" should {
    "update readTime only if its empty" in new TestContext(coreLetters = lettersWithoutReadTime) {
      await(repository.addReadTime(objectID.stringify))
      val result: Either[SecureMessageError, Letter] =
        await(
          repository
            .getLetter(objectID.stringify, Set(Identifier("EORINumber", "GB1234567890", Some("HMRC-CUS-ORG")))))
      result.right.get.readTime must not be empty
    }
    "not update readTime if it already exists" in new TestContext() {
      await(repository.addReadTime(objectID.stringify))
      val result: Either[SecureMessageError, Letter] =
        await(
          repository
            .getLetter(objectID.stringify, Set(Identifier("EORINumber", "GB1234567890", Some("HMRC-CUS-ORG")))))
      result.right.get.readTime mustBe letters.head.readTime
    }

    "return left if Bsonid is invalid" in new TestContext() {
      val result: Either[SecureMessageError, Unit] = await(repository.addReadTime("not valid id"))
      result.left.get.message must include("Wrong ObjectId")
    }

  }

  "getLetters" should {
    "return the letters for matching identifier enrolment and value " in new TestContext() {
      val result: Future[List[Letter]] = repository.getLetters(identifiers, None)
      result.futureValue mustBe letters
    }

    "return an empty list if no identifier enrolment matches" in new TestContext() {
      val result: Future[List[Letter]] =
        repository.getLetters(identifiers.map(i => i.copy(enrolment = Some("non-existing"))), None)
      result.futureValue mustBe empty
    }
    "return an empty list if no identifier value matches" in new TestContext() {
      val result: Future[List[Letter]] =
        repository.getLetters(identifiers.map(i => i.copy(value = "non-existing")), None)
      result.futureValue mustBe empty
    }
    "return letters ignoring identifier name matches" in new TestContext() {
      val result: Future[List[Letter]] =
        repository.getLetters(identifiers.map(i => i.copy(name = "non-existing")), None)
      result.futureValue mustBe letters
    }
    "return letters matching tags" in new TestContext() {
      val result: Future[List[Letter]] =
        repository.getLetters(identifiers, Some(List(FilterTag("notificationType", "Direct Debit"))))
      result.futureValue mustBe letters
    }
    "not return an empty list for non matching tags" in new TestContext() {
      val result: Future[List[Letter]] =
        repository.getLetters(identifiers, Some(List(FilterTag("notificationType", "non-existing"))))
      result.futureValue mustBe empty
    }
  }

  "getLettersCount" should {

    "return 0 total messages and 0 unread" in new TestContext(coreLetters = List()) {
      val result = repository.getLettersCount(identifiers, None)
      result.futureValue mustBe Count(0, 0)
    }

    "return 1 total messages and 0 unread" in new TestContext() {
      val result = repository.getLettersCount(identifiers, None)
      result.futureValue mustBe Count(1, 0)
    }

    "return 1 total messages and 1 unread" in new TestContext(coreLetters = lettersWithoutReadTime) {
      val result = repository.getLettersCount(identifiers, None)
      result.futureValue mustBe Count(1, 1)
    }
  }

  class TestContext(coreLetters: List[JsValue] = lettersWithTimeFields) {
    val objectID: BSONObjectID = BSONObjectID.generate()
    val letters: List[Letter] = coreLetters.map(_.add(Seq("_id" -> Json.toJson(objectID))).as[Letter])
    letters.map(letter => repository.insert(letter).futureValue)
  }
}

trait StaticTestData {
  val lastUpdatedField: (String, JsValue) = "lastUpdated" -> Json.toJson(DateTime.now())
  val readTimeField: (String, JsValue) = "readTime"       -> Json.toJson(DateTime.now())
  val timeFields = Seq(lastUpdatedField, readTimeField)
  val lettersWithTimeFields = List(Resources.readJson("model/core/letter.json").add(timeFields))
  val lettersWithoutReadTime = List(Resources.readJson("model/core/letter.json").add(Seq(lastUpdatedField)))
  val identifiers: Set[Identifier] = Set(Identifier("EORINumber", "GB1234567890", Some("HMRC-CUS-ORG")))

  implicit class JsonLetterExtensions(letter: JsValue) {
    def add(fields: Seq[(String, JsValue)]): JsObject =
      fields.foldLeft(letter.as[JsObject]) { (nextLetter, field) =>
        nextLetter + field
      }
    def remove(fields: Seq[String]): JsObject =
      fields.foldLeft(letter.as[JsObject]) { (nextLetter, field) =>
        nextLetter - field
      }
  }

}
