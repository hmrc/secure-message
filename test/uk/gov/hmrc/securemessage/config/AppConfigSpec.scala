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

package uk.gov.hmrc.securemessage.config

import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

class AppConfigSpec extends PlaySpec {
  "AppConfig" should {
    "load configuration values" in {
      val mockConfig = play.api.Configuration(
        "auditing.enabled"                   -> true,
        "microservice.metrics.graphite.host" -> "localhost",
        "microservice.services.auth.host"    -> "localhost",
        "microservice.services.auth.port"    -> 8500
      )
      val mockServicesConfig = ServicesConfig(mockConfig)
      val appConfig = new AppConfig(mockConfig, mockServicesConfig)
      appConfig.auditingEnabled mustBe true
      appConfig.authBaseUrl mustBe "http://localhost:8500"
      appConfig.graphiteHost mustBe "localhost"
    }

  }

}
