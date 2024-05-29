/*
 * Copyright 2022 HM Revenue & Customs
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

import play.sbt.PlayImport._
import sbt._

object AppDependencies {

  private val bootstrapVersion = "8.4.0"
  private val dcMessageLibraryVersion = "0.44.0"

  val compile = Seq(
    caffeine,
    "uk.gov.hmrc"   %% "bootstrap-backend-play-30" % bootstrapVersion,
    "uk.gov.hmrc"   %% "dc-message-library"        % dcMessageLibraryVersion,
    "com.beachape"  %% "enumeratum-play"           % "1.8.0",
    "org.typelevel" %% "cats-core"                 % "2.9.0",
    "com.networknt" % "json-schema-validator" % "1.0.77" excludeAll ("com.fasterxml.jackson.core", "jackson-databind"),
    "org.jsoup"     % "jsoup"                 % "1.15.4",
    "net.codingwell" %% "scala-guice" % "5.1.0"
  )

  val test = Seq(
    "uk.gov.hmrc"         %% "bootstrap-test-play-30"  % bootstrapVersion        % Test,
    "uk.gov.hmrc.mongo"   %% "hmrc-mongo-test-play-30" % "1.7.0"                 % Test,
    "org.scalatestplus"   %% "mockito-3-4"             % "3.2.10.0"              % Test,
    "uk.gov.hmrc"         %% "dc-message-library"      % dcMessageLibraryVersion % Test,
    "com.vladsch.flexmark" % "flexmark-all"            % "0.64.8"                % Test,
    "org.pegdown"          % "pegdown"                 % "1.6.0"                 % Test
  )
}
