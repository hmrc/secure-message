/*
 * Copyright 2020 HM Revenue & Customs
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

import play.core.PlayVersion.current
import sbt._

object AppDependencies {

  val compile = Seq(
    "uk.gov.hmrc"       %% "bootstrap-backend-play-27" % "4.0.0",
    "uk.gov.hmrc"       %% "simple-reactivemongo"      % "7.31.0-play-27",
    "uk.gov.hmrc"       %% "time"                      % "3.19.0",
    "org.webjars"       % "swagger-ui"                 % "3.42.0",
    "com.beachape"      %% "enumeratum-play-json"      % "1.6.1",
    "com.typesafe.play" %% "play-json-joda"            % "2.9.1",
    "org.typelevel"     %% "cats-core"                 % "2.4.0"
  )

  val test = Seq(
    "uk.gov.hmrc"            %% "bootstrap-test-play-27"   % "4.0.0"          % Test,
    "uk.gov.hmrc"            %% "reactivemongo-test"       % "4.22.0-play-27" % Test,
    "com.typesafe.play"      %% "play-test"                % current          % Test,
    "org.scalatestplus.play" %% "scalatestplus-play"       % "4.0.3"          % "test, it",
    "org.mockito"            % "mockito-core"              % "3.7.7"          % "test, it",
    "uk.gov.hmrc"            %% "service-integration-test" % "0.13.0-play-27" % "test, it",
    "org.pegdown"            % "pegdown"                   % "1.6.0"          % "test, it"
  )
}
