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
import play.api.{ Configuration, Logging }
import uk.gov.hmrc.mongo.lock.{ LockRepository, LockService }
import uk.gov.hmrc.securemessage.services.{ EmailAlerter, EmailResults }

import javax.inject.Inject
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.{ Duration, FiniteDuration, HOURS, MILLISECONDS }

class SendEmailJob @Inject()(
  val configuration: Configuration,
  lockRepository: LockRepository,
  emailAlerter: EmailAlerter)
    extends Actor with Timers with SchedulingConfig with Logging {

  override val name: String = "SendEmailJob"

  lazy val maxLockHours: Long = 1
  lazy val releaseLockAfter: Duration = lockDuration match {
    case duration: FiniteDuration => Duration(duration.toMillis, MILLISECONDS)
    case _                        => Duration(maxLockHours, HOURS)
  }

  val ls = LockService(lockRepository = lockRepository, lockId = name, ttl = releaseLockAfter)

  override def preStart(): Unit = {
    logger.warn(s"Job $name starting")
    timers.startTimerWithFixedDelay(StartProcess, StartProcess, initialDelay, interval)
    super.preStart()
  }

  override def receive: Receive = {
    case StartProcess =>
      logger.warn(s"Start processing secure messages to send email requests")
      processSecureMessages
      logger.warn(s"Stop processing secure messages to send email requests")
  }

  def processSecureMessages(): Future[Result] =
    ls.withLock {
      emailAlerter.sendEmailAlerts().map {
        case EmailResults(0, 0) =>
          val msg = "SendEmailJob: No messages to process"
          logger.warn(msg)
          Result(msg)
        case EmailResults(sent, requeued) =>
          val msg =
            s"SendEmailJob: Succeeded - $sent, Will be retried - $requeued"
          Result(msg)
      }
    } map {
      case Some(Result(msg)) => Result(s"$msg")
      case None              => Result(s"$name cannot acquire mongo lock, not running")
    }
}

case object StartProcess
case class Result(message: String)
