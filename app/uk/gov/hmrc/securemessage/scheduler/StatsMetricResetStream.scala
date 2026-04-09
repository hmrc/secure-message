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
import uk.gov.hmrc.securemessage.scheduler.BaseScheduledStream.Result

import java.time.{ ZoneId, ZoneOffset }
import javax.inject.{ Inject, Singleton }
import scala.concurrent.duration.*
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class StatsMetricResetStream @Inject() (
  val configuration: Configuration,
  val lockRepository: LockRepository,
  statsMetricRepository: StatsMetricRepository,
  val lifecycle: ApplicationLifecycle
)(using val ec: ExecutionContext, val mat: Materializer)
    extends BaseScheduledStream {

  lazy val name = "StatsMetricResetStream"

  lazy val lockService: LockService =
    LockService(lockRepository, lockId = name, ttl = 10.hours)

  override protected def startMessage = s"$name Start resetting statistics metrics"

  override protected def stopMessage = s"$name Stop resetting statistics metrics"

  override def processJob(): Future[Result] =
    withLock:
      statsMetricRepository
        .reset()
        .map:
          case Some(r) if r.wasAcknowledged() => "Successfully reset metric counts"
          case _                              => "Could not reset metric counts"
        .recover:
          case e: Exception =>
            val msg = s"Error resetting metric counts: ${e.getMessage}"
            logger.error(msg)
            msg
}
