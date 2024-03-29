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

package uk.gov.hmrc.securemessage.controllers.model.cdcm.write

import play.api.libs.json.{ Json, Reads }
import uk.gov.hmrc.securemessage.models.core.Identifier

final case class CaseworkerMessage(content: String) {
  def senderIdentifier(clientName: String, conversationId: String): Identifier =
    Identifier(clientName, conversationId, None)
}

object CaseworkerMessage {

  implicit val caseworkerMessageRequestReads: Reads[CaseworkerMessage] = Json.reads[CaseworkerMessage]

  final case class System(identifier: SystemIdentifier)
  object System {
    implicit val systemReads: Reads[System] = Json.reads[System]
  }

  final case class Sender(system: System)
  object Sender {
    implicit val senderReads: Reads[Sender] =
      Json.reads[Sender]
  }

  final case class SystemIdentifier(name: String, value: String) {
    def asIdentifier: Identifier = Identifier(name, value, None)
  }
  object SystemIdentifier {
    implicit val identifierReads: Reads[SystemIdentifier] = Json.reads[SystemIdentifier]
  }

}
