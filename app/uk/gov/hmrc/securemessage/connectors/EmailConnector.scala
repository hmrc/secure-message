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

import javax.inject.{ Inject, Singleton }
import play.api.http.Status.ACCEPTED
import uk.gov.hmrc.http.HttpReads.Implicits.readRaw
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{ HeaderCarrier, HttpResponse }
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.securemessage.EmailSendingError
import uk.gov.hmrc.securemessage.controllers.Auditing
import uk.gov.hmrc.securemessage.models.EmailRequest
import uk.gov.hmrc.securemessage.models.EmailRequest.emailRequestWrites
import play.api.libs.json.Json
import play.api.libs.ws.writeableOf_JsValue

import java.net.URI
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class EmailConnector @Inject() (
  httpClient: HttpClientV2,
  servicesConfig: ServicesConfig,
  override val auditConnector: AuditConnector
)(implicit ec: ExecutionContext)
    extends Auditing {

  private val emailUrl = new URI(s"${servicesConfig.baseUrl("email")}/hmrc/email").toURL

  def send(emailRequest: EmailRequest)(implicit hc: HeaderCarrier): Future[Either[EmailSendingError, Unit]] =
    httpClient
      .post(emailUrl)
      .withBody(Json.toJson(emailRequest))
      .execute[HttpResponse]
      .map { response =>
        response.status match {
          case ACCEPTED =>
            val _ = auditEmailSent("NotificationEmailSent", emailRequest, ACCEPTED)
            Right(())
          case status =>
            val _ = auditEmailSent("NotificationEmailSentFailed", emailRequest, status)
            val errMsg = s"Email request failed: got response status $status from email service"
            Left(EmailSendingError(errMsg))
        }
      }

}
