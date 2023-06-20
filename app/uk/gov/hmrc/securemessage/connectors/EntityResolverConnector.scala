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
import play.api.libs.json.Json
import play.mvc.Http.Status
import uk.gov.hmrc.http.HttpClient
import uk.gov.hmrc.common.message.model.TaxEntity
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.securemessage.models.TaxId

import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }

sealed trait VerifiedEmailAddressResponse extends Product with Serializable {
  def fold[X](validated: String => X, notValidated: String => X): X =
    this match {
      case EmailValidation(email)       => validated(email)
      case x @ VerifiedEmailNotFound(_) => notValidated(x.getMessage)
    }

  def value: Either[VerifiedEmailNotFound, EmailValidation] =
    this match {
      case valid: EmailValidation          => Right(valid)
      case notFound: VerifiedEmailNotFound => Left(notFound)
    }
}

case class EmailValidation(email: String) extends VerifiedEmailAddressResponse

object EmailValidation {
  implicit val format = Json.format[EmailValidation]
}

case class VerifiedEmailNotFound(reasonCode: String) extends VerifiedEmailAddressResponse {
  def getMessage: String = reasonCode match {
    case "EMAIL_ADDRESS_NOT_VERIFIED" =>
      "The backend has rejected the message due to not being able to verify the email address."
    case "NOT_OPTED_IN"          => "email: not verified as user not opted in"
    case "PREFERENCES_NOT_FOUND" => "email: not verified as preferences not found"
    case _                       => "email: not verified for unknown reason"
  }
}

case class OtherException(message: String) extends Exception(message)

case class CallFailedException(message: String) extends Exception(message)

@Singleton
class EntityResolverConnector @Inject()(config: Configuration, httpClient: HttpClient)(implicit ec: ExecutionContext)
    extends ServicesConfig(config) with Logging {

  def url(path: String): String = s"${baseUrl("entity-resolver")}$path"

  def verifiedEmailAddress(recipient: TaxEntity): Future[VerifiedEmailAddressResponse] =
    httpClient
      .doGet(url(s"/portal/preferences/${recipient.regime}/${recipient.identifier.value}/verified-email-address"))
      .map { response =>
        response.status match {
          case Status.OK        => response.json.as[EmailValidation]
          case Status.NOT_FOUND => VerifiedEmailNotFound((response.json \ "reason").as[String])
          case status           => throw new OtherException(s"OTHER_EXCEPTION_$status")
        }
      }

  def getTaxId(recipient: TaxEntity): Future[Option[TaxId]] =
    httpClient.doGet(url(s"/entity-resolver/${recipient.regime}/${recipient.identifier.value}")).map { response =>
      response.status match {
        case Status.OK => response.json.asOpt[TaxId]
        case _         => None
      }

    }

}
