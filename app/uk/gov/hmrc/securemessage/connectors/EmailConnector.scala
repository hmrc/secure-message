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

import controllers.Assets.CREATED
import javax.inject.{ Inject, Singleton }
import play.api.Logging
import uk.gov.hmrc.http.HttpReads.Implicits.readRaw
import uk.gov.hmrc.http.{ HeaderCarrier, HttpClient, HttpResponse }
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.securemessage.models.EmailRequest
import uk.gov.hmrc.securemessage.models.EmailRequest.emailRequestWrites

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
@SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter"))
class EmailConnector @Inject()(httpClient: HttpClient, servicesConfig: ServicesConfig)(implicit ec: ExecutionContext)
    extends Logging {

  private val emailBaseUrl = servicesConfig.baseUrl("email")

  def send(emailRequest: EmailRequest)(implicit hc: HeaderCarrier): Future[Unit] =
    httpClient.POST[EmailRequest, HttpResponse](s"$emailBaseUrl/hmrc/email", emailRequest).map { response =>
      response.status match {
        case CREATED => ()
        case status  => logger.error(s"Email request failed: got response status $status from email service")
      }
    }

}