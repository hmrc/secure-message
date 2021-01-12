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

import org.joda.time.DateTime
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{ JsSuccess, JsValue }
import uk.gov.hmrc.securemessage.helpers.Resources
import uk.gov.hmrc.securemessage.models.core.Language.English

class ConversationSpec extends PlaySpec {

  "Validating a conversation" must {

    "be successful when optional fields are present" in {

      val conversationJson: JsValue = Resources.readJson("model/core/FullConversation.json")

      conversationJson.validate[Conversation] mustBe JsSuccess(
        Conversation(
          "123",
          ConversationStatus.Open,
          Some(Map("tag1Name" -> "tag1Value")),
          "Some subject",
          Some(English),
          List(
            Participant(1, ParticipantType.System, "CDS Exports Team", Identifier("CDCM", "queue-123", None), None),
            Participant(
              2,
              ParticipantType.Customer,
              "Fred Smith",
              Identifier("EORINumber", "GB1234567890", Some("HMRC-CUS-ORG")),
              Some("test@test.com"))
          ),
          List(
            Message(
              1,
              new DateTime("2020-11-10T15:00:01.000Z"),
              List(
                Reader(1, new DateTime("2020-12-10T15:00:01.000Z")),
                Reader(2, new DateTime("2020-12-11T15:00:01.000Z"))),
              "QmxhaCBibGFoIGJsYWg="
            ),
            Message(2, new DateTime("2020-11-10T15:00:01.000Z"), List.empty, "QmxhaCBibGFoIGJsYWg=")
          )
        ))
    }

    "be successful when optional fields are not present" in {

      val conversationJson: JsValue = Resources.readJson("model/core/MinimalConversation.json")

      conversationJson.validate[Conversation] mustBe JsSuccess(
        Conversation(
          "123",
          ConversationStatus.Open,
          None,
          "Some subject",
          None,
          List(
            Participant(1, ParticipantType.System, "CDS Exports Team", Identifier("CDCM", "queue-123", None), None)
          ),
          List(
            Message(
              1,
              new DateTime("2020-11-10T15:00:01.000Z"),
              List(
                Reader(1, new DateTime("2020-12-10T15:00:01.000Z")),
                Reader(2, new DateTime("2020-12-11T15:00:01.000Z"))),
              "QmxhaCBibGFoIGJsYWg="
            )
          )
        ))
    }

  }

}
