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

import uk.gov.hmrc.DefaultBuildSettings.integrationTestSettings
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin.publishingSettings
import com.lucidchart.sbt.scalafmt.ScalafmtCorePlugin.autoImport._
import sbt.Resolver
import uk.gov.hmrc.{ ExternalService, ServiceManagerPlugin }
import uk.gov.hmrc.ServiceManagerPlugin.Keys.itDependenciesList
import java.net.URL
import SbtBobbyPlugin.BobbyKeys.bobbyRulesURL
import SbtBobbyPlugin.BobbyKeys.validate

val appName = "secure-message"

val silencerVersion = "1.7.0"

lazy val microservice = Project(appName, file("."))
  .enablePlugins(
    play.sbt.PlayScala,
    SbtAutoBuildPlugin,
    SbtGitVersioning,
    SbtDistributablesPlugin,
    SwaggerPlugin
  )
  .settings(
    majorVersion := 0,
    scalaVersion := "2.12.12",
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test,
    // ***************
    // Use the silencer plugin to suppress warnings
    scalacOptions += "-P:silencer:pathFilters=app.routes",
    libraryDependencies ++= Seq(
      compilerPlugin(
        "com.github.ghik" % "silencer-plugin" % silencerVersion cross CrossVersion.full
      ),
      "com.github.ghik" % "silencer-lib" % silencerVersion % Provided cross CrossVersion.full
    )
    // ***************
  )
  .settings(publishingSettings: _*)
  .configs(IntegrationTest)
  .settings(integrationTestSettings(): _*)
  .settings(
    resolvers ++= Seq(
      Resolver.jcenterRepo,
      Resolver.bintrayRepo("hmrc", "releases"),
      Resolver.bintrayRepo("jetbrains", "markdown"),
      "bintray-djspiewak-maven" at "https://dl.bintray.com/djspiewak/maven"
    ),
    inConfig(IntegrationTest)(
      scalafmtCoreSettings ++
        Seq(compileInputs in compile := Def.taskDyn {
          val task = test in (resolvedScoped.value.scope in scalafmt.key)
          val previousInputs = (compileInputs in compile).value
          task.map(_ => previousInputs)
        }.value)
    )
  )
  .settings(ServiceManagerPlugin.serviceManagerSettings)
  .settings(itDependenciesList := List(
    ExternalService("DATASTREAM")
  ))

lazy val compileScalastyle = taskKey[Unit]("compileScalastyle")
// scalastyle >= 0.9.0
compileScalastyle := scalastyle.in(Compile).toTask("").value
(compile in Compile) := ((compile in Compile) dependsOn compileScalastyle).value
swaggerDomainNameSpaces := Seq("uk.gov.hmrc.securemessage.models.api")
swaggerTarget := baseDirectory.value / "conf"
swaggerFileName := "secure-message-swagger.json"
swaggerPrettyJson := true
swaggerRoutesFile := "prod.routes"
coverageEnabled := true
wartremoverErrors in (Compile, compile) ++= Warts.all
wartremoverExcluded ++= routes.in(Compile).value
bobbyRulesURL := Some(new URL("https://webstore.tax.service.gov.uk/bobby-config/deprecated-dependencies.json"))
addCompilerPlugin("org.wartremover" %% "wartremover" % "2.4.13" cross CrossVersion.full)
scalacOptions += "-P:wartremover:traverser:org.wartremover.warts.Unsafe"
scalafmtOnCompile := true
