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

package uk.gov.hmrc.securemessage.testonly.controllers

import cats.data.NonEmptyList
import com.google.inject.Inject
import org.joda.time.DateTime
import play.api.Logging
import play.api.libs.json.{ JsObject, JsValue, Json }
import play.api.mvc.{ Action, AnyContent, ControllerComponents }
import reactivemongo.api.WriteConcern
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.securemessage.models.core.Language.English
import uk.gov.hmrc.securemessage.models.core.Letter._
import uk.gov.hmrc.securemessage.models.core._
import uk.gov.hmrc.securemessage.repository.{ ConversationRepository, MessageRepository }

import scala.concurrent.ExecutionContext
class TestOnlyController @Inject()(
  cc: ControllerComponents,
  conversationRepository: ConversationRepository,
  messageRepository: MessageRepository)(implicit ec: ExecutionContext)
    extends BackendController(cc) with Logging {

  def deleteConversation(id: String): Action[AnyContent] = Action.async { _ =>
    conversationRepository.removeById(BSONObjectID.parse(id).get, WriteConcern.Default).map { _ =>
      Ok(s"$id deleted successfully")
    }
  }

  def insertMessage(id: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    val messageBody: JsObject = request.body.as[JsObject] + ("_id" -> Json.toJson(BSONObjectID.parse(id).get))
    val letter = messageBody.validate[Letter].get
    messageRepository.insert(letter).map(_ => Created)
  }

  def insertConversation(id: String): Action[JsValue] = Action.async(parse.json) { _ =>
    val identifier = Identifier("EORINumber", "GB1234567890", Some("HMRC-CUS-ORG"))
    val conversation = Conversation(
      BSONObjectID.parse(id).get,
      "CDCM",
      id,
      ConversationStatus.Open,
      None,
      "CDS-EXPORTS Subject",
      English,
      List(
        Participant(
          1,
          ParticipantType.System,
          Identifier("CDCM", "D-80542-20201120", None),
          Some("CDS Exports Team"),
          None,
          None,
          None
        ),
        Participant(
          2,
          ParticipantType.Customer,
          identifier,
          None,
          None,
          None,
          None
        )
      ),
      NonEmptyList.one(
        ConversationMessage(
          1,
          DateTime.now,
          "QmxhaCBibGFoIGJsYWg="
        )
      ),
      Alert("", None)
    )
    conversationRepository.insert(conversation).map(_ => Created)
  }

  def deleteMessage(id: String): Action[AnyContent] =
    Action.async(
      _ =>
        messageRepository
          .removeById(BSONObjectID.parse(id).get, WriteConcern.Default)
          .map(_ => Ok(s"message $id deleted successfully")))
}
