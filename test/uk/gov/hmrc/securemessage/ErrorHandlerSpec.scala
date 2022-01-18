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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{ atLeastOnce, verify, when }
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.Status.{ BAD_REQUEST, NOT_FOUND }
import play.api.mvc.RequestHeader
import play.api.mvc.Results.Ok
import uk.gov.hmrc.play.bootstrap.backend.http.JsonErrorHandler

import scala.concurrent.Future

class ErrorHandlerSpec extends AnyFreeSpec with MockitoSugar with Matchers {
  val jsonErrorHandler: JsonErrorHandler = mock[JsonErrorHandler]
  when(jsonErrorHandler.onClientError(any[RequestHeader], any[Int], any[String])).thenReturn(Future.successful(Ok("")))
  when(jsonErrorHandler.onServerError(any[RequestHeader], any[Throwable])).thenReturn(Future.successful(Ok("")))

  private val errorHandler = new ErrorHandler(jsonErrorHandler)

  "Methods tests" - {
    "onClientError" in {
      errorHandler.onClientError(None.orNull, BAD_REQUEST, "")
      verify(jsonErrorHandler, atLeastOnce()).onClientError(any[RequestHeader], any[Int], any[String])
      errorHandler.onClientError(None.orNull, NOT_FOUND, "")
      verify(jsonErrorHandler, atLeastOnce()).onClientError(any[RequestHeader], any[Int], any[String])
    }

    "onServerError" in {
      errorHandler.onServerError(None.orNull, new Exception(""))
      verify(jsonErrorHandler, atLeastOnce()).onServerError(any[RequestHeader], any[Throwable])
    }
  }
}
