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

package uk.gov.hmrc.securemessage

import com.google.inject.{ AbstractModule, Provides }
import com.google.inject.name.Named

import java.time.Instant
import play.api.Configuration
import play.api.libs.concurrent.PekkoGuiceSupport
import uk.gov.hmrc.common.message.model.TimeSource
import uk.gov.hmrc.message.metrics.MessageStatusMetrics
import uk.gov.hmrc.mongo.{ MongoComponent, TimestampSupport }
import uk.gov.hmrc.mongo.lock.{ LockRepository, LockService, MongoLockRepository }
import uk.gov.hmrc.mongo.metrix.{ MetricOrchestrator, MetricSource, MongoMetricRepository }
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.metrics.Metrics
import uk.gov.hmrc.securemessage.metrics.{ MessageMain, MetricsLock }
import uk.gov.hmrc.securemessage.repository.StatsMetricRepository
import uk.gov.hmrc.securemessage.scheduler.EmailAlertJob
import uk.gov.hmrc.securemessage.services.{ SecureMessageService, SecureMessageServiceImpl }
import uk.gov.hmrc.securemessage.utils.DateTimeUtils

import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{ Duration, FiniteDuration }

class SecureMessageModule extends AbstractModule with PekkoGuiceSupport {

  override def configure(): Unit = {
    bind(classOf[MessageMain]).asEagerSingleton()
    bind(classOf[DateTimeUtils]).to(classOf[TimeProvider])
    bind(classOf[SecureMessageService]).to(classOf[SecureMessageServiceImpl]).asEagerSingleton()
    bindActor[EmailAlertJob]("EmailAlertJob-actor")
    super.configure()
  }

  @Provides
  @Singleton
  def lockRepositoryProvider(mongo: MongoComponent, timestampSupport: TimestampSupport)(implicit
    ec: ExecutionContext
  ): LockRepository =
    new MongoLockRepository(mongo, timestampSupport)

  @Provides
  @Singleton
  def mongoMetricRepositoryProvider(mongo: MongoComponent)(implicit ec: ExecutionContext): MongoMetricRepository =
    new MongoMetricRepository(mongo)

  @Provides
  @Singleton
  def metricsOrchestratorProvider(
    statusMetrics: MessageStatusMetrics,
    statsRepo: StatsMetricRepository,
    configuration: Configuration,
    mongoMetricRepository: MongoMetricRepository,
    metrics: Metrics,
    lockRepository: LockRepository
  ): MetricOrchestrator = {

    val refreshInterval = configuration.getMillis(s"microservice.metrics.gauges.interval")
    val metricsLock = MetricsLock("message-metrics", Duration(refreshInterval, TimeUnit.MILLISECONDS), lockRepository)
    val sources: List[MetricSource] = List(statusMetrics, statsRepo)
    new MetricOrchestrator(
      sources,
      LockService(metricsLock.lockRepository, metricsLock.lockId, metricsLock.ttl),
      mongoMetricRepository,
      metrics.defaultRegistry
    )
  }

  @Singleton
  @Provides
  def systemTimeSourceProvider(): TimeSource = new TimeSource() {
    override def now(): Instant = Instant.now
  }

  @Provides
  @Named("app-name")
  @Singleton
  def appNameProvider(configuration: Configuration): String =
    configuration
      .getOptional[String]("appName")
      .getOrElse(throw new RuntimeException("App name not found in config"))

  @Provides
  @Named("retryFailedAfter")
  @Singleton
  def mongoRetryFailedAfter(configuration: Configuration): Int =
    configuration
      .getOptional[FiniteDuration]("mongodb.retryFailedAfter")
      .getOrElse(throw new RuntimeException("mongodb.retryFailedAfter not found in config"))
      .toMillis
      .toInt

  @Provides
  @Named("retryInProgressAfter")
  @Singleton
  def mongoRetryInProgressAfter(configuration: Configuration): Int =
    configuration
      .getOptional[FiniteDuration]("mongodb.retryInProgressAfter")
      .getOrElse(throw new RuntimeException("mongodb.retryInProgressAfter not found in config"))
      .toMillis
      .toInt
  @Provides
  @Named("queryMaxTimeMs")
  @Singleton
  def queryMaxTimeMs(configuration: Configuration): Int =
    configuration
      .getOptional[FiniteDuration]("mongodb.queryMaxTimeMs")
      .getOrElse(throw new RuntimeException("mongodb.queryMaxTimeMs not found in config"))
      .toMillis
      .toInt

  @Provides
  @Named("invalid-template-ids-push-notifications")
  @Singleton
  def invalidTemplateIdsForPushNotifications(
    configuration: Configuration
  ): List[String] =
    configuration
      .getOptional[Seq[String]]("invalidTemplateIdsForPushNotifications")
      .getOrElse(
        throw new RuntimeException(
          "key invalidTemplateIdsForPushNotifications not defined in config"
        )
      )
      .toList

  @Provides
  @Named("mobile-push-notifications-orchestration-base-url")
  @Singleton
  def mobilePushNotificationsConnectorBaseUrl(servicesConfig: ServicesConfig): String =
    servicesConfig.baseUrl("mobile-push-notifications-orchestration")

  @Provides
  @Named("metricsActive")
  @Singleton
  def isSecureMessageMetricsActive(configuration: Configuration): Boolean =
    configuration.getOptional[Boolean]("metrics.active").getOrElse(false)
}

class TimeProvider extends DateTimeUtils
