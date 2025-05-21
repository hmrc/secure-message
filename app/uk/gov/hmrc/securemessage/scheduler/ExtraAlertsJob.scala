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

import org.apache.pekko.actor.ActorSystem
import play.api.{ Configuration, Logging }
import uk.gov.hmrc.mongo.lock.LockRepository
import uk.gov.hmrc.securemessage.services.{ EmailResults, ExtraAlerter }

import java.time.Duration
import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class ExtraAlertsJob @Inject() (
  val configuration: Configuration,
  extraAlerter: ExtraAlerter,
  repo: LockRepository
)(implicit actor: ActorSystem)
    extends ScheduledJob with SchedulingConfig with Logging {
  val name = "ExtraAlertsJob"

  val lockRepository = repo

  val releaseLockTime = 600
  lazy val releaseLockAfter = Duration.ofMinutes(releaseLockTime)

  def execute(implicit ec: ExecutionContext): Future[Result] = {
    logger.warn("ExtraAlertsJob starting executeInLock")
    extraAlerter.sendAlerts().map {
      case EmailResults(0, 0, 0, 0) =>
        val msg = "ExtraAlertsJob: No messages to process"
        logger.warn(msg)
        Result(msg)
      case EmailResults(sent, requeued, failed, hardCopyRequested) =>
        val msg =
          s"ExtraAlertsJob: Succeeded - $sent, Will be retried - $requeued, Permanently failed - $failed, Hard copy requested = $hardCopyRequested"
        logger.warn(msg)
        Result(msg)
    }
  }

}
