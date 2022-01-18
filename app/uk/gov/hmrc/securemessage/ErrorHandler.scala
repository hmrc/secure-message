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

package uk.gov.hmrc.securemessage

import play.api.http.HttpErrorHandler
import play.api.http.Status.BAD_REQUEST
import play.api.mvc.Results.Status
import play.api.mvc.{ RequestHeader, Result }
import uk.gov.hmrc.play.bootstrap.backend.http.JsonErrorHandler

import javax.inject.Inject
import scala.concurrent.Future

class ErrorHandler @Inject()(jsonErrorHandler: JsonErrorHandler) extends HttpErrorHandler {
  override def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] =
    statusCode match {
      case BAD_REQUEST =>
        jsonErrorHandler.onClientError(request, statusCode, message)
        Future.successful(Status(statusCode)(message))
      case _ =>
        jsonErrorHandler.onClientError(request, statusCode, message)
    }

  override def onServerError(request: RequestHeader, exception: Throwable): Future[Result] =
    jsonErrorHandler.onServerError(request, exception)

}
