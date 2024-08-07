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

import play.api.Logging
import play.api.http.Status
import uk.gov.hmrc.http.{ HeaderCarrier, HttpResponse }
import uk.gov.hmrc.securemessage.models.v4.MobileNotification
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.securemessage.controllers.Auditing
import play.api.libs.json.Json
import play.api.libs.ws.writeableOf_JsValue

import java.net.URI
import javax.inject.{ Inject, Named }
import scala.concurrent.{ ExecutionContext, Future }

class MobilePushNotificationsConnector @Inject() (
  http: HttpClientV2,
  override val auditConnector: AuditConnector,
  @Named("mobile-push-notifications-orchestration-base-url") mobileNotificationsUri: String
) extends Logging with Auditing {

  def sendNotification(
    notification: MobileNotification
  )(implicit headerCarrier: HeaderCarrier, ex: ExecutionContext): Future[Unit] =
    http
      .post(new URI(s"$mobileNotificationsUri/send-push-notification/secure-message").toURL)
      .withBody(Json.toJson(notification))
      .execute[HttpResponse]
      .map { r =>
        val error: Option[String] = r.status match {
          case Status.CREATED => None
          case _              => Some(s"Failed to push the notification. Response:${r.body}")
        }
        auditMobilePushNotification(notification, r.status.toString, error)
        ()
      }
      .recover { case e =>
        auditMobilePushNotification(notification, "internal-error", Some(e.getMessage))
        logger.warn(s"Error while attempting to send alert to mobile push notification service ${e.getMessage}")
      }
}
