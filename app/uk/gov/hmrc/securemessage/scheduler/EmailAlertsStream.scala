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
import uk.gov.hmrc.securemessage.scheduler.BaseScheduledStream.Result
import uk.gov.hmrc.securemessage.services.{ EmailAlertService, EmailResults }

import javax.inject.{ Inject, Singleton }
import scala.concurrent.duration.*
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class EmailAlertsStream @Inject() (
  override val configuration: Configuration,
  val lockRepository: LockRepository,
  emailAlerter: EmailAlertService,
  val lifecycle: ApplicationLifecycle
)(using val ec: ExecutionContext, val mat: Materializer)
    extends BaseScheduledStream with SchedulingConfig {

  override val name = "EmailAlertsStream"

  override val lockService: LockService =
    LockService(lockRepository, lockId = name, ttl = lockTtl(default = 1))

  override protected def startMessage = s"$name Start processing secure messages to send email requests"

  override protected def stopMessage = s"$name Stop processing secure messages to send email requests"

  override protected def processJob(): Future[Result] =
    withLock:
      emailAlerter
        .sendEmailAlerts()
        .map:
          case EmailResults(0, 0, _, _) => s"$name No messages to process"
          case EmailResults(sent, requeued, _, _) =>
            s"$name: Succeeded - $sent, Will be retried - $requeued"
        .recover:
          case e: Exception =>
            s"$name Error processing Alerts ${ExceptionUtils.getStackTrace(e)}"
}
