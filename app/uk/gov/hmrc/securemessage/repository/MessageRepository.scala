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

import com.mongodb.client.model.Indexes.{ ascending, descending }
import org.bson.types.ObjectId
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.*
import org.mongodb.scala.SingleObservableFuture
import org.mongodb.scala.ObservableFuture
import org.mongodb.scala.bson.BsonDocument
import play.api.libs.json.{ JsObject, Json }
import uk.gov.hmrc.common.message.model.{ MessagesCount, Regime, TimeSource }
import uk.gov.hmrc.domain.TaxIds.TaxIdWithName
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.Codecs
import uk.gov.hmrc.mongo.workitem.ProcessingStatus
import uk.gov.hmrc.securemessage.models.core.{ FilterTag, Identifier, Letter, MessageFilter, RenderUrl }
import uk.gov.hmrc.securemessage.{ SecureMessageError, StoreError }

import java.util.concurrent.TimeUnit
import javax.inject.Inject
import scala.concurrent.duration.Duration
import scala.concurrent.{ ExecutionContext, Future }

class MessageRepository @Inject() (mongo: MongoComponent, val timeSource: TimeSource)(implicit ec: ExecutionContext)
    extends AbstractMessageRepository[Letter](
      "message",
      mongo,
      Letter.letterFormat,
      Seq(
        IndexModel(
          ascending("hash"),
          IndexOptions().name("unique-messageHash").unique(true)
        ),
        IndexModel(
          ascending("externalRef.id", "externalRef.source"),
          IndexOptions()
            .name("unique-externalRef")
            .unique(true)
            .sparse(true)
            .background(true)
        ),
        IndexModel(ascending("alertFrom"), IndexOptions().unique(false)),
        IndexModel(
          ascending("status", "alertFrom"),
          IndexOptions().name("status-alertFrom-v1").unique(false).background(true)
        ),
        IndexModel(ascending("status"), IndexOptions().name("status").unique(false)),
        IndexModel(
          ascending("recipient.identifier.value", "recipient.identifier.name"),
          IndexOptions()
            .name("recipient-tax-id-v2")
            .unique(false)
            .background(true)
        ),
        IndexModel(
          ascending("recipient.regime"),
          IndexOptions().name("recipient-regime-id-v2").unique(false).background(true)
        ),
        IndexModel(ascending("body.threadId"), IndexOptions().unique(false).background(true)),
        IndexModel(ascending("body.envelopId"), IndexOptions().unique(false).background(true)),
        IndexModel(
          descending("status", "lastUpdated"),
          IndexOptions()
            .name("status-lastupdated-id-v1")
            .unique(false)
            .background(true)
        ),
        IndexModel(
          descending("validFrom", "verificationBrake"),
          IndexOptions()
            .name("validFrom-verificationBrake-v1")
            .unique(false)
            .background(true)
        ),
        IndexModel(
          descending("rescindment.ref"),
          IndexOptions()
            .name("rescindment-ref-v1")
            .unique(false)
            .background(true)
        )
      ),
      replaceIndexes = false
    ) with MessageSelector {

  override protected def messagesQuerySelector(identifiers: Set[Identifier], tags: Option[List[FilterTag]]): Bson = {
    val superQuery = super.messagesQuerySelector(identifiers, tags)
    if (superQuery == Filters.empty()) {
      superQuery
    } else {
      Filters.and(superQuery, Filters.lte("validFrom", Codecs.toBson(Letter.localDateNow)))
    }
  }

  def count(): Future[Map[String, Int]] =
    Future
      .sequence(ProcessingStatus.values.map { status =>
        mongo.database
          .getCollection[BsonDocument]("message")
          .countDocuments(
            Filters.and(Filters.equal("status", status.name), Filters.gte("lastUpdated", timeSource.today()))
          )
          .toFuture()
          .map(_.toInt)
          .map(count => Map(s"message.${status.name}" -> count))
      })
      .map(_.fold(Map.empty)(_ ++ _))

  def getLetters(identifiers: Set[Identifier], tags: Option[List[FilterTag]])(implicit
    ec: ExecutionContext
  ): Future[List[Letter]] =
    getMessages(identifiers, tags)

  def getLettersCount(identifiers: Set[Identifier], tags: Option[List[FilterTag]])(implicit
    ec: ExecutionContext
  ): Future[MessagesCount] =
    getMessagesCount(identifiers, tags)

  def getLetter(id: ObjectId, identifiers: Set[Identifier])(implicit
    ec: ExecutionContext
  ): Future[Either[SecureMessageError, Letter]] = getMessage(id, identifiers).map(updateRenderUrl)

  private val updateRenderUrl: Either[SecureMessageError, Letter] => Either[SecureMessageError, Letter] = {
    case Right(l) if List("ats-message-renderer", "sa-message-renderer").contains(l.renderUrl.service) =>
      Right(l.copy(renderUrl = RenderUrl("secure-message", s"/secure-messaging${l.renderUrl.url}")))
    case Right(l) if l.renderUrl.service == "two-way-message" =>
      Right(l.copy(renderUrl = RenderUrl("secure-message", s"/secure-messaging/two-way-message${l.renderUrl.url}")))
    case other => other
  }

  def addReadTime(id: ObjectId)(implicit ec: ExecutionContext): Future[Either[SecureMessageError, Letter]] =
    collection
      .findOneAndUpdate(
        filter = Filters.and(Filters.equal("_id", id), Filters.exists("readTime", exists = false)),
        update = Updates.set("readTime", Codecs.toBson(Letter.dateTimeNow))
      )
      .toFuture()
      .map(m => Right(m))
      .recoverWith { case error =>
        Future.successful(Left(StoreError(error.getMessage, None)))
      }

  override protected def findByIdentifierQuery(identifier: Identifier): Seq[(String, String)] =
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

  def findBy(authTaxIds: Set[TaxIdWithName])(implicit
    messageFilter: MessageFilter,
    ec: ExecutionContext
  ): Future[List[Letter]] =
    taxIdRegimeSelector(authTaxIds)
      .map(Filters.and(_, readyForViewingQuery(), rescindedExcludedQuery))
      .fold(Future.successful(List[Letter]())) { query =>
        collection
          .find(query)
          .maxTime(Duration(1, TimeUnit.MINUTES))
          .sort(Filters.equal("_id", -1))
          .toFuture()
          .map(_.toList)
      }
      .recoverWith {
        case e: Exception =>
          logger.error("Error processing the query", e)
          Future.successful(List())
        case anyOtherError =>
          logger.warn(s"Error processing the query  $anyOtherError")
          Future.successful(List())
      }

  def countBy(authTaxIds: Set[TaxIdWithName])(implicit
    messageFilter: MessageFilter
  ): Future[MessagesCount] =
    taxIdRegimeSelector(authTaxIds)
      .map(Filters.and(_, readyForViewingQuery(), rescindedExcludedQuery))
      .fold(Future.successful(MessagesCount(0, 0)))(query =>
        for {
          unreadCount <- collection
                           // scalastyle:off null
                           .countDocuments(Filters.and(query, Filters.equal("readTime", null)))
                           // scalastyle:on null
                           .toFuture()
          totalCount <- collection.countDocuments(query).toFuture()
        } yield MessagesCount(totalCount.toInt, unreadCount.toInt)
      )

  override def readyForViewingQuery(): Bson =
    Filters
      .and(Filters.lte("validFrom", Codecs.toBson(Letter.localDateNow)), Filters.notEqual("verificationBrake", true))
}

trait MessageSelector {

  def selectByTaxId(taxId: TaxIdWithName): JsObject =
    Json.obj("recipient.identifier.value" -> taxId.value, "recipient.identifier.name" -> taxId.name)

  def rescindedExcludedQuery: Bson = Filters.exists("rescindment", exists = false)

  def taxIdRegimeSelector(
    authTaxIds: Set[TaxIdWithName]
  )(implicit messageFilter: MessageFilter): Option[Bson] = {

    val regimesJsonArr: Option[Seq[Bson]] = Option(messageFilter.regimes)
      .filter(_.nonEmpty)
      .map(
        _.map((regime: Regime.Value) => Filters.equal("recipient.regime", Codecs.toBson(regime)))
          .foldLeft(Seq.empty[Bson])((acc, e) => acc.+:(e))
      )

    val taxIdNames: Seq[String] =
      if (messageFilter.taxIdentifiers.isEmpty && messageFilter.regimes.isEmpty) {
        authTaxIds.map(_.name).toSeq
      } else {
        messageFilter.taxIdentifiers
      }

    authTaxIds
      .flatMap(authTaxId =>
        if (taxIdNames.contains(authTaxId.name)) {
          Seq(
            Filters.and(
              Filters.equal("recipient.identifier.name", authTaxId.name),
              Filters.equal("recipient.identifier.value", authTaxId.value)
            )
          )
        } else {
          regimesJsonArr.fold(Seq[Bson]())(ar =>
            Seq[Bson](
              Filters.and(
                Filters.equal("recipient.identifier.value", authTaxId.value),
                Filters.equal("recipient.identifier.name", authTaxId.name),
                Filters.or(ar: _*)
              )
            )
          )
        }
      )
      .foldLeft[Option[List[Bson]]](None) {
        case (None, e)    => Some(List(e))
        case (Some(a), e) => Some(a.+:(e))
      }
      .map(x => Filters.or(x: _*))

  }
}
