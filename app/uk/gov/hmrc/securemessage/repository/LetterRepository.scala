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
import play.api.libs.json.OFormat
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.Codecs
import uk.gov.hmrc.securemessage.models.core.{ Count, FilterTag, Identifier, Letter }
import uk.gov.hmrc.securemessage.{ SecureMessageError, StoreError }

import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

class LetterRepository @Inject()(mongo: MongoComponent)(implicit ec: ExecutionContext)
    extends SecureMessageRepository[Letter](
      "message",
      mongo,
      Letter.letterFormat,
      Seq.empty[IndexModel],
      replaceIndexes = false
    ) {

  implicit val format: OFormat[Letter] = domainFormat.asInstanceOf[OFormat[Letter]]

  override protected def messagesQuerySelector(identifiers: Set[Identifier], tags: Option[List[FilterTag]]): Bson = {
    val superQuery = super.messagesQuerySelector(identifiers, tags)
    if (superQuery == Filters.empty()) {
      superQuery
    } else {
      Filters.and(superQuery, Filters.lte("validFrom", Codecs.toBson(Letter.localDateNow)))
    }
  }

  def getLetters(identifiers: Set[Identifier], tags: Option[List[FilterTag]])(
    implicit ec: ExecutionContext): Future[List[Letter]] =
    getMessages(identifiers, tags)

  def getLettersCount(identifiers: Set[Identifier], tags: Option[List[FilterTag]])(
    implicit ec: ExecutionContext): Future[Count] =
    getMessagesCount(identifiers, tags)

  def getLetter(id: ObjectId, identifiers: Set[Identifier])(
    implicit ec: ExecutionContext): Future[Either[SecureMessageError, Letter]] = getMessage(id, identifiers)

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

}
