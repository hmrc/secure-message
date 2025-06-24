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
import play.api.{ Configuration, Logging }
import play.api.inject.ApplicationLifecycle
import play.libs.exception.ExceptionUtils
import uk.gov.hmrc.mongo.lock.{ LockRepository, LockService }
import uk.gov.hmrc.securemessage.services.{ EmailAlertService, EmailResults }

import javax.inject.{ Inject, Singleton }
import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class EmailAlertsStream @Inject() (
  override val configuration: Configuration,
  lockRepository: LockRepository,
  emailAlerter: EmailAlertService,
  lifecycle: ApplicationLifecycle
)(implicit ec: ExecutionContext, mat: Materializer)
    extends SchedulingConfig with Logging {

  override val name = "EmailAlertsStream"

  private val maxLockHours: Long = 1
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
    logger.warn(s"$name Start processing secure messages to send email requests")

    processSecureMessages().map { result =>
      logger.warn(result.message)
      logger.warn(s"$name Stop processing secure messages to send email requests")
    }
  }

  def processSecureMessages(): Future[Result] =
    lockService
      .withLock[String] {
        emailAlerter
          .sendEmailAlerts()
          .map {
            case EmailResults(0, 0, _, _) =>
              s"$name No messages to process"
            case EmailResults(sent, requeued, _, _) =>
              s"$name: Succeeded - $sent, Will be retried - $requeued"
          }
          .recover { case e: Exception =>
            s"$name Error processing Alerts ${ExceptionUtils.getStackTrace(e)}"
          }
      }
      .map {
        case Some(msg) => Result(msg)
        case None      => Result(s"$name cannot acquire mongo lock, not running")
      }
}
