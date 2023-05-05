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

package uk.gov.hmrc.securemessage.scheduler

import akka.actor.{ Actor, Timers }
import akka.stream.Materializer
import play.api.{ Configuration, Logging }
import play.libs.exception.ExceptionUtils
import uk.gov.hmrc.mongo.lock.{ LockRepository, LockService }
import uk.gov.hmrc.securemessage.services.{ EmailAlertService, EmailResults }

import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.{ Duration, FiniteDuration, HOURS, MILLISECONDS }

@Singleton
class EmailAlertJob @Inject()(
  val configuration: Configuration,
  lockRepository: LockRepository,
  emailAlerter: EmailAlertService)(implicit ec: ExecutionContext, mat: Materializer)
    extends Actor with Timers with SchedulingConfig with Logging {

  override val name: String = "EmailAlertJob"

  lazy val maxLockHours: Long = 1
  lazy val releaseLockAfter: Duration = lockDuration match {
    case duration: FiniteDuration => Duration(duration.toMillis, MILLISECONDS)
    case _                        => Duration(maxLockHours, HOURS)
  }

  lazy val ls = LockService(lockRepository = lockRepository, lockId = name, ttl = releaseLockAfter)

  override def preStart(): Unit = {
    logger.warn(s"Job $name starting")
    timers.startTimerWithFixedDelay(StartProcess, StartProcess, initialDelay, interval)
    super.preStart()
  }

  override def receive: Receive = {
    case StartProcess =>
      logger.warn(s"$name Start processing secure messages to send email requests")
      processSecureMessages()
      logger.warn(s"$name Stop processing secure messages to send email requests")
  }

  def processSecureMessages(): Future[Result] =
    ls.withLock[String] {
      emailAlerter
        .sendEmailAlerts()
        .map {
          case EmailResults(0, 0) => s"$name No messages to process"
          case EmailResults(sent, requeued) =>
            s"$name: Succeeded - $sent, Will be retried - $requeued"
        }
        .recover {
          case e: Exception => s"$name Error processing Alerts ${ExceptionUtils.getStackTrace(e)}"
        }
    } map { msg =>
      val msgStr = msg.getOrElse(s"$name cannot acquire mongo lock, not running")
      logger.warn(msgStr)
      Result(msgStr)
    }
}

case object StartProcess
case class Result(message: String)
