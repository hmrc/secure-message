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

import org.apache.pekko.stream.{ KillSwitches, Materializer, UniqueKillSwitch }
import org.apache.pekko.stream.scaladsl.{ Keep, Sink, Source }
import play.api.inject.ApplicationLifecycle
import play.api.{ Configuration, Logging }
import uk.gov.hmrc.common.message.model.TimeSource
import uk.gov.hmrc.mongo.lock.{ LockRepository, LockService }
import uk.gov.hmrc.securemessage.repository.StatsMetricRepository

import java.time.{ ZoneId, ZoneOffset }
import javax.inject.{ Inject, Singleton }
import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class StatsMetricResetStream @Inject() (
  val configuration: Configuration,
  statsMetricRepository: StatsMetricRepository,
  lockRepository: LockRepository,
  timeSource: TimeSource,
  lifecycle: ApplicationLifecycle
)(implicit ec: ExecutionContext, mat: Materializer)
    extends Logging {

  val name = "StatsMetricResetStream"

  private val maxLockHours: Long = 10
  private val releaseLockAfter: FiniteDuration = maxLockHours.hours

  val lockService: LockService = LockService(lockRepository = lockRepository, lockId = name, ttl = releaseLockAfter)

  private val now = timeSource.now()

  // Calculate initial delay to midnight UTC
  private val initialDelay: FiniteDuration = FiniteDuration(
    now
      .atZone(ZoneId.of("UTC"))
      .toLocalDate
      .atStartOfDay()
      .plusDays(1)
      .toInstant(ZoneOffset.UTC)
      .toEpochMilli - now.toEpochMilli,
    MILLISECONDS
  )

  private val interval: FiniteDuration = 1.day

  private val (killSwitch: UniqueKillSwitch, _) = Source
    .tick(initialDelay, interval, ())
    .viaMat(KillSwitches.single)(Keep.right)
    .mapAsync(1)(_ => runJob())
    .toMat(Sink.ignore)(Keep.both)
    .run()

  // To stop the stream gracefully when system is shut down
  lifecycle.addStopHook { () =>
    logger.info(s"Shutting down $name stream using KillSwitch")
    killSwitch.shutdown()
    Future.unit
  }

  def runJob(): Future[Unit] = {
    logger.warn(s"$name Start resetting statistics metrics")

    resetMetrics().map { result =>
      logger.warn(result.message)
      logger.warn(s"$name Stop resetting statistics metrics")
    }
  }

  def resetMetrics(): Future[Result] =
    lockService
      .withLock[String] {
        statsMetricRepository
          .reset()
          .map {
            case Some(result) if result.wasAcknowledged() =>
              "Successfully reset metric counts"
            case _ =>
              "Could not reset metric counts"
          }
          .recover { case e: Exception =>
            val msg = s"Error resetting metric counts: ${e.getMessage}"
            logger.error(msg)
            msg
          }
      }
      .map {
        case Some(msg) => Result(msg)
        case None      => Result(s"$name cannot acquire mongo lock, not running")
      }
}
