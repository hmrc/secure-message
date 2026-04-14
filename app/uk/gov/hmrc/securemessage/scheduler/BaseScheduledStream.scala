/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.securemessage.scheduler

import org.apache.pekko.stream.scaladsl.{ Keep, Sink, Source }
import org.apache.pekko.stream.{ KillSwitches, Materializer, UniqueKillSwitch }
import play.api.inject.ApplicationLifecycle
import play.api.{ Configuration, Logging }
import uk.gov.hmrc.mongo.lock.LockService
import uk.gov.hmrc.securemessage.scheduler.BaseScheduledStream.Result

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.*
import scala.concurrent.{ ExecutionContext, Future }

trait BaseScheduledStream extends Logging {

  def name: String
  def configuration: Configuration
  def lockService: LockService
  def lifecycle: ApplicationLifecycle

  implicit val ec: ExecutionContext
  implicit val mat: Materializer

  private def durationFromConfig(propertyKey: String): FiniteDuration =
    configuration
      .getOptional[FiniteDuration](s"scheduling.$name.$propertyKey")
      .getOrElse(FiniteDuration(0, TimeUnit.MILLISECONDS))

  lazy val initialDelay: FiniteDuration = durationFromConfig("initialDelay")
  lazy val interval: FiniteDuration = durationFromConfig("interval")
  lazy val lockDuration: FiniteDuration = durationFromConfig("lockDuration")

  protected def lockTtl(default: Long): FiniteDuration =
    Option(lockDuration).getOrElse(default.hours)

  def processJob(): Future[Result]

  protected def startMessage: String = s"$name Start"

  protected def stopMessage: String = s"$name Stop"

  protected def withLock(f: => Future[String]): Future[Result] =
    lockService
      .withLock[String](f)
      .map:
        case Some(msg) => Result(msg)
        case None      => Result(s"$name cannot acquire mongo lock, not running")

  private def buildStream(): UniqueKillSwitch =
    Source
      .tick(initialDelay, interval, ())
      .viaMat(KillSwitches.single)(Keep.right)
      .mapAsync(1)(_ => runJob())
      .to(Sink.ignore)
      .run()

  private final def runJob(): Future[Unit] =
    logger.warn(startMessage)
    processJob().map: result =>
      logger.warn(result.message)
      logger.warn(stopMessage)

  // Kick everything off and register the stop hook
  protected val killSwitch: UniqueKillSwitch =
    val ks = buildStream()
    lifecycle.addStopHook: () =>
      logger.info(s"Shutting down $name stream using KillSwitch")
      ks.shutdown()
      Future.unit
    ks
}

object BaseScheduledStream:
  opaque type Result = String

  object Result:
    def apply(message: String): Result = message

  extension (r: Result) def message: String = r
