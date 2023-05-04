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

import com.lucidchart.sbt.scalafmt.ScalafmtCorePlugin.autoImport._
import uk.gov.hmrc.DefaultBuildSettings.integrationTestSettings
import uk.gov.hmrc.ServiceManagerPlugin.Keys.itDependenciesList
import uk.gov.hmrc.{ ExternalService, ServiceManagerPlugin }

val appName = "secure-message"

lazy val microservice = Project(appName, file("."))
  .enablePlugins(
    play.sbt.PlayScala,
    SbtDistributablesPlugin,
    SwaggerPlugin
  )
  .disablePlugins(JUnitXmlReportPlugin)
  .settings(
    majorVersion := 0,
    scalaVersion := "2.13.8",
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test,
    scalacOptions ++= Seq(
      // Silence unused warnings on Play `routes` files
      "-Wconf:cat=unused-imports&src=.*routes.*:s",
      "-Wconf:cat=unused-privates&src=.*routes.*:s"
    ),
    routesImport ++= Seq(
      "uk.gov.hmrc.securemessage.controllers.binders._",
//      "uk.gov.hmrc.securemessage.controllers.SecureMessageController",
      "uk.gov.hmrc.securemessage.controllers.model._",
      "uk.gov.hmrc.securemessage.controllers.model.common._",
      "uk.gov.hmrc.securemessage.models.core.CustomerEnrolment",
      "uk.gov.hmrc.securemessage.models.core.FilterTag"
    )
  )
  .configs(IntegrationTest)
  .settings(integrationTestSettings(): _*)
  .settings(
    inConfig(IntegrationTest)(
      scalafmtCoreSettings ++
        Seq(compile / compileInputs := Def.taskDyn {
          val task = (resolvedScoped.value.scope in scalafmt.key) / test
          val previousInputs = (compile / compileInputs).value
          task.map(_ => previousInputs)
        }.value)
    )
  )
  .settings(ServiceManagerPlugin.serviceManagerSettings)
  .settings(itDependenciesList := List(
    ExternalService("DATASTREAM"),
    ExternalService("AUTH"),
    ExternalService("AUTH_LOGIN_API"),
    ExternalService("IDENTITY_VERIFICATION"),
    ExternalService("USER_DETAILS"),
    ExternalService("ENTITY_RESOLVER"),
    ExternalService("CHANNEL_PREFERENCES"),
    ExternalService("CUSTOMS_DATA_STORE"),
    ExternalService("CUSTOMS_FINANCIALS_HODS_STUB"),
    ExternalService("EMAIL"),
    ExternalService("MAILGUN_STUB"),
    ExternalService("SECURE_MESSAGE_STUB"),
    ExternalService("MESSAGE")
  ))
  .settings(ScoverageSettings())

lazy val compileScalastyle = taskKey[Unit]("compileScalastyle1")
compileScalastyle := (Compile / scalastyle).toTask("").value
(Compile / compile) := ((Compile / compile) dependsOn compileScalastyle).value

swaggerDomainNameSpaces := Seq(
  "uk.gov.hmrc.securemessage.controllers.model",
  "uk.gov.hmrc.securemessage.controllers.model.cdcm.read",
  "uk.gov.hmrc.securemessage.controllers.model.cdcm.write",
  "uk.gov.hmrc.securemessage.controllers.model.common",
  "uk.gov.hmrc.securemessage.controllers.model.common.read",
  "uk.gov.hmrc.securemessage.controllers.model.common.write",
  "uk.gov.hmrc.securemessage.models.core.CustomerEnrolment",
  "uk.gov.hmrc.securemessage.models.core.FilterTag"
)
swaggerTarget := baseDirectory.value / "public"
swaggerFileName := "schema.json"
swaggerPrettyJson := true
swaggerRoutesFile := "prod.routes"
swaggerV3 := true
//scalafmtOnCompile := true
PlayKeys.playDefaultPort := 9051

dependencyUpdatesFailBuild := false
(Compile / compile) := ((Compile / compile) dependsOn dependencyUpdates).value
dependencyUpdatesFilter -= moduleFilter(organization = "uk.gov.hmrc")
dependencyUpdatesFilter -= moduleFilter(organization = "org.scala-lang")
dependencyUpdatesFilter -= moduleFilter(organization = "com.github.ghik")
dependencyUpdatesFilter -= moduleFilter(organization = "com.typesafe.play")
dependencyUpdatesFilter -= moduleFilter(organization = "org.scalatestplus.play")
dependencyUpdatesFilter -= moduleFilter(organization = "org.webjars")
dependencyUpdatesFilter -= moduleFilter(name = "enumeratum-play")
dependencyUpdatesFilter -= moduleFilter(organization = "com.lucidchart")
dependencyUpdatesFilter -= moduleFilter(name = "flexmark-all")

Compile / doc / sources := Seq.empty

scalaModuleInfo ~= (_.map(_.withOverrideScalaVersion(true)))
//TODO make bellow work and rename resources/service/ContentValidation/*html.txt to html
Test / resourceDirectory := baseDirectory.value / "test" / "resources"
Test / resources / excludeFilter := HiddenFileFilter || "*.html"
