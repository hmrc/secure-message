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
  
  private val bootstrapVersion = "7.15.0"

  val compile = Seq(
    ehcache,
    "uk.gov.hmrc"       %% "bootstrap-backend-play-28" % bootstrapVersion,
    "uk.gov.hmrc"       %% "dc-message-library"        % "0.35.0",
    "org.webjars"       %  "swagger-ui"                % "3.50.0",
    "com.beachape"      %% "enumeratum-play"           % "1.5.17",
    "com.typesafe.play" %% "play-json-joda"            % "2.9.4",
    "org.typelevel"     %% "cats-core"                 % "2.9.0",
    "com.networknt"     %  "json-schema-validator"     % "1.0.77" exclude("com.fasterxml.jackson.core", "jackson-databind"),
    "org.jsoup"         %  "jsoup"                     % "1.15.4",
    "net.codingwell"    %% "scala-guice"               % "5.1.0"
  )

  val test = Seq(
    "uk.gov.hmrc"            %% "bootstrap-test-play-28"   % bootstrapVersion % "test, it",
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-test-play-28"  % "0.74.0"         % "test, it",
    "org.scalatestplus"      %% "mockito-3-4"              % "3.2.10.0"       % "test, it",
    "uk.gov.hmrc"            %% "dc-message-library"       % "0.32.0"         % "test, it",
    "com.vladsch.flexmark"   %  "flexmark-all"             % "0.36.8"         % "test, it",
    "org.pegdown"            %  "pegdown"                  % "1.6.0"          % "test, it"
  )

  val jettyVersion = "11.0.15"

  val overrides = Seq(
    "org.eclipse.jetty" % "jetty-http" % jettyVersion,
    "org.eclipse.jetty" % "jetty-server" % jettyVersion
  )

}
