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

import play.api.{ Configuration, Logging }
import play.api.http.Status.{ BAD_GATEWAY, OK }
import play.api.libs.json.{ JsError, JsResult, JsString, JsSuccess, JsValue, Json, Reads }
import uk.gov.hmrc.emailaddress.EmailAddress
import uk.gov.hmrc.http.{ HeaderCarrier, HttpClient }
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.securemessage.models.core.Identifier

import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

@Singleton
@SuppressWarnings(Array("org.wartremover.warts.All"))
class ChannelPreferencesConnector @Inject()(config: Configuration, httpClient: HttpClient)(
  implicit ec: ExecutionContext)
    extends ServicesConfig(config) with Logging {

  def channelPrefUrl(id: Identifier): String =
    s"""${baseUrl("channel-preferences")}/channel-preferences/preference/email/${id.enrolment.get}/${id.name}/${id.value}"""

  implicit object EmailAddressReads extends Reads[EmailAddress] {
    def reads(json: JsValue): JsResult[EmailAddress] = json match {
      case JsString(s) =>
        Try(EmailAddress(s)) match {
          case Success(v) => JsSuccess(v)
          case Failure(e) => JsError(e.getMessage)
        }
      case _ => JsError("Uable to parse email address")
    }
  }

  private def parseEmail(body: String): Either[Int, EmailAddress] =
    Try(Json.parse(body)) match {
      case Success(v) =>
        (v \ "address").validate[EmailAddress] match {
          case JsSuccess(ev, _) => Right(ev)
          case _ =>
            logger.warn(s"unable to parse $body")
            Left(BAD_GATEWAY)
        }
      case Failure(e) =>
        logger.error(s"channel-preferences response was invalid", e)
        Left(BAD_GATEWAY)
    }

  def getEmailForEnrolment(id: Identifier)(implicit hc: HeaderCarrier): Future[Either[Int, EmailAddress]] =
    httpClient
      .doGet(channelPrefUrl(id))
      .map { resp =>
        resp.status match {
          case OK => parseEmail(resp.body)
          case s  => Left(s)
        }
      }

}
