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

package uk.gov.hmrc.securemessage.connectors

import play.api.http.Status
import play.api.mvc.{ Result, Results }
import uk.gov.hmrc.http.{ HeaderCarrier, HttpClient, HttpResponse }
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class MessageConnector @Inject()(httpClient: HttpClient, servicesConfig: ServicesConfig)(
  implicit ec: ExecutionContext) {

  private val messageBaseUrl = servicesConfig.baseUrl("message")

  def getContent(id: String)(implicit hc: HeaderCarrier): Future[HttpResponse] =
    httpClient.GET[HttpResponse](s"$messageBaseUrl/messages/$id/content")

  def setReadtime(id: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Result] =
    httpClient.POSTEmpty[HttpResponse](s"$messageBaseUrl/messages/$id/read-time") map { r =>
      r.status match {
        case Status.OK                    => Results.Ok
        case Status.INTERNAL_SERVER_ERROR => Results.InternalServerError
        case _                            => Results.NotFound
      }

    }

}
