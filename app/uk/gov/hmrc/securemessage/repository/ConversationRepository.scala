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

import org.joda.time.DateTime
import play.api.libs.json.JodaWrites.{ JodaDateTimeWrites => _ }
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json.{ JsArray, JsObject, JsString, Json }
import reactivemongo.api.indexes.{ Index, IndexType }
import reactivemongo.bson.BSONObjectID
import reactivemongo.core.errors.DatabaseException
import uk.gov.hmrc.mongo.MongoConnector
import uk.gov.hmrc.securemessage._
import uk.gov.hmrc.securemessage.models.core.ConversationMessage.dateTimeFormat
import uk.gov.hmrc.securemessage.models.core.{ Conversation, ConversationMessage, Count, FilterTag, Identifier }
import javax.inject.{ Inject, Singleton }
import scala.collection.Seq
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class ConversationRepository @Inject()(implicit connector: MongoConnector)
    extends SecureMessageRepository[Conversation, BSONObjectID](
      "conversation",
      connector.db,
      Conversation.conversationFormat) {

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

  def getConversations(identifiers: Set[Identifier], tags: Option[List[FilterTag]])(
    implicit ec: ExecutionContext): Future[List[Conversation]] = getMessages(identifiers, tags)

  def getConversationsCount(identifiers: Set[Identifier], tags: Option[List[FilterTag]])(
    implicit ec: ExecutionContext): Future[Count] =
    getMessagesCount(identifiers, tags)

  def getConversation(client: String, conversationId: String, identifiers: Set[Identifier])(
    implicit ec: ExecutionContext): Future[Either[MessageNotFound, Conversation]] =
    collection
      .find[JsObject, Conversation](
        selector = Json.obj("client" -> client, "id" -> conversationId)
          deepMerge identifierQuery(identifiers),
        None)
      .one[Conversation] map {
      case Some(c) => Right(c)
      case None =>
        Left(MessageNotFound(s"Conversation not found for identifier: $identifiers"))
    }

  def getConversation(id: String, identifiers: Set[Identifier])(
    implicit ec: ExecutionContext): Future[Either[SecureMessageError, Conversation]] =
    getMessage(id, identifiers)

  def addMessageToConversation(client: String, conversationId: String, message: ConversationMessage)(
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

  override protected def findByIdentifierQuery(identifier: Identifier): Seq[(String, JsValueWrapper)] =
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

  override protected def tagQuery(tags: List[FilterTag]): JsObject =
    Json.obj(
      "$or" ->
        tags.foldLeft(JsArray())((acc, t) => acc ++ Json.arr(Json.obj(s"tags.${t.key}" -> JsString(t.value))))
    )
}
