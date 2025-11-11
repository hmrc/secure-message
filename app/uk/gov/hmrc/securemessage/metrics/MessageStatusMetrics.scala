/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.message.metrics

import play.api.Logging
import uk.gov.hmrc.securemessage.repository.SecureMessageRepository
import uk.gov.hmrc.mongo.metrix.MetricSource

import java.time.Instant
import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class MessageStatusMetrics @Inject() (secureMessageRepository: SecureMessageRepository)
    extends MetricSource with Logging {
  def metrics(implicit ec: ExecutionContext): Future[Map[String, Int]] = {
    val stasCount = secureMessageRepository.count()
    logger.warn(s"Message status metrics at ${Instant.now()} are $stasCount")
    stasCount
  }
}
