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

package uk.gov.hmrc.securemessage.services.utils

import java.time.LocalDate
import org.mongodb.scala.bson.ObjectId
import uk.gov.hmrc.common.message.model._
import uk.gov.hmrc.domain._
import uk.gov.hmrc.securemessage.models.v4.SecureMessage

object SecureMessageFixtures {

  val utr = "1234567890"
  val form = "SA300"

  def messageForSA(
    utr: String = utr,
    messageType: String = "print-suppression-notification",
    validFrom: LocalDate = testDate(0),
    form: String = "SA300",
    hash: String = "someHashValue",
    alertTemplateId: String = "templateId",
    recipientName: Option[TaxpayerName] = None,
    externalRef: ExternalRef = ExternalRef("2342342341", "gmc")
  ) =
    SecureMessage(
      _id = new ObjectId,
      externalRef,
      recipient = MessageFixtures.createTaxEntity(SaUtr(utr)),
      None,
      messageType,
      validFrom = validFrom,
      List(),
      alertDetails = AlertDetails(alertTemplateId, recipientName, Map()),
      alertQueue = None,
      Some(MessageDetails(formId = form, None, None, None, None, None, None)),
      "testemail@email.com",
      hash = hash
    )

  def testDate(plusDays: Int) = LocalDate.of(2013, 6, 1).plusDays(plusDays.toLong)
}
