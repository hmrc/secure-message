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
import uk.gov.hmrc.securemessage.EisForwardingError
import uk.gov.hmrc.securemessage.models.QueryResponseWrapper

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
@SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter"))
class EISConnector @Inject()(httpClient: HttpClient, servicesConfig: ServicesConfig)(implicit ec: ExecutionContext) {

  private val eisBaseUrl = servicesConfig.baseUrl("eis")

  private val eisBearerToken = servicesConfig.getString("microservice.services.eis.bearer-token")

  def forwardMessage(queryResponse: QueryResponseWrapper, client: String): Future[Either[EisForwardingError, Unit]] = {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    httpClient
      .doPut[QueryResponseWrapper](
        s"$eisBaseUrl/prsup/PRRestService/DMS/Service/QueryResponse",
        queryResponse,
        Seq((AUTHORIZATION, s"Bearer $eisBearerToken"))
      )
      .flatMap { response =>
        response.status match {
          case NO_CONTENT => Future(Right(()))
          case _ =>
            val query = queryResponse.queryResponse
            Future(Left(EisForwardingError(
              s"There was an issue with forwarding the message to EIS for id: ${query.id}, client: $client and conversationId: ${query.conversationId}")))
        }
      }
  }

}
