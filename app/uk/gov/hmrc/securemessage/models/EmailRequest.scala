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

package uk.gov.hmrc.securemessage.models

import cats.implicits.catsSyntaxOptionId
import play.api.libs.json.{ Json, OWrites }
import uk.gov.hmrc.common.message.emailaddress.*
import uk.gov.hmrc.common.message.model.{ Alertable, TaxpayerName }
import uk.gov.hmrc.securemessage.models.v4.SecureMessage
import uk.gov.hmrc.common.message.model.TaxEntity.getEnrolments

final case class Tags(messageId: Option[String], source: Option[String], enrolment: Option[String])

final case class EmailRequest(
  to: List[EmailAddress],
  templateId: String,
  parameters: Map[String, String],
  tags: Option[Tags],
  auditData: Map[String, String] = Map.empty,
  eventUrl: Option[String] = None,
  onSendUrl: Option[String] = None,
  alertQueue: Option[String] = None,
  emailSource: Option[String] = None
)

object EmailRequest {

  implicit val enrolmentsRequestWrite: OWrites[Tags] = Json.writes[Tags]

  implicit val emailRequestWrites: OWrites[EmailRequest] = Json.writes[EmailRequest]

  def createEmailRequest(message: SecureMessage, taxId: Option[TaxId] = None): EmailRequest =
    EmailRequest(
      to = List(EmailAddress(message.emailAddress)),
      templateId = message.templateId,
      parameters = message.alertDetails.data ++
        message.alertDetails.recipientName.fold(Map.empty[String, String])(_.asMap) ++
        taxIdentifiers(taxId, Map(message.recipient.identifier.name -> message.recipient.identifier.value)),
      auditData = message.auditData,
      eventUrl = None,
      onSendUrl = None,
      alertQueue = message.alertQueue,
      emailSource = None,
      tags = Tags(
        message.externalRef.id.some,
        message.externalRef.source.some,
        getEnrolments(message.recipient).main.some
      ).some
    )

  private def taxIdentifiers(taxId: Option[TaxId], default: Map[String, String]): Map[String, String] =
    taxId match {
      case Some(id) =>
        Map("sautr" -> id.sautr.getOrElse("N/A"), "nino" -> id.nino.getOrElse("N/A")) ++
          default.filterNot(p => List("sautr", "nino") contains p._1)
      case _ => default
    }

  def createEmailRequestFromAlert(
    alertable: Alertable,
    emailAddress: String,
    taxpayerName: Option[TaxpayerName],
    eventUrl: Option[String],
    sendCallbackUrl: Option[String],
    alertQueue: Option[String] = None,
    emailSource: Option[String] = None,
    enrolment: String = ""
  ): EmailRequest =
    EmailRequest(
      to = List(EmailAddress(emailAddress)),
      templateId = alertable.alertTemplateName,
      parameters = alertable.alertParams ++ taxpayerName.fold(Map.empty[String, String])(_.asMap),
      auditData =
        alertable.auditData ++ Map(alertable.recipient.identifier.name -> alertable.recipient.identifier.value),
      eventUrl = eventUrl,
      onSendUrl = sendCallbackUrl,
      alertQueue = alertQueue,
      emailSource = emailSource,
      tags = Tags(
        alertable.externalRef.map(_.id),
        alertable.externalRef.map(_.source),
        getEnrolments(alertable.recipient).main.some
      ).some
    )

}
