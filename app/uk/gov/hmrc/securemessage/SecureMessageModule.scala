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
import org.joda.time.{ DateTime, DateTimeZone }
import play.api.Configuration
import play.api.libs.concurrent.AkkaGuiceSupport
import uk.gov.hmrc.common.message.model.TimeSource
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.securemessage.scheduler.EmailAlertJob
import uk.gov.hmrc.securemessage.services.{ SecureMessageService, SecureMessageServiceImpl }
import uk.gov.hmrc.time.DateTimeUtils

import javax.inject.Singleton
import scala.concurrent.duration.FiniteDuration

class SecureMessageModule extends AbstractModule with AkkaGuiceSupport {

  override def configure(): Unit = {
    bind(classOf[DateTimeUtils]).to(classOf[TimeProvider])
    bind(classOf[SecureMessageService]).to(classOf[SecureMessageServiceImpl]).asEagerSingleton()
    bindActor[EmailAlertJob]("EmailAlertJob-actor")
    super.configure()
  }
  @Singleton
  @Provides
  def systemTimeSourceProvider(): TimeSource = new TimeSource() {
    override def now(): DateTime = DateTime.now.withZone(DateTimeZone.UTC)
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
  def mongoRetryFailedAfter(configuration: Configuration): Int = {
    configuration
      .getOptional[FiniteDuration]("mongodb.retryFailedAfter")
      .getOrElse(throw new RuntimeException("mongodb.retryFailedAfter not found in config"))
  }.toMillis.toInt

  @Provides
  @Named("retryInProgressAfter")
  @Singleton
  def mongoRetryInProgressAfter(configuration: Configuration): Int = {
    configuration
      .getOptional[FiniteDuration]("mongodb.retryInProgressAfter")
      .getOrElse(throw new RuntimeException("mongodb.retryInProgressAfter not found in config"))
  }.toMillis.toInt
  @Provides
  @Named("queryMaxTimeMs")
  @Singleton
  def queryMaxTimeMs(configuration: Configuration): Int = {
    configuration
      .getOptional[FiniteDuration]("mongodb.queryMaxTimeMs")
      .getOrElse(throw new RuntimeException("mongodb.queryMaxTimeMs not found in config"))
  }.toMillis.toInt

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

}

class TimeProvider extends DateTimeUtils
