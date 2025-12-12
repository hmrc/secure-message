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

import play.api.{ Configuration, Logging }
import play.api.http.Status.OK
import play.api.libs.json.{ JsSuccess, Json }
import uk.gov.hmrc.common.message.emailaddress.EmailAddress
import uk.gov.hmrc.securemessage.formatter.PlayJsonFormats.emailAddressReads
import uk.gov.hmrc.http.{ HeaderCarrier, HttpResponse }
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.securemessage.EmailLookupError
import uk.gov.hmrc.securemessage.models.core.Identifier
import uk.gov.hmrc.securemessage.models.VerifyEmailRequest
import common.url
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

@Singleton
class ChannelPreferencesConnector @Inject() (config: Configuration, httpClient: HttpClientV2)(implicit
  ec: ExecutionContext
) extends ServicesConfig(config) with Logging {

  def getEmailForEnrolment(identifier: Identifier)(implicit
    hc: HeaderCarrier
  ): Future[Either[EmailLookupError, EmailAddress]] =
    identifier.enrolment match {
      case None => Future.successful(Left(EmailLookupError("Enrolment cannot be Empty")))
      case Some(enrolment) =>
        val request = VerifyEmailRequest(
          enrolmentKey = enrolment,
          identifierKey = identifier.name,
          identifierValue = identifier.value
        )
        httpClient
          .post(url(s"${baseUrl("channel-preferences")}/channel-preferences/get-verified-email"))
          .withBody(Json.toJson(request))
          .execute[HttpResponse]
          .map { resp =>
            resp.status match {
              case OK => parseEmail(resp.body)
              case s =>
                Left(EmailLookupError(s"channel-preferences returned status: $s body: ${resp.body}"))
            }
          }
    }

  private def parseEmail(body: String): Either[EmailLookupError, EmailAddress] =
    Try(Json.parse(body)) match {
      case Success(v) =>
        (v \ "address").validate[EmailAddress] match {
          case JsSuccess(ev, _) => Right(ev)
          case _ =>
            val errMsg = s"could not find an email address in the response: $body"
            Left(EmailLookupError(errMsg))
        }
      case Failure(e) =>
        val errMsg = s"channel-preferences response was an invalid json: $body, error: ${e.getMessage}"
        Left(EmailLookupError(errMsg))
    }

}
