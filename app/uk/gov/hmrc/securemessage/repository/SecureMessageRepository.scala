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

import com.mongodb.client.model.Indexes.ascending
import org.mongodb.scala.MongoException
import org.mongodb.scala.model._
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.securemessage.models.core.Identifier
import uk.gov.hmrc.securemessage.models.v4.{ SecureMessage, SecureMessageMongoFormat }

import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class SecureMessageRepository @Inject()(mongo: MongoComponent)(implicit ec: ExecutionContext)
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
    ) {

  private final val DuplicateKey = 11000

  def save(message: SecureMessage): Future[Boolean] =
    collection.insertOne(message).toFuture().map(_.wasAcknowledged()).recoverWith {
      case e: MongoException if e.getCode == DuplicateKey =>
        logger.warn(s"Ignoring duplicate message found on insertion to MessageV4 collection: $message.")
        Future.successful(false)
    }

  protected def findByIdentifierQuery(identifier: Identifier): Seq[(String, String)] = Seq()
}
