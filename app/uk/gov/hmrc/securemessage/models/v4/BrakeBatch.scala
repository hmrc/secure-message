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

package uk.gov.hmrc.securemessage.models.v4

import org.joda.time.LocalDate
import play.api.libs.json.{ Json, OFormat }
import uk.gov.hmrc.securemessage.models.core.Language.{ English, Welsh }
import play.api.libs.json.JodaWrites._
import play.api.libs.json.JodaReads._

trait BaseBrakeBatch {
  def batchId: String
  def formId: String
  def issueDate: LocalDate
  def templateId: String
}
case class BrakeBatch(batchId: String, formId: String, issueDate: LocalDate, templateId: String) extends BaseBrakeBatch

object BrakeBatch {
  implicit val brakeBatchFormat: OFormat[BrakeBatch] = Json.format[BrakeBatch]
}

case class BrakeBatchApproval(
  batchId: String,
  formId: String,
  issueDate: LocalDate,
  templateId: String,
  reasonText: String
) extends BaseBrakeBatch

object BrakeBatchApproval {
  implicit val brakeBatchApprovalFormat: OFormat[BrakeBatchApproval] = Json.format[BrakeBatchApproval]

  def apply(b: BrakeBatch, reasonText: String = ""): BrakeBatchApproval =
    new BrakeBatchApproval(b.batchId, b.formId, b.issueDate, b.templateId, reasonText)
}

case class BrakeBatchDetails(batchId: String, formId: String, issueDate: LocalDate, templateId: String, count: Int)
    extends BaseBrakeBatch

object BrakeBatchDetails {
  implicit val brakeBatchDetailsFormat: OFormat[BrakeBatchDetails] = Json.format[BrakeBatchDetails]
}

case class BrakeBatchMessage(
  subject: String,
  welshSubject: String,
  content: String,
  welshContent: String,
  externalRefId: String,
  messageType: String,
  issueDate: LocalDate,
  taxIdentifierName: String
)

object BrakeBatchMessage {
  implicit val brakeBatchMessageFormat: OFormat[BrakeBatchMessage] = Json.format[BrakeBatchMessage]

  def apply(m: SecureMessage): BrakeBatchMessage = {
    val englishContent = m.content.find(_.lang == English)
    val welshContent = m.content.find(_.lang == Welsh)
    BrakeBatchMessage(
      subject = englishContent.map(_.subject).getOrElse(""),
      welshSubject = welshContent.map(_.subject).getOrElse(""),
      content = contentEncoded(englishContent),
      welshContent = contentEncoded(welshContent),
      externalRefId = m.externalRef.id,
      messageType = m.messageType,
      issueDate = m.issueDate.toLocalDate,
      taxIdentifierName = m.recipient.identifier.name
    )
  }

  private val contentEncoded = { content: Option[Content] =>
    content.map(_.body).getOrElse("")
  }
}
