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

import play.sbt.PlayImport.PlayKeys
import sbt.Keys._
import uk.gov.hmrc.DefaultBuildSettings

val appName = "secure-message"

ThisBuild / majorVersion := 0
ThisBuild / scalaVersion := "2.13.12"


lazy val microservice = Project(appName, file("."))
  .enablePlugins(
    play.sbt.PlayScala,
    SbtDistributablesPlugin
  )
  .disablePlugins(JUnitXmlReportPlugin)
  .settings(
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test,
    scalacOptions ++= Seq(
      // Silence unused warnings on Play `routes` files
      "-Wconf:cat=unused-imports&src=.*routes.*:s",
      "-Wconf:cat=unused-privates&src=.*routes.*:s"
    ),
    routesImport ++= Seq(
      "uk.gov.hmrc.securemessage.controllers.binders._",
      "uk.gov.hmrc.securemessage.controllers.model._",
      "uk.gov.hmrc.securemessage.controllers.model.common._",
      "uk.gov.hmrc.securemessage.models.core.CustomerEnrolment",
      "uk.gov.hmrc.securemessage.models.core.FilterTag"
    )
  )
  .settings(ScoverageSettings())

lazy val it = project
  .enablePlugins(PlayScala)
  .dependsOn(microservice % "test->test") // the "test->test" allows reusing test code and test dependencies
  .settings(DefaultBuildSettings.itSettings())

lazy val compileScalastyle = taskKey[Unit]("compileScalastyle1")
compileScalastyle := (Compile / scalastyle).toTask("").value
(Compile / compile) := ((Compile / compile) dependsOn compileScalastyle).value

PlayKeys.playDefaultPort := 9051

dependencyUpdatesFailBuild := false
(Compile / compile) := ((Compile / compile) dependsOn dependencyUpdates).value
dependencyUpdatesFilter -= moduleFilter(organization = "uk.gov.hmrc")
dependencyUpdatesFilter -= moduleFilter(organization = "org.scala-lang")
dependencyUpdatesFilter -= moduleFilter(organization = "com.github.ghik")
dependencyUpdatesFilter -= moduleFilter(organization = "org.playframework")
dependencyUpdatesFilter -= moduleFilter(organization = "org.scalatestplus.play")
dependencyUpdatesFilter -= moduleFilter(organization = "org.webjars")
dependencyUpdatesFilter -= moduleFilter(name = "enumeratum-play")
dependencyUpdatesFilter -= moduleFilter(organization = "com.lucidchart")
dependencyUpdatesFilter -= moduleFilter(name = "flexmark-all")

Compile / doc / sources := Seq.empty

//TODO make bellow work and rename resources/service/ContentValidation/*html.txt to html
Test / resourceDirectory := baseDirectory.value / "test" / "resources"
Test / resources / excludeFilter := HiddenFileFilter || "*.html"

Test / test := (Test / test)
  .dependsOn(scalafmtCheckAll)
  .value

it / test := (it / Test / test)
  .dependsOn(scalafmtCheckAll, it/scalafmtCheckAll)
  .value
