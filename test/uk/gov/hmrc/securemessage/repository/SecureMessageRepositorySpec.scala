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
import org.mongodb.scala.model.Filters
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.test.Helpers.{ await, defaultAwaitTimeout }
import org.mongodb.scala.SingleObservableFuture
import org.mongodb.scala.ObservableFuture
import uk.gov.hmrc.common.message.model.{ MessagesCount, TimeSource }
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.mongo.workitem.ProcessingStatus.{ Deferred, Succeeded, ToDo }
import uk.gov.hmrc.securemessage.helpers.Resources
import uk.gov.hmrc.securemessage.models.core.{ Identifier, MessageFilter }
import uk.gov.hmrc.securemessage.models.v4.{ BrakeBatch, BrakeBatchApproval, BrakeBatchDetails, BrakeBatchMessage, SecureMessage }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await
import scala.concurrent.duration.*

class SecureMessageRepositorySpec
    extends PlaySpec with MockitoSugar with DefaultPlayMongoRepositorySupport[SecureMessage] with BeforeAndAfterEach
    with ScalaFutures {

  override def checkTtlIndex: Boolean = false

  Await.result(mongoComponent.database.drop().toFuture(), 5.seconds)
  override val repository: SecureMessageRepository =
    new SecureMessageRepository(mongoComponent, mock[TimeSource], 30, 30, 30)

  override def afterEach(): Unit =
    await(repository.collection.deleteMany(Filters.empty()).toFuture().map(_ => ()))

  val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
  val niMessage: SecureMessage = Resources.readJson("model/core/v4/valid_NI_message.json").as[SecureMessage]

  implicit val messagedFilter: MessageFilter = MessageFilter()

  "Message V4" should {
    "be saved if unique" in {
      val result: Boolean = await(repository.save(message))
      result mustBe true
    }

    "not be saved if duplicate" in {
      await(repository.save(message))
      val result: Boolean = await(repository.save(message))
      result mustBe false
    }

    "return the total & unread messages count for given tax identifiers" in {
      await(repository.save(message.copy(verificationBrake = Some(false))))
      val result: MessagesCount = await(repository.countBy(Set(message.recipient.identifier)))
      result mustBe MessagesCount(1, 1)

      await(repository.save(niMessage.copy(readTime = Some(Instant.now()), verificationBrake = Some(false))))
      val result1: MessagesCount =
        await(repository.countBy(Set(message.recipient.identifier, niMessage.recipient.identifier)))
      result1 mustBe MessagesCount(2, 1)
    }

    "return the total & unread messages count for given tax identifiers - CDS messages" in {
      val taxIdWithName = message.recipient.identifier
      val niTaxIdWithName = niMessage.recipient.identifier
      val identifier = Identifier("", taxIdWithName.value, Some(taxIdWithName.name))
      val niIdentifier = Identifier("", niTaxIdWithName.value, Some(niTaxIdWithName.name))

      await(repository.save(message.copy(verificationBrake = Some(false))))
      val result: MessagesCount = await(repository.getSecureMessageCount(Set(identifier), None))
      result mustBe MessagesCount(1, 1)

      await(repository.save(niMessage.copy(readTime = Some(Instant.now()), verificationBrake = Some(false))))
      val result1: MessagesCount = await(repository.getSecureMessageCount(Set(identifier, niIdentifier), None))
      result1 mustBe MessagesCount(2, 1)
    }

    "return the message - findBy (NonCDS messages)" in {
      await(repository.save(message))
      await(repository.save(niMessage))
      val result = await(repository.findBy(Set(message.recipient.identifier)))
      result mustBe List(message)
      val result1: List[SecureMessage] =
        await(repository.findBy(Set(message.recipient.identifier, niMessage.recipient.identifier)))
      result1 mustBe List(niMessage, message)
    }

    "return the messages for given tax identifiers - CDS messages" in {
      val taxIdWithName = message.recipient.identifier
      val niTaxIdWithName = niMessage.recipient.identifier
      val identifier = Identifier("", taxIdWithName.value, Some(taxIdWithName.name))
      val niIdentifier = Identifier("", niTaxIdWithName.value, Some(niTaxIdWithName.name))

      await(repository.save(message))
      val result: List[SecureMessage] = await(repository.getSecureMessages(Set(identifier), None))
      result mustBe List(message)

      await(repository.save(niMessage))
      val result1: List[SecureMessage] = await(repository.getSecureMessages(Set(identifier, niIdentifier), None))
      result1 mustBe List(niMessage, message)
    }

    "add the read time for given message id" in {
      val taxIdWithName = message.recipient.identifier
      val niTaxIdWithName = niMessage.recipient.identifier
      val identifier = Identifier("", taxIdWithName.value, Some(taxIdWithName.name))
      val niIdentifier = Identifier("", niTaxIdWithName.value, Some(niTaxIdWithName.name))
      val readTime = Instant.now()
      await(repository.save(message))
      await(repository.addReadTime(message._id, readTime))

      val result1: Boolean =
        await(repository.getSecureMessage(message._id, Set(identifier, niIdentifier))).forall(_.readTime.isDefined)
      result1 mustBe true
    }
  }

  "pullBrakeBatches" must {

    "return an empty batch list if there are no Deferred messages with verificationBrake set to true" in {
      val details = message.details.map(_.copy(issueDate = Some(LocalDate.now())))
      val brakeMessage =
        message.copy(status = Succeeded, verificationBrake = Some(true), details = details)
      repository.save(brakeMessage).futureValue
      val batch = repository.pullBrakeBatchDetails().futureValue
      batch must be(List.empty)
    }

    "return valid batches" in {
      val details = message.details.map(_.copy(batchId = Some("foobar")))
      val message1 = message.copy(status = Deferred, verificationBrake = Some(true))
      val message2 = message.copy(status = Deferred, verificationBrake = Some(true))

      val message3 = message.copy(status = Deferred, verificationBrake = Some(true), details = details)
      val message4 = message.copy(status = Succeeded, verificationBrake = Some(true))

      repository.save(message1).futureValue
      repository.save(message2).futureValue
      repository.save(message3).futureValue
      repository.save(message4).futureValue

      val batches = repository.pullBrakeBatchDetails().futureValue

      batches must contain only (BrakeBatchDetails(
        "1234567",
        "SA300",
        LocalDate.parse("2017-02-13"),
        "newMessageAlert_SA300",
        1
      ))
    }

    "return batches if its just 1 message without any grouping" in {

      val message1 = message.copy(status = Deferred, verificationBrake = Some(true))

      repository.save(message1).futureValue

      val batches = repository.pullBrakeBatchDetails().futureValue

      batches must contain only (BrakeBatchDetails(
        "1234567",
        "SA300",
        LocalDate.parse("2017-02-13"),
        "newMessageAlert_SA300",
        1
      ))
    }

    "return batch with group by same message with status deferred" in {

      val message1 = message.copy(status = Deferred, verificationBrake = Some(true))
      val message2 = message.copy(status = Deferred, verificationBrake = Some(true))
      val message3 = message.copy(status = Succeeded, verificationBrake = Some(true))

      repository.save(message1).futureValue
      repository.save(message2).futureValue
      repository.save(message3).futureValue

      val batches = repository.pullBrakeBatchDetails().futureValue

      batches must contain only (BrakeBatchDetails(
        "1234567",
        "SA300",
        LocalDate.parse("2017-02-13"),
        "newMessageAlert_SA300",
        1
      ))
    }
  }

  "brakeBatchAccepted" must {

    "not update \"non-batch\" messages" in {
      val testMessage: SecureMessage = message.copy(status = Succeeded, verificationBrake = Some(true))
      val issueDate = message.details.flatMap(_.issueDate).getOrElse(LocalDate.now())

      repository.save(testMessage).futureValue

      val brakeBatch: BrakeBatchApproval =
        BrakeBatchApproval("1234567", "SA300", issueDate, "newMessageAlert_SA300", "")

      repository.brakeBatchAccepted(brakeBatch).futureValue must be(false)

      repository.collection.find().toFuture().futureValue mustBe List(testMessage)
    }

    "update 1 message if in batch" in {
      val testMessage: SecureMessage = message.copy(status = Deferred, verificationBrake = Some(true))
      val updated_message = testMessage.copy(status = ToDo, verificationBrake = Some(false))
      val issueDate = message.details.flatMap(_.issueDate).getOrElse(LocalDate.now())
      repository.save(testMessage).futureValue

      val brakeBatch: BrakeBatchApproval =
        BrakeBatchApproval("1234567", "SA300", issueDate, "newMessageAlert_SA300", "")
      repository.brakeBatchAccepted(brakeBatch).futureValue must be(true)

      repository.findById(message._id).futureValue mustBe Some(updated_message)
    }

    "update 2 messages if in batch" in {
      val message1 = message.copy(status = Deferred, verificationBrake = Some(true))
      val message2 = message.copy(status = Deferred, verificationBrake = Some(true))
      val updated_message1 = message1.copy(status = ToDo, verificationBrake = Some(false))
      val updated_message2 = message2.copy(status = ToDo, verificationBrake = Some(false))
      val issueDate = message.details.flatMap(_.issueDate).getOrElse(LocalDate.now())
      repository.save(message1).futureValue
      repository.save(message2).futureValue

      val brakeBatch: BrakeBatchApproval =
        BrakeBatchApproval("1234567", "SA300", issueDate, "newMessageAlert_SA300", "")
      repository.brakeBatchAccepted(brakeBatch).futureValue must be(true)

      repository.findById(message1._id).futureValue mustBe Some(updated_message1)
      repository.findById(message2._id).futureValue mustBe Some(updated_message2)
    }
  }

  "brakeBatchRejected" must {

    "not update \"non-batch\" messages" in {
      val issueDate = message.details.flatMap(_.issueDate).getOrElse(LocalDate.now())
      val message1 = message.copy(status = Succeeded, verificationBrake = Some(true))
      repository.save(message1).futureValue

      val brakeBatch = BrakeBatchApproval("1234567", "SA300", issueDate, "newMessageAlert_SA300", "")
      repository.brakeBatchRejected(brakeBatch).futureValue must be(false)

      repository.collection.find(Filters.equal("_id", message._id)).toFuture().futureValue mustBe List(
        message1
      )

    }
  }

  "brakeBatchMessageRandom" must {

    "return None list if there are no Deferred messages with verificationBrake set to true" in {
      val message1 = message.copy(status = Succeeded, verificationBrake = Some(true))
      val issueDate = message.details.flatMap(_.issueDate).getOrElse(LocalDate.now())
      val brakeBatch = BrakeBatch("1234567", "SA300", issueDate, "newMessageAlert_SA300")
      repository.save(message1).futureValue

      repository.brakeBatchMessageRandom(brakeBatch).futureValue must be(None)
    }

    "return a random message if it exists in a requested batch" in {

      val message1 = message.copy(status = Deferred, verificationBrake = Some(true))
      val message2 = message.copy(status = Deferred, verificationBrake = Some(true))

      repository.save(message1).futureValue
      repository.save(message2).futureValue

      val issueDate = message.details.flatMap(_.issueDate).getOrElse(LocalDate.now())

      val brakeBatch = BrakeBatch("1234567", "SA300", issueDate, "newMessageAlert_SA300")
      val result = repository.brakeBatchMessageRandom(brakeBatch).futureValue
      List(Some(BrakeBatchMessage(message1)), Some(BrakeBatchMessage(message2))) must contain(result)
    }
  }
}
