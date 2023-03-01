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
import play.api.Configuration
import uk.gov.hmrc.securemessage.services.{ SecureMessageService, SecureMessageServiceImpl }
import uk.gov.hmrc.time.DateTimeUtils

import javax.inject.Singleton

class SecureMessageModule extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[DateTimeUtils]).to(classOf[TimeProvider])
    bind(classOf[SecureMessageService]).to(classOf[SecureMessageServiceImpl]).asEagerSingleton()
    super.configure()
  }

  @Provides
  @Named("app-name")
  @Singleton
  def appNameProvider(configuration: Configuration): String =
    configuration
      .getOptional[String]("appName")
      .getOrElse(throw new RuntimeException("App name not found in config"))
}

class TimeProvider extends DateTimeUtils
