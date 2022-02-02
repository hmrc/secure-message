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

package uk.gov.hmrc.requestmapping.repository

import org.joda.time.DateTime
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.api.indexes.{ Index, IndexType }
import reactivemongo.api.{ DB, WriteConcern }
import reactivemongo.bson.{ BSONDocument, _ }
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

abstract class CacheStoreRepo[A, B] @Inject()(servicesConfig: ServicesConfig)(
  implicit val mongo: () => DB,
  ec: ExecutionContext) {

  private val cacheRepo = mongo().collection[BSONCollection]("cacheStore")

  private lazy val ttlExpiry: Int = servicesConfig.getInt("mongodb.expiryTime")

  private val ttlIndexName = "unqiue-requestMap"

  private lazy val ttlIndex = Index(
    Seq(("createdAt", IndexType.Ascending)),
    name = Some(ttlIndexName),
    options = BSONDocument("expireAfterSeconds" -> ttlExpiry)
  )

  private def setIndex(): Unit =
    cacheRepo.indexesManager.drop(ttlIndexName) onComplete { _ =>
      cacheRepo.indexesManager.ensure(ttlIndex)
    }

  setIndex()

  def findValue(key: A): Future[Option[BSONDocument]] = {
    val valueExists = BSONDocument(s"$key" -> BSONDocument("$exists" -> true))
    cacheRepo.find(valueExists, None).one[BSONDocument].map {
      case Some(bd) => Some(bd)
      case None     => None
    }
  }

  def createMap(key: A, value: B): Future[Boolean] = {
    val keyFormatCheck = key.toString
    val valueFormatCheck = value.toString
    val dateTime = DateTime.now().getMillis
    val createdAt = BSONDateTime.apply(dateTime)
    cacheRepo
      .insert(ordered = true, writeConcern = WriteConcern.Default)
      .one(BSONDocument(keyFormatCheck -> valueFormatCheck, "createdAt" -> createdAt))
  }.map(_.ok)
}
