import uk.gov.hmrc.DefaultBuildSettings.integrationTestSettings
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin.publishingSettings
import com.lucidchart.sbt.scalafmt.ScalafmtCorePlugin.autoImport._
import sbt.Resolver

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

swaggerDomainNameSpaces := Seq("models")
swaggerRoutesFile := "app.routes"
coverageEnabled := true
wartremoverWarnings ++= Warts.all
