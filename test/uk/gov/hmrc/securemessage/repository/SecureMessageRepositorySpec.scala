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

import org.joda.time.DateTime
import org.mongodb.scala.model.Filters
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.test.Helpers.{ await, defaultAwaitTimeout }
import uk.gov.hmrc.common.message.model.TimeSource
import uk.gov.hmrc.common.message.model.MessagesCount
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.securemessage.helpers.Resources
import uk.gov.hmrc.securemessage.models.core.MessageFilter
import uk.gov.hmrc.securemessage.models.v4.SecureMessage

import scala.concurrent.ExecutionContext.Implicits.global

class SecureMessageRepositorySpec
    extends PlaySpec with MockitoSugar with DefaultPlayMongoRepositorySupport[SecureMessage] with BeforeAndAfterEach
    with ScalaFutures {

  override lazy val repository = new SecureMessageRepository(mongoComponent, mock[TimeSource], 30, 30, 30)

  override def afterEach(): Unit =
    await(repository.collection.deleteMany(Filters.empty()).toFuture().map(_ => ()))

  val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
  val niMessage: SecureMessage = Resources.readJson("model/core/v4/valid_NI_message.json").as[SecureMessage]

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
      val result: MessagesCount = await(repository.countBy(Set(message.recipient.taxIdentifier))(MessageFilter()))
      result mustBe MessagesCount(1, 1)

      await(repository.save(niMessage.copy(readTime = Some(DateTime.now()), verificationBrake = Some(false))))
      val result1: MessagesCount = await(
        repository.countBy(Set(message.recipient.taxIdentifier, niMessage.recipient.taxIdentifier))(MessageFilter()))
      result1 mustBe MessagesCount(2, 1)
    }
  }
}
