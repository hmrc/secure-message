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
import uk.gov.hmrc.http.{ HeaderCarrier, HttpClient, HttpResponse }
import uk.gov.hmrc.securemessage.models.v4.MobileNotification
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.securemessage.controllers.Auditing

import javax.inject.{ Inject, Named }
import scala.concurrent.{ ExecutionContext, Future }

class MobilePushNotificationsConnector @Inject()(
  http: HttpClient,
  auditing: Auditing,
  @Named("mobile-push-notifications-orchestration-base-url") mobileNotificationsUri: String
) extends Logging {

  def sendNotification(
    notification: MobileNotification
  )(implicit headerCarrier: HeaderCarrier, ex: ExecutionContext): Future[Unit] =
    http
      .POST[MobileNotification, HttpResponse](
        s"$mobileNotificationsUri/send-push-notification/secure-message",
        notification
      )
      .map { r =>
        auditing.auditMobilePushNotification(notification, r.status.toString)
        ()
      }
      .recover {
        case e =>
          auditing.auditMobilePushNotification(notification, "internal-error", Some(e.getMessage))
          logger.warn(s"Error while attempting to send alert to mobile push notification service ${e.getMessage}")
      }
}
