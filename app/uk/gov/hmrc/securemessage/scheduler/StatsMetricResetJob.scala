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

import play.api.{ Configuration, Logging }
import uk.gov.hmrc.common.message.model.TimeSource
import uk.gov.hmrc.mongo.lock.LockRepository
import uk.gov.hmrc.securemessage.repository.StatsMetricRepository

import java.time.{ Duration, ZoneId, ZoneOffset }
import javax.inject.{ Inject, Singleton }
import scala.concurrent.Future
import scala.concurrent.duration.{ FiniteDuration, * }

@Singleton
class StatsMetricResetJob @Inject() (
  val configuration: Configuration,
  statsMetricRepository: StatsMetricRepository,
  lockRepo: LockRepository,
  timeSource: TimeSource
) extends ScheduledJob with SchedulingConfig with Logging {

  val name = "formMetricReset"

  val lockRepository = lockRepo

  val now = timeSource.now()

  override lazy val initialDelay: FiniteDuration = FiniteDuration(
    now.atZone(ZoneId.of("UTC")).toLocalDate.atStartOfDay().plusDays(1).toInstant(ZoneOffset.UTC).toEpochMilli -
      now.toEpochMilli,
    MILLISECONDS
  )

  override lazy val interval: FiniteDuration = FiniteDuration(1, DAYS)

  val realseLockTime = 600
  lazy val releaseLockAfter = Duration.ofMinutes(realseLockTime)

  def execute(implicit ec: scala.concurrent.ExecutionContext): Future[Result] = statsMetricRepository.reset().map {
    case Some(result) if result.wasAcknowledged() =>
      Result("Successfully reset metric counts")
    case _ =>
      val msg = s"Could not reset metric counts"
      logger.error(msg)
      Result(msg)
  }

}
