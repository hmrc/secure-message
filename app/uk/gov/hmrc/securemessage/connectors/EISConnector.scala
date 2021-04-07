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

import java.time.{ ZoneOffset, ZonedDateTime }
import java.time.format.DateTimeFormatter

import controllers.Assets.{ ACCEPT, CONTENT_TYPE, DATE }
import javax.inject.{ Inject, Singleton }
import play.api.http.HeaderNames.AUTHORIZATION
import play.api.http.MimeTypes
import play.api.http.Status._
import uk.gov.hmrc.http.{ HeaderCarrier, HttpClient }
//import uk.gov.hmrc.play.audit.model.EventTypes
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.securemessage.EisForwardingError
import uk.gov.hmrc.securemessage.connectors.utils.CustomHeaders
import uk.gov.hmrc.securemessage.models.QueryMessageWrapper
//import uk.gov.hmrc.securemessage.services.Auditing
//import uk.gov.hmrc.securemessage.models.QueryResponseWrapper
import scala.concurrent.{ ExecutionContext, Future }

//TODO: add tests for the connector
@Singleton
@SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter"))
class EISConnector @Inject()(httpClient: HttpClient, servicesConfig: ServicesConfig)(implicit ec: ExecutionContext) {

  private val eisBaseUrl = servicesConfig.baseUrl("eis")
  private val eisBearerToken = servicesConfig.getString("microservice.services.eis.bearer-token")
  private val eisEndpoint = servicesConfig.getString("microservice.services.eis.endpoint")
  private val eisEnvironment = servicesConfig.getString("microservice.services.eis.environment")

  def forwardMessage(queryMessageWrapper: QueryMessageWrapper): Future[Either[EisForwardingError, Unit]] = {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    httpClient
      .doPut[QueryMessageWrapper](
        s"$eisBaseUrl$eisEndpoint",
        queryMessageWrapper,
        Seq(
          (CONTENT_TYPE, MimeTypes.JSON),
          (ACCEPT, MimeTypes.JSON),
          (AUTHORIZATION, s"Bearer $eisBearerToken"),
          (DATE, DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC))),
          (CustomHeaders.CorrelationId, queryMessageWrapper.queryMessageRequest.requestCommon.acknowledgementReference),
          (CustomHeaders.ForwardedHost, "Digital"),
          (CustomHeaders.Environment, eisEnvironment)
        )
      )
      .flatMap { response =>
        response.status match {
          case NO_CONTENT =>
//            val _ = auditMessageForwarded(EventTypes.Succeeded, queryMessageWrapper.queryMessageRequest, NO_CONTENT)
            Future.successful(Right(()))
          case code =>
//            val _ = auditMessageForwarded(EventTypes.Failed, queryMessageWrapper.queryMessageRequest, code)
            Future.successful(Left(EisForwardingError(
              s"There was an issue with forwarding the message to EIS, response code is: $code, response body is: ${response.body}")))
        }
      }
  }
}
