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

import javax.inject.Inject
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json.{ JsString, Json }
import reactivemongo.api.indexes.{ Index, IndexType }
import reactivemongo.bson.BSONObjectID
import reactivemongo.core.errors.DatabaseException
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.mongo.{ MongoConnector, ReactiveRepository }
import uk.gov.hmrc.securemessage.controllers.models.generic.Enrolment
import uk.gov.hmrc.securemessage.models.core.Conversation

import scala.concurrent.{ ExecutionContext, Future }

@SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter"))
class ConversationRepository @Inject()(implicit connector: MongoConnector)
    extends ReactiveRepository[Conversation, BSONObjectID](
      "conversation",
      connector.db,
      Conversation.conversationFormat,
      ReactiveMongoFormats.objectIdFormats) {

  private val DuplicateKey = 11000

  override def indexes: Seq[Index] =
    Seq(
      Index(
        key = Seq("client" -> IndexType.Ascending, "conversationId" -> IndexType.Ascending),
        name = Some("unique-conversation"),
        unique = true,
        sparse = true))

  def insertIfUnique(conversation: Conversation)(implicit ec: ExecutionContext): Future[Boolean] =
    insert(conversation)
      .map(_ => true)
      .recoverWith {
        case e: DatabaseException if e.code.contains(DuplicateKey) =>
          logger.warn("Ignoring duplicate found on insertion to conversation collection: " + e.getMessage())
          Future.successful(false)
      }

  def getConversations(enrolment: Enrolment)(implicit ec: ExecutionContext): Future[List[Conversation]] =
    find(findByEnrolmentQuery(enrolment): _*)

  def getConversation(client: String, conversationId: String, enrolment: Enrolment)(
    implicit ec: ExecutionContext): Future[Option[Conversation]] = {
    import uk.gov.hmrc.securemessage.models.core.Conversation.conversationFormat
    collection
      .find(
        selector = Json.obj("client" -> client, "conversationId" -> conversationId)
          deepMerge Json.obj(findByEnrolmentQuery(enrolment): _*),
        None)
      .one[Conversation]
  }

  private def findByEnrolmentQuery(enrolment: Enrolment): Seq[(String, JsValueWrapper)] =
    Seq(
      "participants.identifier.name"      -> JsString(enrolment.name),
      "participants.identifier.value"     -> JsString(enrolment.value),
      "participants.identifier.enrolment" -> JsString(enrolment.key)
    )
}
