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

package uk.gov.hmrc.securemessage.metrics

import org.apache.pekko.actor.ActorSystem
import play.api.{ Configuration, Logging }
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.metrix.MetricOrchestrator
import uk.gov.hmrc.securemessage.scheduler.ScheduledJob

import javax.inject.{ Inject, Named, Singleton }
import scala.concurrent.duration.*
import scala.concurrent.{ ExecutionContext, Future }
import scala.language.postfixOps
import scala.util.{ Failure, Success, Try }

@Singleton
class MessageMain @Inject() (
  actorSystem: ActorSystem,
  configuration: Configuration,
  scheduledJobs: Seq[ScheduledJob],
  metricOrchestator: MetricOrchestrator,
  mongo: MongoComponent,
  @Named("metricsActive") metricsActive: Boolean,
  @Named("scheduledJobsEnabled") scheduledJobsEnabled: Boolean
)(implicit val ec: ExecutionContext)
    extends Logging {

  logger.warn(
    s" About to initiate scheduler with the flag 'scheduled-jobs.enabled' being set as $scheduledJobsEnabled "
  )
  if (scheduledJobsEnabled) {
    scheduledJobs.foreach(scheduleJob)
    logger.warn(s"Initiated scheduled jobs")
  }

  val refreshInterval: Long = configuration
    .getOptional[FiniteDuration](s"microservice.metrics.gauges.interval")
    .getOrElse(throw new RuntimeException(s"microservice.metrics.gauges.interval is not specified"))
    .toMillis

  if (metricsActive) {
    logger.warn(s"Metric process has scheduled for every $refreshInterval milliseconds")
    actorSystem.scheduler.scheduleWithFixedDelay(60 seconds, refreshInterval milliseconds) { () =>
      Try {
        metricOrchestator
          .attemptMetricRefresh()
          .foreach(_.log())
      } match {
        case Failure(e) => logger.error(s"An error occurred processing metrics: ${e.getMessage}", e)
        case Success(_) => ()
      }
    }
  }

  private def scheduleJob(job: ScheduledJob)(implicit ec: ExecutionContext): Unit = {
    val _ = actorSystem.scheduler.scheduleWithFixedDelay(job.initialDelay, job.interval) { () =>
      logger.warn(s"Next schedule statrretd for ${job.name}")
      job.execute.foreach { result =>
        logger.warn(s"Job ${job.name} result: ${result.message}")
      }
    }
  }
}
