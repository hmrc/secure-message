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
import play.api.Logger
import play.api.libs.json.{ JsValue, Json }
import uk.gov.hmrc.securemessage.helpers.Resources
import uk.gov.hmrc.securemessage.models.core._

@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
class ApiConversationSpec extends PlaySpec {

  "ApiConversation" must {
    "Convert core conversation to ApiConversation details and then serialise into JSON" in {
      val identifier = Identifier(name = "EORINumber", value = "GB1234567890", enrolment = Some("HMRC-CUS-ORG"))
      Logger.logger.warn(identifier.toString)
      val conversationJson: JsValue = Resources.readJson("model/core/conversation-full-extender.json")
      val coreConversation: Conversation = conversationJson.validate[Conversation].get
      val apiConversationJson: JsValue = Resources.readJson("model/api/api-conversation.json")
      val apiConversation: ApiConversation = apiConversationJson.validate[ApiConversation].get
      ApiConversation.coreConversationToApiConversation(coreConversation, identifier) mustEqual apiConversation
      Json.toJson(apiConversation) mustBe Json.parse(
        """{"client":"cdcm","conversationId":"D-80542-20201120","status":"open","tags":{"queryId":"D-80542-20201120","caseId":"D-80542","notificationType":"CDS Exports","mrn":"DMS7324874993","sourceId":"CDCM"},"subject":"D-80542-20201120","language":"en","messages":[{"senderInformation":{"name":"CDS Exports Team","created":"2020-11-10T15:00:01.000+0000"},"read":"2020-11-10T15:00:01.000+0000","content":"QmxhaCBibGFoIGJsYWg="},{"sent":"2020-11-10T15:00:05.000+0000","firstReaderTime":{"name":"CDS Exports Team","readTime":"2020-11-10T15:00:02.000+0000"},"content":"QmxhaCBibGFoIGJsYWg="},{"senderInformation":{"name":"Ben Dover","created":"2020-11-10T15:00:12.000+0000"},"read":"2020-11-10T15:00:01.000+0000","firstReaderTime":{"name":"CDS Exports Team","readTime":"2020-11-10T15:00:01.000+0000"},"content":"QmxhaCBibGFoIGJsYWg="},{"sent":"2020-11-10T15:00:18.000+0000","firstReaderTime":{"name":"Ben Dover","readTime":"2020-11-10T15:00:01.000+0000"},"content":"QmxhaCBibGFoIGJsYWg="}]}""")
    }
  }
}
