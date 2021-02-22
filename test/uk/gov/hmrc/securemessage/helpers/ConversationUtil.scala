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

package uk.gov.hmrc.securemessage.helpers

import cats.data.NonEmptyList
import org.joda.time.DateTime
import uk.gov.hmrc.securemessage.models.core.Language.English
import uk.gov.hmrc.securemessage.models.core._

object ConversationUtil {

  def getFullConversation(id: String): Conversation =
    Conversation(
      "cdcm",
      id,
      ConversationStatus.Open,
      Some(
        Map(
          "sourceId"         -> "CDCM",
          "caseId"           -> "D-80542",
          "queryId"          -> "D-80542-20201120",
          "mrn"              -> "DMS7324874993",
          "notificationType" -> "CDS Exports"
        )),
      "MRN: 19GB4S24GC3PPFGVR7",
      English,
      List(
        Participant(
          1,
          ParticipantType.System,
          Identifier("CDCM", "D-80542-20201120", None),
          Some("CDS Exports Team"),
          None,
          Some(Map("caseId" -> "D-80542", "queryId" -> "D-80542-20201120")),
          None
        ),
        Participant(
          2,
          ParticipantType.Customer,
          Identifier("EORINumber", "GB1234567890", Some("HMRC-CUS-ORG")),
          Some("Joe Bloggs"),
          Some("joebloggs@test.com"),
          None,
          None
        )
      ),
      NonEmptyList.one(
        Message(
          1,
          new DateTime("2020-11-10T15:00:01.000"),
          "QmxhaCBibGFoIGJsYWg=",
          None
        )
      )
    )

  def getMinimalConversation(id: String): Conversation =
    Conversation(
      "cdcm",
      id,
      ConversationStatus.Open,
      None,
      "D-80542-20201120",
      English,
      List(
        Participant(
          1,
          ParticipantType.System,
          Identifier("CDCM", "D-80542-20201120", None),
          Some("CDS Exports Team"),
          None,
          None,
          None),
        Participant(
          2,
          ParticipantType.Customer,
          Identifier("EORINumber", "GB1234567890", Some("HMRC-CUS-ORG")),
          None,
          None,
          None,
          None)
      ),
      NonEmptyList.one(
        Message(
          1,
          new DateTime("2020-11-10T15:00:01.000"),
          "QmxhaCBibGFoIGJsYWg=",
          None
        )
      )
    )

}
