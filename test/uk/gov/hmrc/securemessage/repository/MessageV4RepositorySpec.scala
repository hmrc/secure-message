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

import org.mongodb.scala.model.Filters
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.PlaySpec
import play.api.test.Helpers.{ await, defaultAwaitTimeout }
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.securemessage.helpers.Resources
import uk.gov.hmrc.securemessage.models.core.MessageV4

import scala.concurrent.ExecutionContext.Implicits.global

class MessageV4RepositorySpec
    extends PlaySpec with DefaultPlayMongoRepositorySupport[MessageV4] with BeforeAndAfterEach with ScalaFutures {

  override lazy val repository = new MessageV4Repository(mongoComponent)

  override def afterEach(): Unit =
    await(repository.collection.deleteMany(Filters.empty()).toFuture().map(_ => ()))

  val message: MessageV4 = Resources.readJson("model/core/v4/valid_message.json").as[MessageV4]

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
  }
}
