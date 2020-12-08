import play.core.PlayVersion.current
import play.sbt.PlayImport._
import sbt.Keys.libraryDependencies
import sbt._

object AppDependencies {

  val compile = Seq(
    "uk.gov.hmrc" %% "bootstrap-backend-play-27" % "3.2.0",
    "uk.gov.hmrc" %% "simple-reactivemongo"      % "7.31.0-play-27",
    "org.webjars" % "swagger-ui"                 % "3.37.2"
  )

  val test = Seq(
    "uk.gov.hmrc"            %% "bootstrap-test-play-27"   % "3.2.0"          % Test,
    "com.typesafe.play"      %% "play-test"                % current          % Test,
    "org.scalatestplus.play" %% "scalatestplus-play"       % "5.1.0"          % "test, it",
    "uk.gov.hmrc"            %% "service-integration-test" % "0.13.0-play-27" % "test, it",
    "org.pegdown"            % "pegdown"                   % "1.6.0"          % "test, it"
  )
}
