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

package uk.gov.hmrc.securemessage.scheduler

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.mockito.ArgumentMatchers.{ any, eq as eqTo }
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import play.api.inject.ApplicationLifecycle
import uk.gov.hmrc.mongo.lock.{ Lock, LockRepository }
import uk.gov.hmrc.securemessage.services.{ EmailResults, ExtraAlerter }

import java.time.Instant
import scala.concurrent.duration.*
import scala.concurrent.{ ExecutionContext, Future }

class ExtraAlertsStreamSpec
    extends AnyWordSpec with Matchers with MockitoSugar with ScalaFutures with BeforeAndAfterEach {

  implicit val system: ActorSystem = ActorSystem("ExtraAlertsStreamSpec")
  implicit val mat: Materializer = Materializer(system)
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  private val mockConfig = mock[Configuration]
  private val mockLockRepo = mock[LockRepository]
  private val mockExtraAlerter = mock[ExtraAlerter]
  private val mockLifecycle = mock[ApplicationLifecycle]

  private val testInitialDelay = 1.second
  private val testInterval = 1.minute
  private val testLockDuration = 1.hour

  override def beforeEach(): Unit = {
    when(mockConfig.getOptional[FiniteDuration](eqTo("scheduling.ExtraAlertsStream.initialDelay"))(any()))
      .thenReturn(Some(testInitialDelay))
    when(mockConfig.getOptional[FiniteDuration](eqTo("scheduling.ExtraAlertsStream.interval"))(any()))
      .thenReturn(Some(testInterval))
    when(mockConfig.getOptional[FiniteDuration](eqTo("scheduling.ExtraAlertsStream.lockDuration"))(any()))
      .thenReturn(Some(testLockDuration))
  }

  private def newStream = new ExtraAlertsStream(mockConfig, mockLockRepo, mockExtraAlerter, mockLifecycle)

  private def createLock: Lock = {
    val now = Instant.now()
    Lock("extraAlertsStreamLock", "ExtraAlertsStream", now, now.plusSeconds(testLockDuration.toSeconds))
  }

  "ExtraAlertsStream" should {

    "return success result when lock is acquired and extra alerts are sent" in {
      val expectedResult =
        "ExtraAlertsStream: Succeeded - 3, Will be retried - 1, Permanently failed - 0, Hard copy requested = 0"
      val testLock = createLock

      when(mockLockRepo.takeLock(any(), any(), any())).thenReturn(Future.successful(Some(testLock)))
      when(mockLockRepo.releaseLock(any(), any())).thenReturn(Future.unit)
      when(mockExtraAlerter.sendAlerts()).thenReturn(Future.successful(EmailResults(3, 1)))

      newStream.processJob().futureValue.message shouldBe expectedResult
    }

    "return lock acquisition failure when lock cannot be acquired" in {
      when(mockLockRepo.takeLock(any(), any(), any())).thenReturn(Future.successful(None))

      newStream.processJob().futureValue.message shouldBe
        "ExtraAlertsStream cannot acquire mongo lock, not running"
    }

    "return error message when alert sending fails" in {
      val testLock = createLock
      val error = new RuntimeException("Failed to send alerts")

      when(mockLockRepo.takeLock(any(), any(), any())).thenReturn(Future.successful(Some(testLock)))
      when(mockLockRepo.releaseLock(any(), any())).thenReturn(Future.unit)
      when(mockExtraAlerter.sendAlerts()).thenReturn(Future.failed(error))

      val result = newStream.processJob().futureValue
      result.message should include("Failed to send alerts")
    }
  }
}
