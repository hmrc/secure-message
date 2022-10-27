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

import org.bson.types.ObjectId
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model._
import play.api.libs.json.JodaWrites.{ JodaDateTimeWrites => _ }
import play.api.libs.json.{ JsObject, Json }
import uk.gov.hmrc.common.message.model.Message
import uk.gov.hmrc.domain.TaxIds.TaxIdWithName
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.Codecs
import uk.gov.hmrc.securemessage.models.core.{ Count, FilterTag, Identifier, Letter, MessageFilter }
import uk.gov.hmrc.securemessage.{ SecureMessageError, StoreError }

import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

class MessageRepository @Inject()(mongo: MongoComponent)(implicit ec: ExecutionContext)
    extends SecureMessageRepository[Message](
      "message",
      mongo,
      Message.messageFormat,
      Seq.empty[IndexModel],
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

  def getLetters(identifiers: Set[Identifier], tags: Option[List[FilterTag]])(
    implicit ec: ExecutionContext): Future[List[Message]] =
    getMessages(identifiers, tags)

  def getLettersCount(identifiers: Set[Identifier], tags: Option[List[FilterTag]])(
    implicit ec: ExecutionContext): Future[Count] =
    getMessagesCount(identifiers, tags)

  def getLetter(id: ObjectId, identifiers: Set[Identifier])(
    implicit ec: ExecutionContext): Future[Either[SecureMessageError, Message]] = getMessage(id, identifiers)

  def addReadTime(id: ObjectId)(implicit ec: ExecutionContext): Future[Either[SecureMessageError, Unit]] =
    collection
      .updateOne(
        filter = Filters.and(Filters.equal("_id", id), Filters.exists("readTime", exists = false)),
        update = Updates.set("readTime", Codecs.toBson(Letter.dateTimeNow))
      )
      .toFuture()
      .map(_ => Right(()))
      .recoverWith {
        case error => Future.successful(Left(StoreError(error.getMessage, None)))
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

  def findBy(authTaxIds: Set[TaxIdWithName])(
    implicit messageFilter: MessageFilter,
    ec: ExecutionContext
  ): Future[List[Message]] = {

    def readyForViewingQuery: Bson =
      Filters
        .and(Filters.lte("validFrom", Codecs.toBson(Letter.localDateNow)), Filters.notEqual("verificationBrake", true))

    val querySelector = Filters.and(taxIdRegimeSelector(authTaxIds), readyForViewingQuery)
    collection
      .find(querySelector)
      .sort(Filters.equal("_id", -1))
      .toFuture()
      .map(_.toList)
  }
}

trait MessageSelector {

  def selectByTaxId(taxId: TaxIdWithName): JsObject =
    Json.obj("recipient.identifier.value" -> taxId.value, "recipient.identifier.name" -> taxId.name)

  def taxIdRegimeSelector(authTaxIds: Set[TaxIdWithName])(implicit messageFilter: MessageFilter): Bson = {
    val regimesBson: Bson = messageFilter.regimes
      .map { regime =>
        Filters.eq("recipient.regime", regime)
      }
      .foldLeft(Filters.empty()) { (a, b) =>
        Filters.and(a, b)
      }

    val taxIdNames =
      if (messageFilter.taxIdentifiers.isEmpty && messageFilter.regimes.isEmpty) {
        authTaxIds.map(_.name).toSeq
      } else {
        messageFilter.taxIdentifiers
      }

    authTaxIds
      .map { authTaxId =>
        if (taxIdNames.contains(authTaxId.name)) {
          Filters.and(
            Filters.eq("recipient.identifier.value", authTaxId.value),
            Filters.eq("recipient.identifier.name", authTaxId.name))

        } else {
          Filters.or(
            Filters.and(
              Filters.eq("recipient.identifier.value", authTaxId.value),
              Filters.eq("recipient.identifier.name", authTaxId.name)),
            regimesBson)
        }
      }
      .foldLeft(Filters.empty()) { (a, b) =>
        Filters.or(a, b)
      }
  }
}
