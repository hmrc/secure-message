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

package uk.gov.hmrc.securemessage.services

import com.google.inject.Inject
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.Result
import play.api.mvc.Results.{ Conflict, Created, InternalServerError }
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.securemessage.models.core.MessageV4
import uk.gov.hmrc.securemessage.repository.MessageV4Repository

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

trait SecureMessageV4Service {
  def createMessage(jsonMessage: JsValue)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Result]
}

class SecureMessageV4ServiceImpl @Inject()(messageV4Repository: MessageV4Repository) extends SecureMessageV4Service {
  override def createMessage(jsonMessage: JsValue)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Result] =
    Try(jsonMessage.as[MessageV4]) match {
      case Success(message) =>
        messageV4Repository.save(message) map {
          case true  => Created(Json.obj("id"      -> message.id.toString))
          case false => Conflict(Json.obj("reason" -> "Duplicate Message"))
        }
      case Failure(exception) =>
        Future.successful(
          InternalServerError(Json.obj("reason" -> s"Failed to parse message: ${exception.getMessage}")))
    }
}
