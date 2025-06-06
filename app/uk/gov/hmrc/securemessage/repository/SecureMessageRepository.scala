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
import java.time.{ Instant, LocalDate }
import org.mongodb.scala.MongoException
import org.mongodb.scala.bson.{ BsonArray, BsonDocument }
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Aggregates.{ `match`, sample }
import org.mongodb.scala.model.Updates.set
import org.mongodb.scala.model._
import org.mongodb.scala.SingleObservableFuture
import org.mongodb.scala.ObservableFuture
import play.api.libs.json.Json
import uk.gov.hmrc.common.message.model.{ EmailAlert, MessagesCount, TimeSource }
import uk.gov.hmrc.domain.TaxIds.TaxIdWithName
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.Codecs
import uk.gov.hmrc.mongo.workitem.ProcessingStatus
import uk.gov.hmrc.mongo.workitem.ProcessingStatus.{ Cancelled, Deferred, Failed, InProgress, ToDo }
import uk.gov.hmrc.securemessage.{ SecureMessageError, StoreError }
import uk.gov.hmrc.securemessage.models.core.{ FilterTag, Identifier, MessageFilter }
import uk.gov.hmrc.securemessage.models.v4.{ BrakeBatch, BrakeBatchApproval, BrakeBatchDetails, BrakeBatchMessage, SecureMessage, SecureMessageMongoFormat }
import uk.gov.hmrc.securemessage.models.v4.SecureMessageMongoFormat._

import java.util.concurrent.TimeUnit
import javax.inject.{ Inject, Named, Singleton }
import scala.concurrent.duration.Duration
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.control.NonFatal

@Singleton
class SecureMessageRepository @Inject() (
  mongo: MongoComponent,
  val timeSource: TimeSource,
  @Named("retryFailedAfter") retryIntervalMillis: Int,
  @Named("retryInProgressAfter") retryInProgressAfter: Int,
  @Named("queryMaxTimeMs") queryMaxTimeMs: Int
)(implicit ec: ExecutionContext)
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
          ascending("externalRef.id", "externalRef.source"),
          IndexOptions().name("unique-externalRef").unique(true).sparse(true).background(true)
        ),
        IndexModel(
          ascending("recipient.identifier.value", "recipient.identifier.name"),
          IndexOptions()
            .name("recipient-tax-id")
            .unique(false)
            .background(true)
        ),
        IndexModel(ascending("status"), IndexOptions().name("status").unique(false))
      ),
      replaceIndexes = true,
      extraCodecs = Seq(
        Codecs.playFormatCodec(uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats.localDateFormat),
        Codecs.playFormatCodec(SecureMessageMongoFormat.localDateFormat),
        Codecs.playFormatCodec(uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats.instantFormat),
        Codecs.playFormatCodec(uk.gov.hmrc.common.message.model.EmailAlert.alertFormat),
        Codecs.playFormatCodec(uk.gov.hmrc.common.message.model.Regime.format),
        Codecs.playFormatCodec(BrakeBatchDetails.brakeBatchDetailsFormat)
      )
    ) with MessageSelector {

  private final val DuplicateKey = 11000

  def save(message: SecureMessage): Future[Boolean] =
    collection.insertOne(message).toFuture().map(_.wasAcknowledged()).recoverWith {
      case e: MongoException if e.getCode == DuplicateKey =>
        logger.warn(s"Ignoring duplicate message found on insertion to MessageV4 collection: ${message._id}.")
        Future.successful(false)
    }

  def count(): Future[Map[String, Int]] =
    Future
      .sequence(ProcessingStatus.values.map { status =>
        mongo.database
          .getCollection[BsonDocument]("secure-message")
          .countDocuments(
            Filters.and(Filters.equal("status", status.name), Filters.gte("lastUpdated", timeSource.today()))
          )
          .toFuture()
          .map(_.toInt)
          .map(count => Map(s"message.${status.name}" -> count))
      })
      .map(_.fold(Map.empty)(_ ++ _))

  def pullMessageToAlert(): Future[Option[SecureMessage]] = {

    val failedBefore: Instant = timeSource.now().minusMillis(retryIntervalMillis.toLong)
    val startedProcessingBefore: Instant = Instant.now().minusMillis(retryInProgressAfter.toLong)

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
      Filters.lte("validFrom", timeSource.today())
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
      Updates.set("lastUpdated", timeSource.now())
    )

  def updateStatus(id: ObjectId, status: ProcessingStatus): Future[Boolean] =
    collection
      .updateOne(
        Filters.equal("_id", id),
        Updates.combine(
          Updates.set("status", status.name),
          Updates.set("lastUpdated", timeSource.now())
        )
      )
      .toFuture()
      .map(_.getModifiedCount == 1)

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

    val query = Filters.and(Filters.equal("_id", id), readyForViewingQuery())

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

  def getSecureMessageCount(identifiers: Set[Identifier], tags: Option[List[FilterTag]])(implicit
    ec: ExecutionContext
  ): Future[MessagesCount] =
    getMessagesCount(identifiers, tags)

  override def readyForViewingQuery(): Bson = {
    import SecureMessageMongoFormat.localDateFormat
    Filters.and(
      Filters.lte("validFrom", Codecs.toBson(LocalDate.now())),
      Filters.nor(Filters.equal("verificationBrake", true))
    )
  }

  def findBy(authTaxIds: Set[TaxIdWithName])(implicit
    messageFilter: MessageFilter,
    ec: ExecutionContext
  ): Future[List[SecureMessage]] =
    taxIdRegimeSelector(authTaxIds)
      .map(Filters.and(_, readyForViewingQuery()))
      .fold(Future.successful(List[SecureMessage]())) { query =>
        logger.debug(s"SecureMessageQuery $query")
        collection
          .find(query)
          .maxTime(Duration(1, TimeUnit.MINUTES))
          .sort(Filters.equal("_id", -1))
          .toFuture()
          .map(_.toList)
      }
      .recoverWith { case NonFatal(e) =>
        logger.error(s"Error processing the query ${e.getMessage}")
        Future.successful(List())
      }

  def countBy(authTaxIds: Set[TaxIdWithName])(implicit
    messageFilter: MessageFilter,
    ec: ExecutionContext
  ): Future[MessagesCount] =
    taxIdRegimeSelector(authTaxIds)
      .map(Filters.and(_, readyForViewingQuery()))
      .fold(Future.successful(MessagesCount(0, 0))) { query =>
        logger.debug(s"SecureMessageCountQuery $query")
        for {
          unreadCount <- collection
                           // scalastyle:off null
                           .countDocuments(Filters.and(query, Filters.equal("readTime", null)))
                           // scalastyle:on null
                           .toFuture()
          totalCount <- collection.countDocuments(query).toFuture()
        } yield MessagesCount(totalCount.toInt, unreadCount.toInt)
      }

  def getSecureMessages(identifiers: Set[Identifier], tags: Option[List[FilterTag]])(implicit
    ec: ExecutionContext
  ): Future[List[SecureMessage]] =
    getMessages(identifiers, tags)

  def getSecureMessage(id: ObjectId, identifiers: Set[Identifier])(implicit
    ec: ExecutionContext
  ): Future[Either[SecureMessageError, SecureMessage]] = getMessage(id, identifiers)

  import SecureMessageMongoFormat.dateTimeFormat
  def addReadTime(id: ObjectId, readTime: Instant)(implicit
    ec: ExecutionContext
  ): Future[Either[SecureMessageError, SecureMessage]] =
    collection
      .findOneAndUpdate(
        filter = Filters.and(Filters.equal("_id", id), Filters.exists("readTime", exists = false)),
        update = Updates.set("readTime", Codecs.toBson(Json.toJson(readTime)))
      )
      .toFuture()
      .map(m => Right(m))
      .recoverWith { case error =>
        Future.successful(Left(StoreError(error.getMessage, None)))
      }

  def pullBrakeBatchDetails(): Future[List[BrakeBatchDetails]] = {
    import org.mongodb.scala.model.Aggregates._
    collection
      .aggregate[BrakeBatchDetails](
        List(
          `match`(Filters.and(Filters.equal("status", Deferred.name), Filters.equal("verificationBrake", true))),
          project(
            Projections.fields(
              Filters.equal("formId", BsonDocument(f"$$ifNull" -> BsonArray(f"$$details.formId", "Unspecified"))),
              Filters
                .equal(
                  "issueDate",
                  BsonDocument(f"$$ifNull" -> BsonArray(f"$$details.issueDate", Codecs.toBson(Instant.EPOCH)))
                ),
              Filters.equal("batchId", BsonDocument(f"$$ifNull" -> BsonArray(f"$$details.batchId", "Unspecified"))),
              Filters.equal("templateId", f"$$alertDetails.templateId")
            )
          ),
          group(
            BsonDocument(
              "batchId"    -> f"$$batchId",
              "formId"     -> f"$$formId",
              "issueDate"  -> f"$$issueDate",
              "templateId" -> f"$$templateId"
            ),
            Accumulators.first("formId", f"$$formId"),
            Accumulators.first("batchId", f"$$batchId"),
            Accumulators.first("issueDate", f"$$issueDate"),
            Accumulators.first("templateId", f"$$templateId"),
            Accumulators.sum("count", 1)
          )
        )
      )
      .collect()
      .toFuture()
      .map(_.toList)
  }

  def brakeBatchAccepted(brakeBatchApproval: BrakeBatchApproval): Future[Boolean] =
    collection
      .updateMany(
        Filters.and(
          Filters.equal("status", Deferred.name),
          Filters.equal(
            "details.batchId",
            if (brakeBatchApproval.batchId.equals("Unspecified")) null else brakeBatchApproval.batchId
          ),
          Filters.equal(
            "details.formId",
            if (brakeBatchApproval.formId.equals("Unspecified")) null else brakeBatchApproval.formId
          ),
          Filters.equal("alertDetails.templateId", brakeBatchApproval.templateId),
          Filters.equal(
            "details.issueDate",
            if (brakeBatchApproval.issueDate.equals(Instant.EPOCH)) {
              null
            } else {
              brakeBatchApproval.issueDate.toString
            }
          )
        ),
        Updates.combine(Updates.set("status", ToDo.name), Updates.set("verificationBrake", false))
      )
      .toFuture()
      .map(_.getModifiedCount >= 1)

  def brakeBatchRejected(brakeBatchApproval: BrakeBatchApproval): Future[Boolean] =
    collection
      .updateMany(
        Filters.and(
          Filters.equal("status", Deferred.name),
          Filters.equal(
            "details.batchId",
            if (brakeBatchApproval.batchId.equals("Unspecified")) null else brakeBatchApproval.batchId
          ),
          Filters.equal(
            "details.formId",
            if (brakeBatchApproval.formId.equals("Unspecified")) null else brakeBatchApproval.formId
          ),
          Filters.equal("alertDetails.templateId", brakeBatchApproval.templateId),
          Filters.equal(
            "details.issueDate",
            if (brakeBatchApproval.issueDate.equals(Instant.EPOCH)) {
              null
            } else {
              brakeBatchApproval.issueDate.toString
            }
          )
        ),
        set("status", Cancelled.name)
      )
      .toFuture()
      .map(_.getModifiedCount >= 1)

  def brakeBatchMessageRandom(
    brakeBatch: BrakeBatch
  ): Future[Option[BrakeBatchMessage]] =
    collection
      .aggregate[SecureMessage](
        List(
          `match`(
            Filters.and(
              Filters.equal("status", Deferred.name),
              Filters
                .equal("details.batchId", if (brakeBatch.batchId.equals("Unspecified")) null else brakeBatch.batchId),
              Filters.equal("details.formId", if (brakeBatch.formId.equals("Unspecified")) null else brakeBatch.formId),
              Filters.equal("alertDetails.templateId", brakeBatch.templateId),
              Filters.equal(
                "details.issueDate",
                if (brakeBatch.issueDate.equals(Instant.EPOCH)) null else brakeBatch.issueDate.toString
              )
            )
          ),
          sample(1)
        )
      )
      .collect()
      .toFuture()
      .map(_.headOption.map(x => BrakeBatchMessage(x)))
}
