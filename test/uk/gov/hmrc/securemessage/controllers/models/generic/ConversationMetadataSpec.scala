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

package uk.gov.hmrc.securemessage.controllers.models.generic

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{ JsValue, Json }
import uk.gov.hmrc.securemessage.helpers.Resources
import uk.gov.hmrc.securemessage.models.core._

@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
class ConversationMetadataSpec extends PlaySpec {

  "ConversationMetadata" must {
    "Convert core conversation to conversation metadata and then serialise into JSON" in {
      val identifier = Identifier(name = "EORINumber", value = "GB1234567890", enrolment = Some("HMRC-CUS-ORG"))
      val conversationJson: JsValue = Resources.readJson("model/core/conversation-full-extender.json")
      val coreConversation: Conversation = conversationJson.validate[Conversation].get
      val conversationMetadataJson: JsValue = Resources.readJson("model/api/conversation-metadata.json")
      val conversationMetadata: ConversationMetadata = conversationMetadataJson.validate[ConversationMetadata].get
      ConversationMetadata.coreToConversationMetadata(coreConversation, identifier) mustEqual conversationMetadata
      Json.toJson(conversationMetadata) mustBe Json.parse("""{"client":"cdcm",
                                                            |"conversationId":"D-80542-20201120",
                                                            |"subject":"MRN: 19GB4S24GC3PPFGVR7",
                                                            |"issueDate":"2020-11-10T15:00:18.000+0000",
                                                            |"senderName":"CDS Exports Team",
                                                            |"unreadMessages":true,
                                                            |"count":7}""".stripMargin)
    }
  }
}
