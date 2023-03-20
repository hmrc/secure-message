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
import org.bson.types.ObjectId
import org.joda.time.{ DateTime, LocalDate }
import org.mongodb.scala.MongoException
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model._
import play.api.libs.json.Json
import uk.gov.hmrc.common.message.model.{ EmailAlert, MessagesCount, TimeSource }
import uk.gov.hmrc.domain.TaxIds.TaxIdWithName
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.Codecs
import uk.gov.hmrc.mongo.workitem.ProcessingStatus
import uk.gov.hmrc.mongo.workitem.ProcessingStatus.{ Failed, InProgress, ToDo }
import uk.gov.hmrc.securemessage.{ SecureMessageError, StoreError }
import uk.gov.hmrc.securemessage.models.core.{ Count, FilterTag, Identifier, MessageFilter }
import uk.gov.hmrc.securemessage.models.v4.{ SecureMessage, SecureMessageMongoFormat }
import uk.gov.hmrc.securemessage.models.v4.SecureMessageMongoFormat._
import java.util.concurrent.TimeUnit
import javax.inject.{ Inject, Named, Singleton }
import scala.concurrent.duration.Duration
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.control.NonFatal

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
        ),
        IndexModel(
          ascending("recipient.identifier.value", "recipient.identifier.name"),
          IndexOptions()
            .name("recipient-tax-id")
            .unique(false)
            .background(true))
      ),
      replaceIndexes = false,
      extraCodecs = Seq(
        Codecs.playFormatCodec(uk.gov.hmrc.mongo.play.json.formats.MongoJodaFormats.localDateFormat),
        Codecs.playFormatCodec(SecureMessageMongoFormat.localDateFormat),
        Codecs.playFormatCodec(uk.gov.hmrc.mongo.play.json.formats.MongoJodaFormats.dateTimeFormat),
        Codecs.playFormatCodec(uk.gov.hmrc.common.message.model.EmailAlert.alertFormat),
        Codecs.playFormatCodec(uk.gov.hmrc.common.message.model.Regime.format)
      )
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
      Filters.lte("validFrom", timeSource.today)
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

  protected def findByIdentifierQuery(identifier: Identifier): Seq[(String, String)] =
    identifier.enrolment match {
      case Some(enrolment) =>
        Seq[(String, String)](
          "recipient.identifier.name"  -> enrolment,
          "recipient.identifier.value" -> identifier.value
        )
      case None =>
        Seq[(String, String)](
          "recipient.identifier.name"  -> "Unknown",
          "recipient.identifier.value" -> identifier.value
        )
    }

  def getSecureMessageCount(identifiers: Set[Identifier], tags: Option[List[FilterTag]])(
    implicit ec: ExecutionContext): Future[Count] =
    getMessagesCount(identifiers, tags)

  override def readyForViewingQuery: Bson = {
    import SecureMessageMongoFormat.localDateFormat
    Filters.and(
      Filters.lte("validFrom", Codecs.toBson(LocalDate.now())),
      Filters.nor(Filters.equal("verificationBrake", true)))
  }

  def findBy(authTaxIds: Set[TaxIdWithName])(
    implicit messageFilter: MessageFilter,
    ec: ExecutionContext
  ): Future[List[SecureMessage]] =
    taxIdRegimeSelector(authTaxIds)
      .map(Filters.and(_, readyForViewingQuery))
      .fold(Future.successful(List[SecureMessage]())) { query =>
        collection
          .find(query)
          .maxTime(Duration(1, TimeUnit.MINUTES))
          .sort(Filters.equal("_id", -1))
          .toFuture()
          .map(_.toList)
      }
      .recoverWith {
        case NonFatal(e) =>
          logger.error(s"Error processing the query ${e.getMessage}")
          Future.successful(List())
      }

  def countBy(authTaxIds: Set[TaxIdWithName])(
    implicit messageFilter: MessageFilter,
    ec: ExecutionContext
  ): Future[MessagesCount] =
    taxIdRegimeSelector(authTaxIds)
      .map(Filters.and(_, readyForViewingQuery))
      .fold(Future.successful(MessagesCount(0, 0)))(query =>
        for {
          unreadCount <- collection
                        // scalastyle:off null
                          .countDocuments(Filters.and(query, Filters.equal("readTime", null)))
                          // scalastyle:on null
                          .toFuture()
          totalCount <- collection.countDocuments(query).toFuture()
        } yield MessagesCount(totalCount.toInt, unreadCount.toInt))

  def getSecureMessages(identifiers: Set[Identifier], tags: Option[List[FilterTag]])(
    implicit ec: ExecutionContext): Future[List[SecureMessage]] =
    getMessages(identifiers, tags)

  def getSecureMessage(id: ObjectId, identifiers: Set[Identifier])(
    implicit ec: ExecutionContext): Future[Either[SecureMessageError, SecureMessage]] = getMessage(id, identifiers)

  import SecureMessageMongoFormat.dateTimeFormat
  def addReadTime(id: ObjectId, readTime: DateTime)(
    implicit ec: ExecutionContext): Future[Either[SecureMessageError, Unit]] =
    collection
      .updateOne(
        filter = Filters.and(Filters.equal("_id", id), Filters.exists("readTime", exists = false)),
        update = Updates.set("readTime", Codecs.toBson(Json.toJson(readTime)))
      )
      .toFuture()
      .map(_ => Right(()))
      .recoverWith {
        case error => Future.successful(Left(StoreError(error.getMessage, None)))
      }
}
