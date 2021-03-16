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

package uk.gov.hmrc.securemessage.connectors

import javax.inject.{ Inject, Singleton }
import play.api.http.HeaderNames.AUTHORIZATION
import play.api.http.Status._
import uk.gov.hmrc.http.{ HeaderCarrier, HttpClient }
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.securemessage.models.QueryResponseWrapper

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
@SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter"))
class EISConnector @Inject()(httpClient: HttpClient, servicesConfig: ServicesConfig)(implicit ec: ExecutionContext) {

  private val eisBaseUrl = servicesConfig.baseUrl("eis")

  private val eisBearerToken = servicesConfig.getString("microservice.services.eis.bearer-token")

  def forwardMessage(queryResponse: QueryResponseWrapper): Future[Boolean] = {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    httpClient
      .doPut[QueryResponseWrapper](
        s"$eisBaseUrl/prsup/PRRestService/DMS/Service/QueryResponse",
        queryResponse,
        Seq((AUTHORIZATION, s"Bearer $eisBearerToken"))
      )
      .map { response =>
        response.status match {
          case NO_CONTENT => true
          case _          => false
        }
      }
  }

}
