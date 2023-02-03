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

package uk.gov.hmrc.securemessage.controllers.model

import play.api.libs.json.{ JsValue, Writes }
import uk.gov.hmrc.securemessage.controllers.model.cdcm.read.ApiConversation
import uk.gov.hmrc.securemessage.controllers.model.cdsf.read.ApiLetter

trait ApiMessage {}

object ApiMessage {
  implicit val apiMessageFormat: Writes[ApiMessage] = new Writes[ApiMessage] {
    override def writes(o: ApiMessage): JsValue = o match {
      case c: ApiConversation         => ApiConversation.conversationFormat.writes(c)
      case l: ApiLetter               => ApiLetter.messageFormat.writes(l)
      case m: MessageResourceResponse => MessageResourceResponse.messageResourceResponseWrites.writes(m)
    }
  }
}
