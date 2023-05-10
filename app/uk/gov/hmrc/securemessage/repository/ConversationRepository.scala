/*
 * Copyright 2023 HM Revenue & Customs
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
import org.mongodb.scala.MongoWriteException
import org.mongodb.scala.bson.ObjectId
import org.mongodb.scala.model._
import play.api.libs.json.JodaWrites.{ JodaDateTimeWrites => _ }
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.Codecs
import uk.gov.hmrc.securemessage._
import uk.gov.hmrc.securemessage.models.core.{ Filters => _, _ }

import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class ConversationRepository @Inject()(mongo: MongoComponent)(implicit ec: ExecutionContext)
    extends AbstractMessageRepository[Conversation](
      "conversation",
      mongo,
      Conversation.conversationFormat,
      Seq(
        IndexModel(
          Indexes.ascending("client", "id"),
          IndexOptions()
            .name("unique-conversation")
            .unique(true)
            .sparse(true))),
      replaceIndexes = false
    ) {

  private val DuplicateKey = 11000

  def insertIfUnique(conversation: Conversation)(
    implicit ec: ExecutionContext): Future[Either[SecureMessageError, Unit]] =
    collection
      .insertOne(conversation)
      .toFuture()
      .map(_ => Right(()))
      .recoverWith {
        case e: MongoWriteException if e.getError.getCode == DuplicateKey =>
          val errMsg = "Duplicate conversation: " + e.getError.getMessage
          Future.successful(Left(DuplicateConversationError(errMsg, Some(e))))
        case e =>
          val errMsg = s"Database error trying to store conversation ${conversation.id}: " + e.getMessage
          Future.successful(Left(StoreError(errMsg, Some(e))))
      }

  def getConversations(identifiers: Set[Identifier], tags: Option[List[FilterTag]])(
    implicit ec: ExecutionContext): Future[List[Conversation]] = getMessages(identifiers, tags)

  def getConversationsCount(identifiers: Set[Identifier], tags: Option[List[FilterTag]])(
    implicit ec: ExecutionContext): Future[Count] =
    getMessagesCount(identifiers, tags)

  def getConversation(client: String, conversationId: String, identifiers: Set[Identifier])(
    implicit ec: ExecutionContext): Future[Either[MessageNotFound, Conversation]] = {
    val query = identifierQuery(identifiers)
    collection
      .find(
        Filters.and(Filters.equal("id", conversationId), Filters.equal("client", client), query)
      )
      .sort(Filters.equal("_id", -1))
      .first()
      .toFuture()
      .map(Option(_) match {
        case Some(m) => Right(m)
        case None    => Left(MessageNotFound(s"Conversation not found for identifiers: $identifiers"))
      })
      .recoverWith {
        case exception =>
          Future.successful(Left(MessageNotFound(exception.getMessage)))
      }
  }

  def getConversation(id: ObjectId, identifiers: Set[Identifier])(
    implicit ec: ExecutionContext): Future[Either[SecureMessageError, Conversation]] = getMessage(id, identifiers)

  def addMessageToConversation(client: String, conversationId: String, message: ConversationMessage)(
    implicit ec: ExecutionContext): Future[Either[SecureMessageError, Unit]] = {
    val query =
      Filters.and(Filters.equal("client", client), Filters.equal("id", conversationId))
    collection
      .updateOne(query, Updates.addToSet("messages", Codecs.toBson(message)))
      .toFuture()
      .map(_ => Right(()))
      .recover {
        case e =>
          Left(StoreError(s"Message not created for $client and $conversationId. error: ${e.getMessage}", None))
      }
  }

  def addReadTime(client: String, conversationId: String, participantId: Int, readTime: DateTime)(
    implicit ec: ExecutionContext): Future[Either[StoreError, Unit]] = {
    val query = Filters.and(
      Filters.equal("client", client),
      Filters.equal("id", conversationId),
      Filters.equal("participants.id", participantId))
    collection
      .updateOne(
        query,
        Updates.addToSet("participants.$.readTimes", Codecs.toBson(readTime)(Participant.dateFormat)),
        UpdateOptions().upsert(true)
      )
      .toFuture()
      .map(_ => Right(()))
      .recover { case error => Left(StoreError(error.getMessage, None)) }
  }

  override protected def findByIdentifierQuery(identifier: Identifier): Seq[(String, String)] =
    identifier.enrolment match {
      case Some(enrolment) =>
        Seq(
          "participants.identifier.name"      -> identifier.name,
          "participants.identifier.value"     -> identifier.value,
          "participants.identifier.enrolment" -> enrolment
        )
      case None =>
        Seq(
          "participants.identifier.name"  -> identifier.name,
          "participants.identifier.value" -> identifier.value
        )
    }
}
