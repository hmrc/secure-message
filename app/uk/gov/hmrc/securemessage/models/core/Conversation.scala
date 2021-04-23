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

package uk.gov.hmrc.securemessage.models.core

import cats.data.NonEmptyList
import play.api.libs.json.{ JsError, JsSuccess, JsValue, Json, OFormat, Reads, Writes, __ }
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.securemessage.models.utils.NonEmptyListOps._

import scala.util.{ Failure, Success }

final case class Conversation(
  _id: Option[BSONObjectID] = None,
  client: String,
  id: String,
  status: ConversationStatus,
  tags: Option[Map[String, String]],
  subject: String,
  language: Language,
  participants: List[Participant],
  messages: NonEmptyList[Message],
  alert: Alert
)

object Conversation {

  implicit val bsonObjectIdWrites: Writes[BSONObjectID] = new Writes[BSONObjectID] {
    def writes(bsonObjectId: BSONObjectID): JsValue = Json.toJson(bsonObjectId.stringify)
  }

  implicit val objectIdRead: Reads[BSONObjectID] = Reads[BSONObjectID] { json =>
    (json \ "$oid").validate[String].flatMap { str =>
      BSONObjectID.parse(str) match {
        case Success(bsonId) => JsSuccess(bsonId)
        case Failure(err)    => JsError(__, s"Invalid BSON Object ID $json; ${err.getMessage}")
      }
    }
  }

  implicit val conversationFormat: OFormat[Conversation] = Json.format[Conversation]
}
