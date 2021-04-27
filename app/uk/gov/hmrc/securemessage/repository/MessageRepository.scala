/*
 * Copyright 2021 HM Revenue & Customs
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

import play.api.libs.json.{ JsObject, Json }
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.mongo.{ MongoConnector, ReactiveRepository }
import uk.gov.hmrc.securemessage.{ LetterNotFound }
import uk.gov.hmrc.securemessage.models.core.Letter

import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

class MessageRepository @Inject()(implicit connector: MongoConnector)
    extends ReactiveRepository[Letter, BSONObjectID](
      "message",
      connector.db,
      Letter.letterFormat
    ) {

  def getLetter(id: BSONObjectID)(implicit ec: ExecutionContext): Future[Either[LetterNotFound, Letter]] =
    collection
      .find[JsObject, Letter](
        selector = Json.obj("_id" -> id)
      )
      .one[Letter] map {
      case Some(c) => Right(c)
      case None =>
        Left(LetterNotFound(s"Letter not found"))
    }
}
