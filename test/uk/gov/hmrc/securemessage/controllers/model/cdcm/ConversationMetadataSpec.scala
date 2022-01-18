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

package uk.gov.hmrc.securemessage.controllers.model.cdcm

import org.scalatestplus.play.PlaySpec
import play.api.i18n.Messages
import play.api.libs.json.{ JsObject, JsValue, Json }
import play.api.test.Helpers._
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.securemessage.controllers.model.cdcm.read.ConversationMetadata
import uk.gov.hmrc.securemessage.helpers.Resources
import uk.gov.hmrc.securemessage.models.core.Conversation._
import uk.gov.hmrc.securemessage.models.core._

class ConversationMetadataSpec extends PlaySpec {

  implicit val messages: Messages = stubMessages()
  val objectID = BSONObjectID.generate()
  "ConversationMetadata" must {
    "Convert core conversation to conversation metadata and then serialise into JSON" in {
      val identifier = Identifier(name = "EORINumber", value = "GB1234567890", enrolment = Some("HMRC-CUS-ORG"))
      val conversationJson: JsValue = Resources
        .readJson("model/core/conversation-full-extender.json")
        .as[JsObject] + ("_id" -> Json.toJson(objectID))
      val coreConversation: Conversation = conversationJson.validate[Conversation].get
      val conversationMetadataJson: JsValue = Resources.readJson("model/api/cdcm/read/conversation-metadata.json")
      val conversationMetadata: ConversationMetadata = conversationMetadataJson.validate[ConversationMetadata].get
      ConversationMetadata
        .coreToConversationMetadata(coreConversation, Set(identifier)) mustEqual conversationMetadata
      Json.toJson(conversationMetadata) mustBe Json.parse("""{
                                                            |    "client": "cdcm",
                                                            |    "conversationId": "D-80542-20201120",
                                                            |    "count": 8,
                                                            |    "issueDate": "2020-11-10T16:00:00.000+0000",
                                                            |    "senderName": "James Smith",
                                                            |    "subject": "MRN: 19GB4S24GC3PPFGVR7",
                                                            |    "unreadMessages": true
                                                            |}""".stripMargin)
    }

    "Convert core conversation to conversation metadata JSON when provided with a list of identifiers" in {
      val identifiers = Set(
        Identifier(name = "EORINumber", value = "GB1234567890", enrolment = Some("HMRC-CUS-ORG")),
        Identifier(name = "UTR", value = "GB1234567890", enrolment = Some("HMRC-CUS-ORG"))
      )
      val conversationJson: JsValue = Resources
        .readJson("model/core/conversation-full-extender.json")
        .as[JsObject] + ("_id" -> Json.toJson(objectID))
      val coreConversation: Conversation = conversationJson.validate[Conversation].get
      val conversationMetadataJson: JsValue = Resources.readJson("model/api/cdcm/read/conversation-metadata.json")
      val conversationMetadata: ConversationMetadata = conversationMetadataJson.validate[ConversationMetadata].get
      ConversationMetadata
        .coreToConversationMetadata(coreConversation, identifiers) mustEqual conversationMetadata
      Json.toJson(conversationMetadata) mustBe Json.parse("""{
                                                            |    "client": "cdcm",
                                                            |    "conversationId": "D-80542-20201120",
                                                            |    "count": 8,
                                                            |    "issueDate": "2020-11-10T16:00:00.000+0000",
                                                            |    "senderName": "James Smith",
                                                            |    "subject": "MRN: 19GB4S24GC3PPFGVR7",
                                                            |    "unreadMessages": true
                                                            |}""".stripMargin)
    }
  }
}
