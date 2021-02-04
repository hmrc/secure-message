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

package uk.gov.hmrc.securemessage.controllers.utils

import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.auth.core.{ EnrolmentIdentifier, Enrolments }
import uk.gov.hmrc.securemessage.controllers.models.generic
import uk.gov.hmrc.securemessage.controllers.utils.EnrolmentHelper.findEnrolment

class EnrolmentHelperSpec extends PlaySpec {

  "findEoriEnrolment" must {
    "returns a specific enrolment found within a list of enrolments designated by it's key and name" in {
      val expectedEnrolment = generic.Enrolment("HMRC-CUS-ORG", "EORINumber", "GB123456789")
      val enrolments = Enrolments(
        Set(
          uk.gov.hmrc.auth.core.Enrolment(
            key = "HMRC-CUS-ORG",
            identifiers = Seq(EnrolmentIdentifier("EORINumber", "GB123456789")),
            state = "",
            None)))
      findEnrolment(enrolments, "HMRC-CUS-ORG", "EORINumber") mustBe Some(expectedEnrolment)
    }

    "returns a specific enrolment found within a list of enrolments designated by it's key and name in a case-insensitive manner" in {
      val expectedEnrolment = generic.Enrolment("HMRC-CUS-ORG", "EORINumber", "GB123456789")
      val enrolments = Enrolments(
        Set(
          uk.gov.hmrc.auth.core.Enrolment(
            key = "HMRC-CUS-ORG",
            identifiers = Seq(EnrolmentIdentifier("EORINumber", "GB123456789")),
            state = "",
            None)))
      findEnrolment(enrolments, "hmrc-cUs-oRG", "eoriNumber") mustBe Some(expectedEnrolment)
    }

    "returns None when a specific enrolment cannot be found within a list of enrolments" in {
      val enrolments = Enrolments(
        Set(
          uk.gov.hmrc.auth.core.Enrolment(
            key = "HMRC-CUS-ORG",
            identifiers = Seq(EnrolmentIdentifier("Some other name", "some value")),
            state = "",
            None)))
      findEnrolment(enrolments, "HMRC-CUS-ORG", "EORINumber") mustBe None
    }
  }
}
