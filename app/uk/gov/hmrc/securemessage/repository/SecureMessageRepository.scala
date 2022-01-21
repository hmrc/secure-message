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

import cats.implicits.toFoldableOps
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json._
import reactivemongo.api.Cursor.ErrorHandler
import reactivemongo.api.{ Cursor, DB, ReadConcern }
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.securemessage.models.core.{ Conversation, Count, FilterTag, Identifier }
import uk.gov.hmrc.securemessage.{ InvalidBsonId, MessageNotFound, SecureMessageError }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }
import scala.reflect.runtime.universe.{ TypeTag, typeOf }

abstract class SecureMessageRepository[A: TypeTag, ID](
  collectionName: String,
  mongo: () => DB,
  val domainFormat: Format[A],
  idFormat: Format[ID] = ReactiveMongoFormats.objectIdFormats.asInstanceOf[Format[ID]])
    extends ReactiveRepository[A, ID](collectionName, mongo, domainFormat, idFormat) {
  implicit val format: OFormat[A] = domainFormat.asInstanceOf[OFormat[A]]

  protected def messagesQuerySelector(identifiers: Set[Identifier], tags: Option[List[FilterTag]]): JsObject =
    (identifiers, tags) match {
      case (identifiers, _) if identifiers.isEmpty => //TODO: move this case to service
        JsObject.empty
      case (identifiers, None) =>
        identifierQuery(identifiers)
      case (identifiers, Some(Nil)) =>
        identifierQuery(identifiers)
      case (identifiers, Some(tags)) =>
        Json.obj(
          "$and" -> Json.arr(
            identifierQuery(identifiers),
            tagQuery(tags)
          ))
      case _ =>
        JsObject.empty
    }

  protected def getMessages(identifiers: Set[Identifier], tags: Option[List[FilterTag]])(
    implicit ec: ExecutionContext): Future[List[A]] = {
    val querySelector = messagesQuerySelector(identifiers, tags)
    logger.info(s"[getMessages] querySelector: $querySelector")
    if (querySelector != JsObject.empty) {
      collection
        .find[JsObject, A](
          selector = querySelector,
          None
        )
        .sort(Json.obj("_id" -> -1))
        .cursor[A]()
        .collect[List](-1, dbErrorHandler[List[A]])
    } else {
      Future(List())
    }
  }

  protected def getMessagesCount(identifiers: Set[Identifier], tags: Option[List[FilterTag]])(
    implicit ec: ExecutionContext): Future[Count] = {
    val querySelector = messagesQuerySelector(identifiers, tags)
    val totalCount: Future[Long] = getCount(querySelector)
    val unreadCount: Future[Long] = totalCount.flatMap { total =>
      if (total == 0) {
        Future.successful(0L)
      } else if (collectionName == "conversation") {
        getConversationsUnreadCount(identifiers, tags)
      } else {
        getMessageUnreadCount(querySelector)
      }
    }

    for {
      total  <- getCount(querySelector)
      unread <- unreadCount
    } yield Count(total, unread)
  }

  private[repository] def conversationRead(conversation: A, identifier: Set[Identifier]): Long =
    if (conversation.asInstanceOf[Conversation].unreadMessagesFor(identifier).isEmpty) 0 else 1

  private[repository] def getConversationsUnreadCount(identifiers: Set[Identifier], tags: Option[List[FilterTag]])(
    implicit ec: ExecutionContext): Future[Long] =
    getMessages(identifiers, tags).map { conversations =>
      conversations.foldMap(c => conversationRead(c, identifiers))
    }

  private def getMessageUnreadCount(baseQuery: JsObject)(implicit ec: ExecutionContext) =
    getCount(Json.obj("$and" -> Json.arr(baseQuery, Json.obj("readTime" -> JsNull))))

  private def getCount(selectorObj: JsObject)(implicit ec: ExecutionContext): Future[Long] =
    if (selectorObj != JsObject.empty) {
      collection.count(
        selector = Some(selectorObj),
        limit = None,
        skip = 0,
        hint = None,
        readConcern = ReadConcern.Local
      )
    } else {
      Future(0)
    }

  protected def getMessage(id: String, identifiers: Set[Identifier])(
    implicit ec: ExecutionContext): Future[Either[SecureMessageError, A]] =
    BSONObjectID.parse(id) match {
      case Success(bsonId) =>
        collection
          .find[JsObject, A](
            selector = Json.obj("_id" -> bsonId.asInstanceOf[ID])
              deepMerge
                identifierQuery(identifiers)
          )
          .one[A] map {
          case Some(c) => Right(c)
          case None => {
            logger.debug(identifiers.toString())
            Left(MessageNotFound(s"${typeOf[A].typeSymbol.name} not found for identifiers: $identifiers"))
          }
        }
      case Failure(exception) =>
        Future.successful(Left(InvalidBsonId(s"Invalid BsonId: ${exception.getMessage} ", Some(exception))))
    }

  protected def dbErrorHandler[B]: ErrorHandler[B] = Cursor.FailOnError[B] { (_, error) =>
    logger.error(s"db error: ${error.getMessage}", error)
  }

  protected def identifierQuery(identifiers: Set[Identifier]): JsObject =
    Json.obj(
      "$or" ->
        identifiers.foldLeft(JsArray())((acc, i) => acc ++ Json.arr(Json.obj(findByIdentifierQuery(i): _*)))
    )

  protected def findByIdentifierQuery(identifier: Identifier): Seq[(String, JsValueWrapper)]

  protected def tagQuery(tags: List[FilterTag]): JsObject

}
