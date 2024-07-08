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

import org.mongodb.scala.model.ReturnDocument.AFTER
import org.mongodb.scala.model.Updates.set
import org.mongodb.scala.model.{ Filters, FindOneAndUpdateOptions, IndexModel }
import org.mongodb.scala.SingleObservableFuture
import org.mongodb.scala.ObservableFuture
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.securemessage.models.v4.Allowlist

import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class AllowlistRepository @Inject() (mongo: MongoComponent)(implicit executionContext: ExecutionContext)
    extends PlayMongoRepository[Allowlist](
      mongo,
      "brake-gmc-allowlist",
      Allowlist.format,
      Seq.empty[IndexModel]
    ) {
  def store(allowlist: List[String]): Future[Option[Allowlist]] =
    collection
      .findOneAndUpdate(
        Filters.empty(),
        update = set("formIdList", allowlist),
        FindOneAndUpdateOptions().upsert(true).returnDocument(AFTER)
      )
      .toFuture()
      .map(Option(_))

  def retrieve(): Future[Option[Allowlist]] =
    collection.find(Filters.empty()).toFuture().map(_.headOption)
}
