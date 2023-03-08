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

import com.mongodb.ReadPreference
import com.mongodb.client.model.Indexes.ascending
import com.mongodb.client.model.ReturnDocument
import org.bson.conversions.Bson
import org.bson.types.ObjectId
import org.joda.time.DateTime
import org.mongodb.scala.MongoException
import org.mongodb.scala.model._
import uk.gov.hmrc.common.message.model.{ EmailAlert, TimeSource }
import uk.gov.hmrc.common.message.model.MessagesCount
import uk.gov.hmrc.domain.TaxIds.TaxIdWithName
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.workitem.ProcessingStatus
import uk.gov.hmrc.mongo.workitem.ProcessingStatus.{ Failed, InProgress, ToDo }
import uk.gov.hmrc.securemessage.models.core.Identifier
import uk.gov.hmrc.securemessage.models.core.{ Count, FilterTag, Identifier, MessageFilter }
import uk.gov.hmrc.securemessage.models.v4.{ SecureMessage, SecureMessageMongoFormat }

import java.util.concurrent.TimeUnit
import javax.inject.{ Inject, Named, Singleton }
import scala.concurrent.duration.Duration
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class SecureMessageRepository @Inject()(
  mongo: MongoComponent,
  val timeSource: TimeSource,
  @Named("retryFailedAfter") retryIntervalMillis: Int,
  @Named("retryInProgressAfter") retryInProgressAfter: Int,
  @Named("queryMaxTimeMs") queryMaxTimeMs: Int)(implicit ec: ExecutionContext)
    extends AbstractMessageRepository[SecureMessage](
      "secure-message",
      mongo,
      SecureMessageMongoFormat.mongoMessageFormat,
      Seq(
        IndexModel(
          ascending("hash"),
          IndexOptions().name("unique-messageHash").unique(true)
        )
      ),
      replaceIndexes = false
    ) with MessageSelector {

  private final val DuplicateKey = 11000

  def save(message: SecureMessage): Future[Boolean] =
    collection.insertOne(message).toFuture().map(_.wasAcknowledged()).recoverWith {
      case e: MongoException if e.getCode == DuplicateKey =>
        logger.warn(s"Ignoring duplicate message found on insertion to MessageV4 collection: ${message._id}.")
        Future.successful(false)
    }

  def pullMessageToAlert(): Future[Option[SecureMessage]] = {

    val failedBefore: DateTime = timeSource.now().minusMillis(retryIntervalMillis)
    val startedProcessingBefore: DateTime = DateTime.now().minusMillis(retryInProgressAfter)

    def pull(query: Bson): Future[Option[SecureMessage]] =
      collection
        .findOneAndUpdate(
          query,
          setStatusOperation(ProcessingStatus.InProgress),
          FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
        )
        .toFutureOption()

    val todoQuery = Filters.and(
      Filters.equal("status", ToDo.name),
      Filters.lte("alertFrom", timeSource.today)
    )

    val failedBeforeQuery = Filters.or(
      Filters.and(Filters.equal("status", InProgress.name), Filters.lt("lastUpdated", startedProcessingBefore)),
      Filters.and(Filters.equal("status", Failed.name), Filters.lt("lastUpdated", failedBefore))
    )
    pull(todoQuery).flatMap {
      case None        => pull(failedBeforeQuery)
      case messageToDo => Future.successful(messageToDo)
    }
  }

  private def setStatusOperation(newStatus: ProcessingStatus): Bson =
    Updates.combine(
      Updates.set("status", newStatus.name),
      Updates.set("lastUpdated", timeSource.now)
    )

  def alertCompleted(id: ObjectId, status: ProcessingStatus, alert: EmailAlert): Future[Boolean] =
    collection
      .updateOne(
        Filters.equal("_id", id),
        Updates.combine(
          Updates.set("status", status.name),
          Updates.set("alerts", alert)
        )
      )
      .toFuture()
      .map(_.getModifiedCount == 1)

  def findById(id: ObjectId): Future[Option[SecureMessage]] = {

    val query = Filters.and(Filters.equal("_id", id), readyForViewingQuery)

    collection
      .withReadPreference(ReadPreference.secondaryPreferred)
      .find(query)
      .maxTime(Duration(queryMaxTimeMs.toLong, TimeUnit.MILLISECONDS))
      .headOption()

  }

  protected def findByIdentifierQuery(identifier: Identifier): Seq[(String, String)] = Seq()

  def getSecureMessageCount(identifiers: Set[Identifier], tags: Option[List[FilterTag]])(
    implicit ec: ExecutionContext): Future[Count] =
    getMessagesCount(identifiers, tags)

  def countBy(authTaxIds: Set[TaxIdWithName])(
    implicit messageFilter: MessageFilter
  ): Future[MessagesCount] =
    taxIdRegimeSelector(authTaxIds)
      .map(Filters.and(_, readyForViewingQuery, rescindedExcludedQuery))
      .fold(Future.successful(MessagesCount(0, 0)))(query =>
        for {
          unreadCount <- collection
                        // scalastyle:off null
                          .countDocuments(Filters.and(query, Filters.equal("readTime", null)))
                          // scalastyle:on null
                          .toFuture()
          totalCount <- collection.countDocuments(query).toFuture()
        } yield MessagesCount(totalCount.toInt, unreadCount.toInt))
}
