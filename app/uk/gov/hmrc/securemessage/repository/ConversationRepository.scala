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

import javax.inject.{ Inject, Singleton }
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
import uk.gov.hmrc.securemessage.controllers.models.generic.Tag
import uk.gov.hmrc.securemessage.models.core.Message.dateFormat
import uk.gov.hmrc.securemessage.models.core.{ Conversation, Identifier, Message }
import uk.gov.hmrc.securemessage.{ ConversationNotFound, DuplicateConversationError, SecureMessageError, StoreError }

import scala.collection.Seq
import scala.concurrent.{ ExecutionContext, Future }
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
        key = Seq("client" -> IndexType.Ascending, "conversationId" -> IndexType.Ascending),
        name = Some("unique-conversation"),
        unique = true,
        sparse = true))

  def insertIfUnique(conversation: Conversation)(
    implicit ec: ExecutionContext): Future[Either[SecureMessageError, Unit]] =
    insert(conversation)
      .map(_ => Right(()))
      .recoverWith {
        case e: DatabaseException if e.code.contains(DuplicateKey) =>
          val errMsg = "Ignoring duplicate found on insertion to conversation collection: " + e.getMessage()
          Future.successful(Left(DuplicateConversationError(errMsg, Some(e))))
        case e: DatabaseException =>
          val errMsg = s"Database error trying to store conversation ${conversation.conversationId}: " + e.getMessage()
          Future.successful(Left(StoreError(errMsg, Some(e))))
      }

  def getConversationsFiltered(identifiers: Set[Identifier], tags: Option[List[Tag]])(
    implicit ec: ExecutionContext): Future[List[Conversation]] = {
    import uk.gov.hmrc.securemessage.models.core.Conversation.conversationFormat
    val querySelector = (identifiers, tags) match {
      case (identifiers, None) => identifierQuery(identifiers)
      case (identifiers, Some(tags)) if tags.nonEmpty =>
        Json.obj(
          "$and" -> Json.arr(
            identifierQuery(identifiers),
            tagQuery(tags)
          ))
      case (identifiers, Some(_))                  => identifierQuery(identifiers)
      case (identifiers, _) if identifiers.isEmpty => JsObject.empty
      case (_, _)                                  => JsObject.empty
    }

    collection
      .find[JsObject, Conversation](
        selector = querySelector,
        None
      )
      .sort(Json.obj("_id" -> -1))
      .cursor[Conversation]()
      .collect[List](-1, dbErrorHandler[List[Conversation]])
  }

  @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
  def getConversation(client: String, conversationId: String, identifier: Option[Identifier])(
    implicit ec: ExecutionContext): Future[Either[ConversationNotFound, Conversation]] = {
    val commonQuery = conversationQuery(client, conversationId)
    collection
      .find[JsObject, Conversation](selector = identifier match {
        case Some(i) => commonQuery deepMerge Json.obj(findByIdentifierQuery(i): _*)
        case None    => commonQuery
      }, None)
      .one[Conversation] map {
      case Some(c) => Right(c)
      case None =>
        Left(
          ConversationNotFound(
            s"Conversation not found for client: $client, conversationId: $conversationId, identifier: $identifier"))
    }
  }

  private def identifierQuery(identifiers: Set[Identifier]): JsObject =
    Json.obj(
      "$or" ->
        identifiers.foldLeft(JsArray())((acc, i) => acc ++ Json.arr(Json.obj(findByIdentifierQuery(i): _*)))
    )

  private def findByIdentifierQuery(enrolment: Identifier): Seq[(String, JsValueWrapper)] =
    Seq(
      "participants.identifier.name"      -> JsString(enrolment.name),
      "participants.identifier.value"     -> JsString(enrolment.value),
      "participants.identifier.enrolment" -> JsString(enrolment.enrolment.getOrElse(""))
    )

  private def tagQuery(tags: List[Tag]): JsObject =
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
    Json.obj("client" -> client, "conversationId" -> conversationId)

  def addReadTime(client: String, conversationId: String, id: Int, readTime: DateTime)(
    implicit ec: ExecutionContext): Future[Either[StoreError, Unit]] =
    collection
      .update(ordered = false)
      .one[JsObject, JsObject](
        Json.obj("client" -> client, "conversationId" -> conversationId, "participants.id" -> id),
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
