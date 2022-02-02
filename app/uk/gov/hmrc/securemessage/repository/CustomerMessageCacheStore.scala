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

package uk.gov.hmrc.securemessage.repository

import reactivemongo.api.DB
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.requestmapping.repository.CacheStoreRepo

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

class CustomerMessageCacheStore @Inject()(servicesConfig: ServicesConfig)(
  implicit mongo: () => DB,
  ec: ExecutionContext)
    extends CacheStoreRepo[String, UUID](servicesConfig) {

  def findOrCreate(xRequestId: String)(implicit ec: ExecutionContext): Future[String] = {
    val randomId = UUID.randomUUID()
    findValue(xRequestId).flatMap {
      case Some(bsdoc) =>
        Future.successful(bsdoc.getAs[String](s"$xRequestId").get)
      case None =>
        createMap(xRequestId, randomId).flatMap {
          case true  => Future.successful(randomId.toString)
          case false => Future.successful(xRequestId)
        }
    }
  }
}
