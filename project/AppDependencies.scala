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
    "org.scalatest" %% "scalatest" % "3.2.3" % Test,
    "com.typesafe.play" %% "play-test" % current % Test,
    "com.vladsch.flexmark" % "flexmark-all" % "0.36.8" % "test, it",
    "org.scalatestplus.play" %% "scalatestplus-play" % "4.0.3" % "test, it"
  )
}
