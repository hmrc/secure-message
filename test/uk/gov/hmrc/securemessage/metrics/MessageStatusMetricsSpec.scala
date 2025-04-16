/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.securemessage.metrics

import java.time.Instant
import org.mongodb.scala.model.Filters
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{ IntegrationPatience, ScalaFutures }
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import uk.gov.hmrc.common.message.model.{ ExternalRef, SystemTimeSource, TimeSource }
import uk.gov.hmrc.http.HeaderCarrier
import org.mongodb.scala.SingleObservableFuture
import uk.gov.hmrc.message.metrics.MessageStatusMetrics
import uk.gov.hmrc.mongo.metrix.MongoMetricRepository
import uk.gov.hmrc.mongo.workitem.ProcessingStatus
import uk.gov.hmrc.common.message.model.EmailAlert
import uk.gov.hmrc.securemessage.repository.SecureMessageRepository
import uk.gov.hmrc.securemessage.services.utils.SecureMessageFixtures

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Success

class MessageStatusMetricsSpec
    extends PlaySpec with BeforeAndAfterEach with ScalaFutures with GuiceOneAppPerSuite with IntegrationPatience {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  lazy implicit val timeSource: TimeSource = new TimeSource {
    private val currentDate: Instant = SystemTimeSource.now()
    override def now(): Instant = currentDate
  }

  val messageRepository: SecureMessageRepository = app.injector.instanceOf[SecureMessageRepository]
  val metricsRepo: MongoMetricRepository = app.injector.instanceOf[MongoMetricRepository]

  override def beforeEach(): Unit = {
    // Await cleanup
    metricsRepo.collection.deleteMany(Filters.empty()).toFuture().futureValue
    messageRepository.collection.deleteMany(Filters.empty()).toFuture().futureValue
    super.beforeEach()
  }

  "MessageStatusMetrics" must {
    "provide a count of the total number of messages with the provided ProcessingStatus" in new TestCase {
      insertMessageForEach(ProcessingStatus.values)

      val messageStatusMetrics = MessageStatusMetrics(messageRepository)
      val values = messageStatusMetrics.metrics.futureValue

      values.size mustBe ProcessingStatus.values.size

      val expectedKeys = ProcessingStatus.values.map(status => s"message.${status.name}")
      values.keySet must contain theSameElementsAs expectedKeys
      values.values.foreach(_ mustBe 1)
    }
  }

  trait TestCase {
    def insertMessageForEach(processingStatuses: Set[ProcessingStatus]): Unit =
      processingStatuses.foreach { processingStatus =>
        val m = SecureMessageFixtures
          .messageForSA(
            externalRef = ExternalRef(s"${UUID.randomUUID()}ExtRef", "gmc"),
            utr = s"${UUID.randomUUID()}123456",
            hash = ""
          )
        messageRepository
          .save(m)
          .andThen { case Success(_) =>
            messageRepository.updateStatus(
              id = m._id,
              status = processingStatus
            )
          }
          .futureValue
      }
  }
}
