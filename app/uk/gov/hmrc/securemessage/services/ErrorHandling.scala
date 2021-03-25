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

package uk.gov.hmrc.securemessage.services

import play.api.Logging
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.mvc.Results._
import uk.gov.hmrc.securemessage.controllers.model.ClientName
import uk.gov.hmrc.securemessage._

trait ErrorHandling extends Logging {

  def handleErrors(client: ClientName, conversationId: String, error: Exception): Result = {
    val errMsg =
      s"Error on conversation with client: $client, conversationId: $conversationId, error message: ${error.getMessage}"
    logger.error(error.getMessage, error.getCause)
    val jsonError = Json.toJson(errMsg)
    error match {
      case EmailSendingError(_)                        => Created(jsonError)
      case NoReceiverEmailError(_)                     => Created(jsonError)
      case DuplicateConversationError(_, _)            => Conflict(jsonError)
      case InvalidContent(_, _) | InvalidRequest(_, _) => BadRequest(jsonError)
      case ParticipantNotFound(_)                      => Unauthorized(jsonError)
      case ConversationNotFound(_)                     => NotFound(jsonError)
      case EisForwardingError(_)                       => BadGateway(jsonError)
      case StoreError(_, _)                            => InternalServerError(jsonError)
      case _                                           => InternalServerError(jsonError)
    }
  }

}
