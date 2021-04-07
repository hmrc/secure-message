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
import play.api.http.Status.ACCEPTED
import uk.gov.hmrc.http.HttpReads.Implicits.readRaw
import uk.gov.hmrc.http.{ HeaderCarrier, HttpClient, HttpResponse }
//import uk.gov.hmrc.play.audit.model.EventTypes
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.securemessage.EmailSendingError
import uk.gov.hmrc.securemessage.models.EmailRequest
import uk.gov.hmrc.securemessage.models.EmailRequest.emailRequestWrites

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
@SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter", "org.wartremover.warts.Nothing"))
class EmailConnector @Inject()(
  httpClient: HttpClient,
  servicesConfig: ServicesConfig
//  override val auditConnector: AuditConnector
)(implicit ec: ExecutionContext) {

  private val emailBaseUrl = servicesConfig.baseUrl("email")

  def send(emailRequest: EmailRequest)(implicit hc: HeaderCarrier): Future[Either[EmailSendingError, Unit]] =
    httpClient.POST[EmailRequest, HttpResponse](s"$emailBaseUrl/hmrc/email", emailRequest).map { response =>
      response.status match {
        case ACCEPTED =>
//          val _ = auditEmailSent(EventTypes.Succeeded, emailRequest, ACCEPTED)
          Right(())
        case status =>
//          val _ = auditEmailSent(EventTypes.Failed, emailRequest, status)
          val errMsg = s"Email request failed: got response status $status from email service"
          Left(EmailSendingError(errMsg))
      }
    }

}
