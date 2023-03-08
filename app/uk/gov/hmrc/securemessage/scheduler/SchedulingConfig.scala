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

import play.api.Configuration

trait SchedulingConfig {

  import scala.concurrent.duration._

  val name: String
  val configuration: Configuration

  def durationFromConfig(propertyKey: String): FiniteDuration =
    configuration
      .getOptional[FiniteDuration](s"scheduling.$name.$propertyKey")
      .getOrElse(throw new IllegalStateException(s"$propertyKey missing"))

  lazy val initialDelay = durationFromConfig("initialDelay")
  lazy val interval = durationFromConfig("interval")
  lazy val lockDuration = durationFromConfig("lockDuration")
}
