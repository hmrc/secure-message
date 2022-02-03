/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.securemessage.services

import play.api.Logger
import uk.gov.hmrc.requestcache.PersistentMap

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

class CustomerMessageCacheService @Inject()(persistentMap: PersistentMap[String, String]) {
  val logger = Logger(getClass.getName)
  def findOrCreate(xRequestId: String, eisId: UUID = UUID.randomUUID())(implicit ec: ExecutionContext): Future[String] = {
    val randomId = eisId.toString
    for {
      get    <- persistentMap.get(xRequestId)
      insert <- if (get.nonEmpty) Future(false) else persistentMap.insert(xRequestId, randomId)
    } yield
      insert match {
        case false => xRequestId
        case true  => randomId
      }
  }.recover {
    case error =>
      logger.error(s"There was an issue when trying to create a customer message: Error - $error")
      xRequestId
  }
}
