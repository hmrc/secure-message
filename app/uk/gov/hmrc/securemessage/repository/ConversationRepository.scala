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

import org.joda.time.DateTime
import play.api.libs.json.JodaWrites.{ JodaDateTimeWrites => _ }
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json.{ JsArray, JsObject, JsString, Json }
import reactivemongo.api.Cursor.ErrorHandler
import reactivemongo.api.indexes.{ Index, IndexType }
import reactivemongo.api.{ Cursor, WriteConcern }
import reactivemongo.bson.BSONObjectID
import reactivemongo.core.errors.DatabaseException
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.mongo.{ MongoConnector, ReactiveRepository }
import uk.gov.hmrc.securemessage.models.core.Message.dateFormat
import uk.gov.hmrc.securemessage.models.core.{ Conversation, FilterTag, Identifier, Message }
import uk.gov.hmrc.securemessage._
import javax.inject.{ Inject, Singleton }
import scala.collection.Seq
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

@Singleton
@SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter", "org.wartremover.warts.Nothing"))
class ConversationRepository @Inject()(implicit connector: MongoConnector)
    extends ReactiveRepository[Conversation, BSONObjectID](
      "conversation",
      connector.db,
      Conversation.conversationFormat,
      ReactiveMongoFormats.objectIdFormats) {

  private val DuplicateKey = 11000

  override def indexes: Seq[Index] =
    Seq(
      Index(
        key = Seq("client" -> IndexType.Ascending, "id" -> IndexType.Ascending),
        name = Some("unique-conversation"),
        unique = true,
        sparse = true))

  def insertIfUnique(conversation: Conversation)(
    implicit ec: ExecutionContext): Future[Either[SecureMessageError, Unit]] =
    insert(conversation)
      .map(_ => Right(()))
      .recoverWith {
        case e: DatabaseException if e.code.contains(DuplicateKey) =>
          val errMsg = "Duplicate conversation: " + e.getMessage()
          Future.successful(Left(DuplicateConversationError(errMsg, Some(e))))
        case e: DatabaseException =>
          val errMsg = s"Database error trying to store conversation ${conversation.id}: " + e.getMessage()
          Future.successful(Left(StoreError(errMsg, Some(e))))
      }

  def getConversationsFiltered(identifiers: Set[Identifier], tags: Option[List[FilterTag]])(
    implicit ec: ExecutionContext): Future[List[Conversation]] = {
    import uk.gov.hmrc.securemessage.models.core.Conversation.conversationFormat
    val querySelector = (identifiers, tags) match {
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
    if (querySelector != JsObject.empty) {
      collection
        .find[JsObject, Conversation](
          selector = querySelector,
          None
        )
        .sort(Json.obj("_id" -> -1))
        .cursor[Conversation]()
        .collect[List](-1, dbErrorHandler[List[Conversation]])
    } else {
      Future(List())
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
  def getConversation(client: String, conversationId: String, identifiers: Set[Identifier])(
    implicit ec: ExecutionContext): Future[Either[ConversationNotFound, Conversation]] =
    collection
      .find[JsObject, Conversation](
        selector = Json.obj("client" -> client, "id" -> conversationId)
          deepMerge identifierQuery(identifiers),
        None)
      .one[Conversation] map {
      case Some(c) => Right(c)
      case None =>
        Left(ConversationNotFound(s"Conversation not found for identifier: $identifiers"))
    }

  def getConversation(id: String, identifiers: Set[Identifier])(
    implicit ec: ExecutionContext): Future[Either[SecureMessageError, Conversation]] =
    BSONObjectID.parse(id) match {
      case Success(bsonId) =>
        collection
          .find[JsObject, Conversation](
            selector = Json.obj("_id" -> bsonId)
              deepMerge identifierQuery(identifiers),
            None)
          .one[Conversation] map {
          case Some(c) => Right(c)
          case None =>
            Left(ConversationNotFound(s"Conversation not found for identifier: $identifiers"))
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
          "participants.identifier.name"      -> JsString(identifier.name),
          "participants.identifier.value"     -> JsString(identifier.value),
          "participants.identifier.enrolment" -> JsString(enrolment)
        )
      case None =>
        Seq(
          "participants.identifier.name"  -> JsString(identifier.name),
          "participants.identifier.value" -> JsString(identifier.value)
        )
    }

  private def tagQuery(tags: List[FilterTag]): JsObject =
    Json.obj(
      "$or" ->
        tags.foldLeft(JsArray())((acc, t) => acc ++ Json.arr(Json.obj(s"tags.${t.key}" -> JsString(t.value))))
    )

  def addMessageToConversation(client: String, conversationId: String, message: Message)(
    implicit ec: ExecutionContext): Future[Either[SecureMessageError, Unit]] =
    collection
      .update(ordered = false)
      .one[JsObject, JsObject](
        conversationQuery(client, conversationId),
        Json.obj("$push" -> Json.obj("messages" -> message))
      )
      .map(_.errmsg match {
        case Some(errMsg) =>
          Left(StoreError(s"Message not created for $client and $conversationId. error: $errMsg", None))
        case _ => Right(())
      })

  private def conversationQuery(client: String, conversationId: String): JsObject =
    Json.obj("client" -> client, "id" -> conversationId)

  def addReadTime(client: String, conversationId: String, participantId: Int, readTime: DateTime)(
    implicit ec: ExecutionContext): Future[Either[StoreError, Unit]] =
    collection
      .update(ordered = false)
      .one[JsObject, JsObject](
        Json.obj("client" -> client, "id" -> conversationId, "participants.id" -> participantId),
        Json.obj("$push"  -> Json.obj("participants.$.readTimes" -> readTime))
      )
      .map(_.errmsg match {
        case Some(errMsg) => Left(StoreError(errMsg, None))
        case None         => Right(())
      })

  def deleteConversationForTestOnly(conversationId: String, client: String)(
    implicit ec: ExecutionContext): Future[Unit] =
    collection
      .findAndRemove[JsObject](
        selector = conversationQuery(client, conversationId),
        None,
        None,
        WriteConcern.Default,
        None,
        None,
        Nil)
      .map(_ => ())

  //TODO: replace this with returning the error as an Either.Left upstream.
  private def dbErrorHandler[A]: ErrorHandler[A] = Cursor.FailOnError[A] { (_, error) =>
    logger.error(s"db error: ${error.getMessage}", error)
  }

}
