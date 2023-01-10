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

package uk.gov.hmrc.securemessage.controllers.model.common.write

import play.api.libs.json.{ Json, Reads }
import uk.gov.hmrc.securemessage.models.RequestDetail

final case class CustomerMessage(content: String) {
  def asRequestDetail(requestId: String, conversationId: String): RequestDetail =
    RequestDetail(requestId, conversationId, content)
}

object CustomerMessage {
  implicit val customerMessageRequestReads: Reads[CustomerMessage] = Json.reads[CustomerMessage]
}
