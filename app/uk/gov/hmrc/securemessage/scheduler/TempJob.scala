/*
 * Copyright 2024 HM Revenue & Customs
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

import org.apache.pekko.actor.{ Actor, Timers }
import org.apache.pekko.stream.Materializer
import play.api.{ Configuration, Logging }
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.securemessage.models.core.Identifier
import uk.gov.hmrc.securemessage.repository.TempRepository

import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

class TempJob @Inject() (
  val configuration: Configuration,
  tempRepo: TempRepository,
  val auditConnector: AuditConnector
)(implicit ec: ExecutionContext, mat: Materializer)
    extends Actor with Timers with SchedulingConfig with Logging {

  override val name: String = "TempMessageQueryJob"

  override lazy val initialDelay = durationFromConfig("initialDelay")
  override lazy val interval = durationFromConfig("interval")
  lazy val sautr = configuration.get[String]("debug.value")

  timers.startTimerWithFixedDelay(StartMessageQuery, StartMessageQuery, initialDelay, interval)

  override def receive: Receive = { case StartMessageQuery =>
    logger.info(s"$name Started")
    val identifiers = Set(Identifier("sautr", sautr, Some("sautr")))
    for {
      message <- tempRepo.getLettersTempFunc(identifiers)
      _ <-
        auditConnector.sendEvent(DataEvent("secure-message", "MessageQuery", detail = Map("raw" -> message.toString())))
    } yield FinishedMessageQuery
  }

  case object StartMessageQuery
  case object FinishedMessageQuery

}
