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

package uk.gov.hmrc.securemessage.repository

import java.time.{ Instant, LocalDate }
import org.mongodb.scala.bson.ObjectId
import org.mongodb.scala.model.Filters
import org.mongodb.scala.SingleObservableFuture
import org.scalatest.EitherValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{ JsObject, JsValue, Json }
import play.api.test.Helpers.{ await, defaultAwaitTimeout }
import uk.gov.hmrc.common.message.model.{ MessagesCount, SystemTimeSource, TimeSource }
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.securemessage.helpers.Resources
import uk.gov.hmrc.securemessage.models.core.Letter.*
import uk.gov.hmrc.securemessage.models.core.{ ExternalReference, FilterTag, Identifier, Letter, RenderUrl }
import uk.gov.hmrc.securemessage.{ MessageNotFound, SecureMessageError }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ Await, Future }
import scala.concurrent.duration.*

class MessageRepositorySpec
    extends PlaySpec with DefaultPlayMongoRepositorySupport[Letter] with ScalaFutures with StaticTestData
    with EitherValues {

  Await.result(mongoComponent.database.drop().toFuture(), 5.seconds)
  override def checkTtlIndex: Boolean = false

  val timeSource: TimeSource = new TimeSource() {
    override def now(): Instant = SystemTimeSource.now()
  }

  override val repository: MessageRepository = new MessageRepository(mongoComponent, timeSource)

  "A letter" should {
    "be returned for a participating enrolment" in new TestContext() {
      val result: Either[SecureMessageError, Letter] =
        await(repository.getLetter(objectID, identifiers))
      result mustBe Right(letter)
    }

    "be returned for a participating enrolment with no name" in new TestContext() {
      val result: Either[SecureMessageError, Letter] =
        await(repository.getLetter(objectID, Set(Identifier("", "GB1234567890", Some("HMRC-CUS-ORG")))))
      result.toOption.get mustBe letter
    }

    "be returned for a participating enrolment without readTime timestamp" in new TestContext(
      coreLetters = lettersWithoutReadTime
    ) {
      val result: Either[SecureMessageError, Letter] =
        await(repository.getLetter(objectID, Set(Identifier("EORINumber", "GB1234567890", Some("HMRC-CUS-ORG")))))
      result.toOption.get.readTime mustBe None
    }

    "not be returned for a participating enrolment with no enrolment HMRC-CUS-ORG" in new TestContext() {
      val result: Either[SecureMessageError, Letter] =
        await(repository.getLetter(objectID, Set(Identifier("EORINumber", "GB1234567890", enrolment = None))))
      result.left.value.message mustBe "Letter not found for identifiers: Set(Identifier(EORINumber,GB1234567890,None))"
    }

    "not be returned for a participating enrolment with different Enrolment" in new TestContext(
      coreLetters = Resources.readJson("model/core/letterWithOutHmrcCusOrg.json").add(timeFields)
    ) {
      val result: Either[SecureMessageError, Letter] = await(repository.getLetter(objectID, identifiers))

      result.left.value.message mustBe "Letter not found for identifiers: Set(Identifier(EORINumber,GB1234567890,Some(HMRC-CUS-ORG)))"
    }

    "not be returned if the enrolment is not a recipient" in new TestContext(
      coreLetters = Resources.readJson("model/core/letter.json").add(Seq(lastUpdatedField))
    ) {
      val result: Either[SecureMessageError, Letter] =
        await(repository.getLetter(objectID, Set(Identifier("EORINumber", "GB1234567891", Some("HMRC-CUS-ORG")))))
      result.left.value mustBe MessageNotFound(
        "Letter not found for identifiers: Set(Identifier(EORINumber,GB1234567891,Some(HMRC-CUS-ORG)))"
      )
    }

    "be returned with renderUrl updated for ats-message-renderer type" in new TestContext() {
      val messageId = new ObjectId
      val atsRenderUrl: RenderUrl = RenderUrl("ats-message-renderer", s"/ats-message-renderer/message/$messageId")
      val renderUrl: RenderUrl =
        RenderUrl("secure-message", s"/secure-messaging/ats-message-renderer/message/$messageId")
      val letterWithAtsRenderUrl: Letter = letter.copy(_id = messageId, renderUrl = atsRenderUrl)
      val letterWithUpdatedRenderUrl: Letter = letter.copy(_id = messageId, renderUrl = renderUrl)
      repository.collection.deleteMany(Filters.empty()).toFuture().futureValue
      repository.collection.insertOne(letterWithAtsRenderUrl).toFuture().futureValue

      val result: Either[SecureMessageError, Letter] =
        await(repository.getLetter(messageId, identifiers))
      result mustBe Right(letterWithUpdatedRenderUrl)
    }

    "be returned with renderUrl updated for two-way-message type" in new TestContext() {
      val messageId = new ObjectId
      val twoWayMessageRenderUrl: RenderUrl = RenderUrl("two-way-message", s"/messages/$messageId/content")
      val renderUrl: RenderUrl =
        RenderUrl("secure-message", s"/secure-messaging/two-way-message/messages/$messageId/content")
      val letterWith2WMRenderUrl: Letter = letter.copy(_id = messageId, renderUrl = twoWayMessageRenderUrl)
      val letterWithUpdatedRenderUrl: Letter = letter.copy(_id = messageId, renderUrl = renderUrl)
      repository.collection.deleteMany(Filters.empty()).toFuture().futureValue
      repository.collection.insertOne(letterWith2WMRenderUrl).toFuture().futureValue

      val result: Either[SecureMessageError, Letter] =
        await(repository.getLetter(messageId, identifiers))
      result mustBe Right(letterWithUpdatedRenderUrl)
    }

    "be returned with renderUrl updated for sa-message-renderer type" in new TestContext() {
      val messageId = new ObjectId
      val saMessageRenderUrl: RenderUrl = RenderUrl("sa-message-renderer", s"/messages/sa/utr/$messageId")
      val renderUrl: RenderUrl =
        RenderUrl("secure-message", s"/secure-messaging/messages/sa/utr/$messageId")
      val letterWithSARenderUrl: Letter = letter.copy(_id = messageId, renderUrl = saMessageRenderUrl)
      val letterWithUpdatedRenderUrl: Letter = letter.copy(_id = messageId, renderUrl = renderUrl)
      repository.collection.deleteMany(Filters.empty()).toFuture().futureValue
      repository.collection.insertOne(letterWithUpdatedRenderUrl).toFuture().futureValue

      val result: Either[SecureMessageError, Letter] =
        await(repository.getLetter(messageId, identifiers))
      result mustBe Right(letterWithUpdatedRenderUrl)
    }
  }

  "Update letter with new read time" should {
    "update readTime only if its empty" in new TestContext(coreLetters = lettersWithoutReadTime) {
      await(repository.addReadTime(objectID))
      val result: Either[SecureMessageError, Letter] =
        await(
          repository
            .getLetter(objectID, Set(Identifier("EORINumber", "GB1234567890", Some("HMRC-CUS-ORG"))))
        )
      result.toOption.get.readTime must not be empty
    }
    "not update readTime if it already exists" in new TestContext() {
      await(repository.addReadTime(objectID))
      val result: Either[SecureMessageError, Letter] =
        await(
          repository
            .getLetter(objectID, Set(Identifier("EORINumber", "GB1234567890", Some("HMRC-CUS-ORG"))))
        )
      result.toOption.get.readTime mustBe letter.readTime
    }
  }

  "getLetters with future validFrom" should {
    "not return letters with a future validFrom Date" in new TestContext() {
      val result = (for {
        _ <- repository.collection.insertOne(letterWithFutureDate).toFuture()
        _ <- repository.collection.insertOne(letterWithTodaysDate).toFuture()
        r <- repository.getLetters(identifiers, None)
      } yield r).futureValue
      result.size mustBe 2
    }

    "return an empty list if no identifier value matches" in new TestContext() {
      val result = (for {
        _ <- repository.collection.insertOne(letterWithFutureDate).toFuture()
        r <- repository.getLetters(identifiers.map(i => i.copy(value = "non-existing")), None)
      } yield r).futureValue
      result mustBe empty
    }
  }

  "getLetters" should {
    "return the letters for matching identifier enrolment and value " in new TestContext() {
      val result: Future[List[Letter]] = repository.getLetters(identifiers, None)
      result.futureValue mustBe List(letter)
    }
    "return an empty list if no identifier enrolment passed" in new TestContext() {
      val result: Future[List[Letter]] = repository.getLetters(Set.empty, None)
      result.futureValue mustBe empty
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
      result.futureValue mustBe List(letter)
    }
    "return letters matching tags" in new TestContext() {
      val result: Future[List[Letter]] =
        repository.getLetters(identifiers, Some(List(FilterTag("notificationType", "Direct Debit"))))
      result.futureValue mustBe List(letter)
    }
    "not return an empty list for non matching tags" in new TestContext() {
      val result: Future[List[Letter]] =
        repository.getLetters(identifiers, Some(List(FilterTag("notificationType", "non-existing"))))
      result.futureValue mustBe empty
    }
  }

  "getLettersCount" should {
    "return Count when readTime is recorded for matching identifier enrolment and value" in new TestContext() {
      val result = repository.getLettersCount(identifiers, None)
      result.futureValue mustBe MessagesCount(1, 0)
    }
    "return Count when no readTime is recorded for matching identifier enrolment and value" in new TestContext(
      lettersWithoutReadTime
    ) {
      val result = repository.getLettersCount(identifiers, None)
      result.futureValue mustBe MessagesCount(1, 1)
    }
    "return Count if no identifier enrolment matches" in new TestContext() {
      val result =
        repository.getLettersCount(identifiers.map(i => i.copy(enrolment = Some("non-existing"))), None)
      result.futureValue mustBe MessagesCount(0, 0)
    }
    "return Count ignoring identifier name matches" in new TestContext() {
      val result =
        repository.getLettersCount(identifiers.map(i => i.copy(name = "non-existing")), None)
      result.futureValue mustBe MessagesCount(1, 0)
    }
    "return Count matching tags" in new TestContext() {
      val result =
        repository.getLettersCount(identifiers, Some(List(FilterTag("notificationType", "Direct Debit"))))
      result.futureValue mustBe MessagesCount(1, 0)
    }
    "return an empty Count for non matching tags" in new TestContext() {
      val result =
        repository.getLettersCount(identifiers, Some(List(FilterTag("notificationType", "non-existing")))).futureValue
      result mustBe MessagesCount(0, 0)
    }
    "return a count ignoring letters with a future validFrom" in new TestContext() {
      val result = (for {
        _ <- repository.collection.insertOne(letterWithFutureDate).toFuture()
        _ <- repository.collection.insertOne(letterWithTodaysDate).toFuture()
        r <- repository.getLettersCount(identifiers, None)
      } yield r).futureValue
      result mustBe MessagesCount(2, 0)
    }
  }

  class TestContext(coreLetters: JsValue = lettersWithTimeFields, val objectID: ObjectId = new ObjectId()) {
    val letter: Letter = coreLetters.add(Seq("_id" -> Json.toJson(objectID))).as[Letter]
    val letterWithTodaysDate: Letter = letter.copy(
      _id = new ObjectId(),
      hash = "LfK755SXhY2rlc9kL50ohJZ2dvRzZGjU74kjcdJMAcX=",
      externalRef = Some(ExternalReference("1234567891234567893", "mdtp")),
      validFrom = LocalDate.now()
    )
    val letterWithFutureDate: Letter = letter.copy(
      _id = new ObjectId(),
      hash = "LfK755SXhY2rlc9kL50ohJZ2dvRzZGjU74kjcdJMAcZ=",
      externalRef = Some(ExternalReference("1234567891234567894", "mdtp")),
      validFrom = LocalDate.now().plusDays(1)
    )
    repository.collection.deleteMany(Filters.empty()).toFuture().futureValue
    repository.collection.insertOne(letter).toFuture().futureValue
  }

}

trait StaticTestData {
  val lastUpdatedField: (String, JsValue) = "lastUpdated" -> Json.toJson(Instant.now())
  val readTimeField: (String, JsValue) = "readTime"       -> Json.toJson(Instant.now())
  val timeFields = Seq(lastUpdatedField, readTimeField)
  val lettersWithTimeFields = Resources.readJson("model/core/letter.json").add(timeFields)
  val lettersWithoutReadTime = Resources.readJson("model/core/letter.json").add(Seq(lastUpdatedField))
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
