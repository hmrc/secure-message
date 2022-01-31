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
import play.api.libs.json.{ JsObject, Json }
import reactivemongo.api.indexes.{ Index, IndexType }
import reactivemongo.api.{ DB, WriteConcern }
import reactivemongo.bson.{ BSONDocument, BSONObjectID }
import uk.gov.hmrc.requestmapping.model.RequestMap
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

class RequestMappingRepo @Inject()(servicesConfig: ServicesConfig)(
  implicit mongo: () => DB,
  executionContext: ExecutionContext)
    extends ReactiveRepository[RequestMap, BSONObjectID]("requestMapping", mongo, RequestMap.formats) {

  //TODO get index to work with timeout
  lazy val ttlExpiry: Int = servicesConfig.getInt("mongodb.expiryTime")
//
//  override def indexes: Seq[Index] =
//    Seq(
//      Index(
//        key = Seq("xRequestId" -> IndexType.Ascending),
//        name = Some("unique-request-map"),
//        unique = false,
//        sparse = true,
//        options = BSONDocument("expireAfterSeconds" -> BSONInteger(1))
//      ))

  private val ttlIndexName = "unqiue-requestMap"

  private lazy val ttlIndex = Index(
    Seq(("createdAt", IndexType.Ascending)),
    name = Some(ttlIndexName),
    options = BSONDocument("expireAfterSeconds" -> ttlExpiry)
  )

  private def setIndex(): Unit =
    collection.indexesManager.drop(ttlIndexName) onComplete { _ =>
      collection.indexesManager.ensure(ttlIndex)
    }

  setIndex()

  def findRequestMap(xRequest: String): Future[Option[RequestMap]] =
    collection.find[JsObject, RequestMap](Json.obj("xRequest" -> xRequest), None).one[RequestMap]

  def insertRequestMap(requestMap: Option[RequestMap], xRequest: String): Future[Option[RequestMap]] =
    requestMap match {
      case Some(rm) => Future.successful(Some(rm))
      case None =>
        val newUUID = UUID.randomUUID().toString
        val newRequestMap = RequestMap(xRequest, newUUID, DateTime.now())
        collection
          .insert(ordered = false, writeConcern = WriteConcern.Default)
          .one(newRequestMap)
          .map(_ => Some(newRequestMap))
          .recover {
            case error =>
              logger.error(s"There was an error with mongo Error: $error")
              None
          }
    }
}
