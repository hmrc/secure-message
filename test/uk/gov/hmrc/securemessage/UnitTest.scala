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

import org.apache.commons.codec.binary.Base64
import org.joda.time.DateTime
import uk.gov.hmrc.auth.core.{ Enrolment, EnrolmentIdentifier, Enrolments }
import uk.gov.hmrc.securemessage.models.core.CustomerEnrolment
import uk.gov.hmrc.securemessage.utils.DateTimeUtils


/** This will be the base class for all our unit tests, replacing PlaySpec and all extended traits for consistency
  * */
trait UnitTest {

  val zeroTimeProvider: ZeroTimeProvider = new ZeroTimeProvider()
  val now: DateTime = zeroTimeProvider.now

  class ZeroTimeProvider extends DateTimeUtils {
    override def now: DateTime = new DateTime(0)
  }

  def authEnrolmentsFrom(customerEnrolments: Set[CustomerEnrolment]): Enrolments =
    Enrolments(
      customerEnrolments.map(
        enrolment =>
          Enrolment(
            key = enrolment.key,
            identifiers = Seq(EnrolmentIdentifier(enrolment.name, enrolment.value)),
            state = "",
            None))
    )

  def base64Encode(path: String): String = Base64.encodeBase64String(path.getBytes("UTF-8"))
}
