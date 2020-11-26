import play.core.PlayVersion.current
import play.sbt.PlayImport._
import sbt.Keys.libraryDependencies
import sbt._

object AppDependencies {

  val compile = Seq(
    "uk.gov.hmrc" %% "bootstrap-backend-play-27" % "3.0.0",
    "uk.gov.hmrc" %% "simple-reactivemongo" % "7.30.0-play-27",
    "org.webjars" % "swagger-ui" % "3.35.0"
  )

  val test = Seq(
    "uk.gov.hmrc" %% "bootstrap-test-play-27" % "3.0.0" % Test,
    "com.typesafe.play" %% "play-test" % current % Test,
    "org.scalatestplus.play" %% "scalatestplus-play" % "4.0.3" % "test, it",
    "uk.gov.hmrc" %% "service-integration-test" % "0.12.0-play-27" % "test, it",
    "org.pegdown" % "pegdown" % "1.6.0" % "test, it"
  )
}
