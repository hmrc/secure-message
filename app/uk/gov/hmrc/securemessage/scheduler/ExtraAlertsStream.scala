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
import org.apache.pekko.stream.{ KillSwitches, Materializer, UniqueKillSwitch }
import org.apache.pekko.stream.scaladsl.{ Keep, Sink, Source }
import play.api.inject.ApplicationLifecycle
import play.api.{ Configuration, Logging }
import uk.gov.hmrc.mongo.lock.{ LockRepository, LockService }
import uk.gov.hmrc.securemessage.services.{ EmailResults, ExtraAlerter }

import javax.inject.{ Inject, Singleton }
import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class ExtraAlertsStream @Inject() (
  override val configuration: Configuration,
  lockRepository: LockRepository,
  extraAlerter: ExtraAlerter,
  lifecycle: ApplicationLifecycle
)(implicit ec: ExecutionContext, mat: Materializer, actorSystem: ActorSystem)
    extends SchedulingConfig with Logging {

  override val name = "ExtraAlertsStream"

  private val maxLockHours: Long = 10
  private val releaseLockAfter: FiniteDuration = Option(lockDuration).getOrElse(maxLockHours.hours)

  val lockService: LockService = LockService(lockRepository = lockRepository, lockId = name, ttl = releaseLockAfter)

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
    logger.warn(s"$name Start processing extra alerts to send email requests")

    processExtraAlerts().map { result =>
      logger.warn(result.message)
      logger.warn(s"$name Stop processing extra alerts to send email requests")
    }
  }

  def processExtraAlerts(): Future[Result] =
    lockService
      .withLock[String] {
        extraAlerter
          .sendAlerts()
          .map {
            case EmailResults(0, 0, 0, 0) =>
              s"$name No messages to process"
            case EmailResults(sent, requeued, failed, hardCopyRequested) =>
              s"$name: Succeeded - $sent, Will be retried - $requeued, Permanently failed - $failed, Hard copy requested = $hardCopyRequested"
          }
          .recover { case e: Exception =>
            s"$name Error processing Extra Alerts: ${e.getMessage}"
          }
      }
      .map {
        case Some(msg) => Result(msg)
        case None      => Result(s"$name cannot acquire mongo lock, not running")
      }
}

case class Result(message: String)
