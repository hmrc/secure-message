/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.securemessage

import org.bson.types.ObjectId
import uk.gov.hmrc.common.message.emailaddress.EmailAddress
import uk.gov.hmrc.common.message.model.{ Adviser, Details, EmailAlert, Regime, RenderUrl, TaxEntity }
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.securemessage.models.Tags
import uk.gov.hmrc.securemessage.models.core.Identifier

import java.time.{ Instant, LocalDate }

object TestData {
  val TEST_IDENTIFIER_NAME = "test_name"
  val TEST_IDENTIFIER_VALUE = "test_value"
  val TEST_IDENTIFIER_ENROLMENT = "HMRC-CUS-ORG"
  val TEST_IDENTIFIER: Identifier =
    Identifier(TEST_IDENTIFIER_NAME, TEST_IDENTIFIER_VALUE, Some(TEST_IDENTIFIER_ENROLMENT))

  val TEST_FORM = "test_form"
  val TEST_TYPE = "test_type"
  val TEST_DATE_STRING = "2025-12-20"
  val TEST_ID = "test_id"
  val TEST_PID_ID = "1234567"
  val TEST_BATCH_ID = "87912345"
  val TEST_REGIME = "test_regime"
  val TEST_STATUS = "test_status"
  val TEST_CLIENT = "test_client"

  val TEST_YEAR = 2025
  val TEST_YEAR_2026 = 2026
  val TEST_MONTH = 12
  val TEST_MONTH_JAN = 1
  val TEST_DAY = 20
  val TEST_DATE: LocalDate = LocalDate.of(TEST_YEAR, TEST_MONTH, TEST_DAY)
  val TEST_DATE1: LocalDate = LocalDate.of(TEST_YEAR_2026, TEST_MONTH_JAN, 6)

  val EPOCH_MILLI_SECONDS = 789245L
  val TEST_TIME_INSTANT: Instant = Instant.ofEpochMilli(EPOCH_MILLI_SECONDS)

  val TEST_EMAIL_ADDRESS_VALUE = "test@test.com"
  val TEST_EMAIL_ADDRESS: EmailAddress = EmailAddress(TEST_EMAIL_ADDRESS_VALUE)

  val TEST_THREAD_ID = "adfg#1456hjftwer==+gj123"
  val TEST_ENQUIRY_TYPE = "test_enquiry"
  val TEST_ADVISER: Adviser = Adviser(TEST_PID_ID)
  val TEST_WAIT_TIME = "200"
  val TEST_TOPIC = "test_topic"
  val TEST_ENVELOPE_ID = "adfg#1456hjftwer=="
  val TEST_EXT_REF_ID = "adfg1278"
  val TEST_SUBJECT = "sub_test"
  val TEST_WELSH_SUBJECT = "Nodyn atgoffa i ffeilio ffurflen Hunanasesiad"
  val TEST_CONTENT = "adfg#1456hjftwer=="
  val TEST_WELSH_CONTENT = "Q3lubnd5cyAtIDQyNTQxMDEzODQxNzQ5MTcxNDE="
  val TEST_MSG_TYPE = "letter"
  val TEST_TEMPLATE_ID = "test_template_id"
  val TEST_TITLE = "test_title"

  val TEST_NAME = "test_name"
  val TEST_KEY = "test_key"
  val TEST_KEY_VALUE = "test_key_value"
  val TEST_HASH = "1456hjftwer+===####"

  val TEST_URL = "test@test.com"
  val TEST_SERVICE_NAME = "test_service"
  val TEST_SOURCE = "gmc"
  val TEST_MSG_ID = "1456hjftwer"
  val TEST_ENROLMENT_VALUE = "HMRC-ORG"

  val TEST_PARAMETERS: Map[String, String] = Map(TEST_IDENTIFIER_NAME -> TEST_IDENTIFIER_VALUE)
  val TEST_TAGS: Tags =
    Tags(messageId = Some(TEST_MSG_ID), source = Some(TEST_SOURCE), enrolment = Some(TEST_ENROLMENT_VALUE))

  val TEST_TAX_ENTITY: TaxEntity = TaxEntity(Regime.paye, Nino("SJ123456A"), Some(TEST_EMAIL_ADDRESS_VALUE))
  val TEST_DETAILS: Details =
    Details(
      form = Some(TEST_FORM),
      `type` = Some(TEST_TYPE),
      suppressedAt = Some("2026-01-05"),
      detailsId = Some(TEST_TEMPLATE_ID),
      enquiryType = Some("p800_D2"),
      issueDate = Some(TEST_DATE1)
    )

  val TEST_EMAIL_ALERT: EmailAlert = EmailAlert(
    emailAddress = Some(TEST_EMAIL_ADDRESS_VALUE),
    alertTime = TEST_TIME_INSTANT,
    success = true,
    failureReason = Some("server error")
  )

  val TEST_RENDER_URL: RenderUrl = RenderUrl(service = TEST_SERVICE_NAME, url = TEST_URL)

  val TEST_SAUTR = "1234567890"
  val TEST_NINO = "SJ123456A"
  val TEST_HMRC_MTD_ITSA_VALUE = "X99999999999"

  val EMPTY_STRING = ""
  val TEST_OBJECT_ID = ObjectId("adf145612345678656782456")
}
