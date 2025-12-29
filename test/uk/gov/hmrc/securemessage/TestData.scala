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

package uk.gov.hmrc.securemessage

import uk.gov.hmrc.common.message.model.Adviser

import java.time.LocalDate

object TestData {
  val TEST_IDENTIFIER_NAME = "test_name"
  val TEST_IDENTIFIER_VALUE = "test_value"

  val TEST_FORM = "test_form"
  val TEST_TYPE = "test_type"
  val TEST_DATE_STRING = "2025-12-20"
  val TEST_ID = "test_id"
  val TEST_PID_ID = "1234567"
  val TEST_BATCH_ID = "87912345"

  val TEST_YEAR = 2025
  val TEST_MONTH = 12
  val TEST_DAY = 20
  val TEST_DATE: LocalDate = LocalDate.of(TEST_YEAR, TEST_MONTH, TEST_DAY)

  val TEST_EMAIL_ADDRESS_VALUE = "test@test.com"
  val TEST_THREAD_ID = "adfg#1456hjftwer==+gj123"
  val TEST_ENQUIRY_TYPE = "test_enquiry"
  val TEST_ADVISER: Adviser = Adviser(TEST_PID_ID)
  val TEST_WAIT_TIME = "200"
  val TEST_TOPIC = "test_topic"
  val TEST_ENVELOPE_ID = "adfg#1456hjftwer=="

  val TEST_KEY = "test_key"
  val TEST_KEY_VALUE = "test_key_value"
}
