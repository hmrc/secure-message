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
import uk.gov.hmrc.securemessage.services.{ EmailAlertService, EmailResults }

import java.time.Instant
import scala.concurrent.duration.*
import scala.concurrent.{ ExecutionContext, Future }

class EmailAlertsStreamSpec
    extends AnyWordSpec with Matchers with MockitoSugar with ScalaFutures with BeforeAndAfterEach {

  implicit val system: ActorSystem = ActorSystem("EmailAlertsStreamSpec")
  implicit val mat: Materializer = Materializer(system)
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  private val mockConfig = mock[Configuration]
  private val mockLockRepo = mock[LockRepository]
  private val mockEmailAlerter = mock[EmailAlertService]
  private val mockLifecycle = mock[ApplicationLifecycle]

  private val testInitialDelay = 1.second
  private val testInterval = 1.minute
  private val testLockDuration = 1.hour

  override def beforeEach(): Unit = {
    super.beforeEach()
    when(mockConfig.getOptional[FiniteDuration](eqTo("scheduling.EmailAlertsStream.initialDelay"))(any()))
      .thenReturn(Some(testInitialDelay))
    when(mockConfig.getOptional[FiniteDuration](eqTo("scheduling.EmailAlertsStream.interval"))(any()))
      .thenReturn(Some(testInterval))
    when(mockConfig.getOptional[FiniteDuration](eqTo("scheduling.EmailAlertsStream.lockDuration"))(any()))
      .thenReturn(Some(testLockDuration))
  }

  private def newStream = new EmailAlertsStream(mockConfig, mockLockRepo, mockEmailAlerter, mockLifecycle)

  private def createLock: Lock = {
    val now = Instant.now()
    Lock("emailAlertsStreamLock", "EmailAlertsStream", now, now.plusSeconds(testLockDuration.toSeconds))
  }

  "EmailAlertsStream" should {

    "return success result when lock is acquired and email alerts are sent" in {
      val expectedResult = "EmailAlertsStream: Succeeded - 3, Will be retried - 1"
      val testLock = createLock

      when(mockLockRepo.takeLock(any(), any(), any())).thenReturn(Future.successful(Some(testLock)))
      when(mockLockRepo.releaseLock(any(), any())).thenReturn(Future.unit)
      when(mockEmailAlerter.sendEmailAlerts()).thenReturn(Future.successful(EmailResults(3, 1)))

      newStream.processSecureMessages().futureValue.message shouldBe expectedResult
    }

    "return lock acquisition failure when lock cannot be acquired" in {
      when(mockLockRepo.takeLock(any(), any(), any())).thenReturn(Future.successful(None))

      newStream.processSecureMessages().futureValue.message shouldBe
        "EmailAlertsStream cannot acquire mongo lock, not running"
    }

    "return error message when email alert sending fails" in {
      val testLock = createLock
      val error = new RuntimeException("Failed to send emails")

      when(mockLockRepo.takeLock(any(), any(), any())).thenReturn(Future.successful(Some(testLock)))
      when(mockLockRepo.releaseLock(any(), any())).thenReturn(Future.unit)
      when(mockEmailAlerter.sendEmailAlerts()).thenReturn(Future.failed(error))

      val result = newStream.processSecureMessages().futureValue
      result.message should include("Error processing Alerts")
    }
  }
}
