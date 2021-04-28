/*
 * Copyright 2021 HM Revenue & Customs
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

import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json.{ JsArray, JsObject, JsString, Json }
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.mongo.{ MongoConnector, ReactiveRepository }
import uk.gov.hmrc.securemessage.{ InvalidBsonId, LetterNotFound, SecureMessageError }
import uk.gov.hmrc.securemessage.models.core.{ Identifier, Letter }

import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

class MessageRepository @Inject()(implicit connector: MongoConnector)
    extends ReactiveRepository[Letter, BSONObjectID](
      "message",
      connector.db,
      Letter.letterFormat
    ) {

  def getLetter(id: String, identifiers: Set[Identifier])(
    implicit ec: ExecutionContext): Future[Either[SecureMessageError, Letter]] =
    BSONObjectID.parse(id) match {
      case Success(bsonId) =>
        collection
          .find[JsObject, Letter](
            selector = Json.obj("_id" -> bsonId)
              deepMerge
                identifierQuery(identifiers)
          )
          .one[Letter] map {
          case Some(c) => Right(c)
          case None => {
            logger.debug(identifiers.toString())
            Left(LetterNotFound(s"Letter not found"))
          }
        }
      case Failure(exception) =>
        Future.successful(Left(InvalidBsonId(s"Invalid BsonId: ${exception.getMessage} ", Some(exception))))
    }

  private def identifierQuery(identifiers: Set[Identifier]): JsObject =
    Json.obj(
      "$or" ->
        identifiers.foldLeft(JsArray())((acc, i) => acc ++ Json.arr(Json.obj(findByIdentifierQuery(i): _*)))
    )

  private def findByIdentifierQuery(identifier: Identifier): Seq[(String, JsValueWrapper)] =
    identifier.enrolment match {
      case Some(enrolment) =>
        Seq(
          "recipient.identifier.name"      -> JsString(identifier.name),
          "recipient.identifier.value"     -> JsString(identifier.value),
          "recipient.identifier.enrolment" -> JsString(enrolment)
        )
      case None =>
        Seq(
          "recipient.identifier.name"  -> JsString(identifier.name),
          "recipient.identifier.value" -> JsString(identifier.value)
        )
    }

}
