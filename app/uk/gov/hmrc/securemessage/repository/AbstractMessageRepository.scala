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

import cats.implicits.toFoldableOps
import org.bson.codecs.Codec
import org.mongodb.scala.bson.ObjectId
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.{ Filters, IndexModel }
import org.mongodb.scala.SingleObservableFuture
import org.mongodb.scala.ObservableFuture
import play.api.Logger
import play.api.libs.json._
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.securemessage.models.core.{ Conversation, Count, FilterTag, Identifier }
import uk.gov.hmrc.securemessage.{ MessageNotFound, SecureMessageError }

import scala.collection.immutable
import scala.concurrent.{ ExecutionContext, Future }
import scala.reflect.{ ClassTag, classTag }

abstract class AbstractMessageRepository[A: ClassTag](
  collectionName: String,
  mongo: MongoComponent,
  domainFormat: Format[A],
  indexes: immutable.Seq[IndexModel],
  replaceIndexes: Boolean,
  extraCodecs: immutable.Seq[Codec[_]] = immutable.Seq.empty
)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[A](
      mongo,
      collectionName,
      domainFormat,
      indexes,
      replaceIndexes = replaceIndexes,
      extraCodecs = extraCodecs
    ) {

  implicit val format: OFormat[A] = domainFormat.asInstanceOf[OFormat[A]]
  val logger = Logger(getClass)

  protected def messagesQuerySelector(identifiers: Set[Identifier], tags: Option[List[FilterTag]]): Bson =
    (identifiers, tags) match {
      case (identifiers, _) if identifiers.isEmpty => // TODO: move this case to service
        Filters.empty()
      case (identifiers, None) =>
        identifierQuery(identifiers)
      case (identifiers, Some(Nil)) =>
        identifierQuery(identifiers)
      case (identifiers, Some(tags)) =>
        Filters.and(
          identifierQuery(identifiers),
          Filters.or(tagQuery(tags): _*)
        )
      case _ =>
        Filters.empty()
    }

  protected def getMessages(identifiers: Set[Identifier], tags: Option[List[FilterTag]])(implicit
    ec: ExecutionContext
  ): Future[List[A]] = {
    val querySelector = messagesQuerySelector(identifiers, tags)
    if (querySelector != Filters.empty()) {
      collection
        .find(querySelector)
        .sort(Filters.equal("_id", -1))
        .toFuture()
        .map(_.toList)
    } else {
      Future.successful(List.empty[A])
    }
  }

  protected def getMessagesCount(identifiers: Set[Identifier], tags: Option[List[FilterTag]])(implicit
    ec: ExecutionContext
  ): Future[Count] = {
    val querySelector = messagesQuerySelector(identifiers, tags)
    val totalCount: Future[Int] = {
      val querySelector = messagesQuerySelector(identifiers, tags)
      if (querySelector != Filters.empty()) {
        collection
          .countDocuments(querySelector)
          .toFuture()
          .map(_.toInt)
      } else {
        Future.successful(0)
      }
    }
    val unreadCount: Future[Int] = totalCount.flatMap { total =>
      if (total == 0) {
        Future.successful(0)
      } else if (collectionName == "conversation") {
        getConversationsUnreadCount(identifiers, tags)
      } else {
        getMessageUnreadCount(querySelector)
      }
    }

    for {
      total  <- totalCount
      unread <- unreadCount
    } yield Count(total, unread)
  }

  private[repository] def conversationRead(conversation: A, identifier: Set[Identifier]): Int =
    if (conversation.asInstanceOf[Conversation].unreadMessagesFor(identifier).isEmpty) 0 else 1

  private[repository] def getConversationsUnreadCount(identifiers: Set[Identifier], tags: Option[List[FilterTag]])(
    implicit ec: ExecutionContext
  ): Future[Int] =
    getMessages(identifiers, tags).map { conversations =>
      conversations.foldMap(c => conversationRead(c, identifiers))
    }

  private def getMessageUnreadCount(baseQuery: Bson)(implicit ec: ExecutionContext) =
    if (baseQuery != Filters.empty()) {
      collection
        .countDocuments(Filters.and(baseQuery, Filters.exists("readTime", exists = false)))
        .toFuture()
        .map(_.toInt)
    } else {
      Future.successful(0)
    }

  protected def getMessage(id: ObjectId, identifiers: Set[Identifier])(implicit
    ec: ExecutionContext
  ): Future[Either[SecureMessageError, A]] = {
    val query = identifierQuery(identifiers)
    collection
      .find(
        Filters.and(
          Filters.equal("_id", id),
          query
        )
      )
      .first()
      .toFuture()
      .map(Option(_) match {
        case Some(m) => Right(m)
        case None =>
          logger.debug(identifiers.toString())
          Left(MessageNotFound(s"${classTag[A].runtimeClass.getSimpleName} not found for identifiers: $identifiers"))
      })
      .recoverWith { case exception =>
        Future.successful(Left(MessageNotFound(exception.getMessage)))
      }
  }

  protected def identifierQuery(identifiers: Set[Identifier]): Bson = {
    val listOfFilters = identifiers.foldLeft(List.empty[Bson])((l, i) =>
      l :+
        findByIdentifierQuery(i)
          .map(q => Filters.equal(q._1, q._2))
          .fold(Filters.empty())((nameFilter, valueFilter) => Filters.and(nameFilter, valueFilter))
    )
    if (listOfFilters.isEmpty) {
      Filters.empty()
    } else if (listOfFilters.size > 1) {
      Filters.or(listOfFilters: _*)
    } else {
      listOfFilters.head
    }
  }

  protected def findByIdentifierQuery(identifier: Identifier): Seq[(String, String)]

  protected def tagQuery(tags: List[FilterTag]): List[Bson] =
    tags.foldLeft(List.empty[Bson])((l, t) => l :+ Filters.equal(s"tags.${t.key}", t.value))
}
