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
import play.api.libs.json.Json._
import play.api.libs.json.{ JsArray, JsObject, JsString, Json }
import reactivemongo.bson.{ BSONDateTime, BSONDocument, BSONNull, BSONObjectID }
import uk.gov.hmrc.mongo.MongoConnector
import uk.gov.hmrc.securemessage.{ SecureMessageError, StoreError }
import uk.gov.hmrc.securemessage.models.core.{ FilterTag, Identifier, Letter }
import reactivemongo.play.json.ImplicitBSONHandlers.BSONDocumentWrites
import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

class MessageRepository @Inject()(implicit connector: MongoConnector)
    extends SecureMessageRepository[Letter, BSONObjectID](
      "message",
      connector.db,
      Letter.letterFormat
    ) {

  def getLetters(identifiers: Set[Identifier], tags: Option[List[FilterTag]])(
    implicit ec: ExecutionContext): Future[List[Letter]] =
    getMessages(identifiers, tags)

  def getLetter(id: String, identifiers: Set[Identifier])(
    implicit ec: ExecutionContext): Future[Either[SecureMessageError, Letter]] = getMessage(id, identifiers)

  def addReadTime(id: String)(implicit ec: ExecutionContext): Future[Either[SecureMessageError, Unit]] =
    withCurrentTime { implicit time =>
      BSONObjectID.parse(id) match {
        case Success(bsonId) =>
          collection
            .update(ordered = false)
            .one(
              BSONDocument("_id"  -> bsonId, "readTime" -> BSONNull),
              BSONDocument("$set" -> BSONDocument("readTime" -> BSONDateTime(time.getMillis)))
            )
            .map(_ => Right(()))
        case Failure(error) => Future.successful(Left(StoreError(error.getMessage, None)))
      }
    }

  override protected def findByIdentifierQuery(identifier: Identifier): Seq[(String, JsValueWrapper)] =
    identifier.enrolment match {
      case Some(enrolment) =>
        Seq[(String, JsValueWrapper)](
          "recipient.identifier.name"  -> JsString(enrolment),
          "recipient.identifier.value" -> JsString(identifier.value)
        )
      case None => Seq("" -> arr())
    }

  override protected def tagQuery(tags: List[FilterTag]): JsObject =
    Json.obj(
      "$or" ->
        tags.foldLeft(JsArray())((acc, t) => acc ++ Json.arr(Json.obj(s"tags.${t.key}" -> JsString(t.value))))
    )

}
