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

package uk.gov.hmrc.securemessage.services

import org.scalatest.{ FreeSpec, MustMatchers }
import uk.gov.hmrc.auth.core.{ EnrolmentIdentifier, Enrolments }
import uk.gov.hmrc.securemessage.controllers.model.common
import uk.gov.hmrc.securemessage.controllers.model.common.CustomerEnrolment

class ImplicitClassesExtensionsTest extends FreeSpec with MustMatchers with ImplicitClassesExtensions {

  "ConversationExtensions" - {}

  "EnrolmentsExtensions" - {
    "find" - {
      "returns a specific enrolment found within a list of enrolments designated by it's key and name" in {
        val expectedEnrolment = common.CustomerEnrolment("HMRC-CUS-ORG", "EORINumber", "GB123456789")
        val enrolments = Enrolments(
          Set(
            uk.gov.hmrc.auth.core.Enrolment(
              key = "HMRC-CUS-ORG",
              identifiers = Seq(EnrolmentIdentifier("EORINumber", "GB123456789")),
              state = "",
              None)))
        enrolments.find("HMRC-CUS-ORG", "EORINumber") mustBe Some(expectedEnrolment)
      }

      "returns a specific enrolment found within a list of enrolments designated by it's key and name in a case-insensitive manner" in {
        val expectedEnrolment = common.CustomerEnrolment("HMRC-CUS-ORG", "EORINumber", "GB123456789")
        val enrolments = Enrolments(
          Set(
            uk.gov.hmrc.auth.core.Enrolment(
              key = "HMRC-CUS-ORG",
              identifiers = Seq(EnrolmentIdentifier("EORINumber", "GB123456789")),
              state = "",
              None)))
        enrolments.find("hmrc-cUs-oRG", "eoriNumber") mustBe Some(expectedEnrolment)
      }

      "returns None when a specific enrolment cannot be found within a list of enrolments" in {
        val enrolments = Enrolments(
          Set(
            uk.gov.hmrc.auth.core.Enrolment(
              key = "HMRC-CUS-ORG",
              identifiers = Seq(EnrolmentIdentifier("Some other name", "some value")),
              state = "",
              None)))
        enrolments.find("HMRC-CUS-ORG", "EORINumber") mustBe None
      }
    }

    "filter" - {
      "returns a specific customer enrolment out of all the ones provided and ensures only the one available as an auth enrolment are returned" in {
        val authEnrolments = Enrolments(
          Set(
            uk.gov.hmrc.auth.core.Enrolment(
              key = "HMRC-CUS-ORG",
              identifiers = Seq(EnrolmentIdentifier("EORINumber", "GB1234567890")),
              state = "",
              None)))

        val enrolments = Some(
          List(
            CustomerEnrolment("HMRC-CUS-ORG", "EORINumber", "GB1234567890"),
            CustomerEnrolment("IR-SA", "UTR", "123456789"),
            CustomerEnrolment("IR-CT", "UTR", "345678901")
          ))

        authEnrolments.filter(None, enrolments) mustBe Set(
          CustomerEnrolment("HMRC-CUS-ORG", "EORINumber", "GB1234567890"))
      }

      "returns multiple customer enrolments for same enrolments with multiple identifiers provided and held in auth" in {
        val authEnrolments = Enrolments(
          Set(uk.gov.hmrc.auth.core.Enrolment(
            key = "HMRC-CUS-ORG",
            identifiers = Seq(
              EnrolmentIdentifier("EORINumber", "GB1234567890"),
              EnrolmentIdentifier("EORINumber", "GB1234567891"),
              EnrolmentIdentifier("EORINumber", "GB1234567892")
            ),
            state = "",
            None
          )))

        val enrolments = Some(
          List(
            CustomerEnrolment("HMRC-CUS-ORG", "EORINumber", "GB1234567890"),
            CustomerEnrolment("HMRC-CUS-ORG", "EORINumber", "GB1234567891"),
            CustomerEnrolment("HMRC-CUS-ORG", "EORINumber", "GB1234567892"),
            CustomerEnrolment("IR-SA", "UTR", "123456789"),
            CustomerEnrolment("IR-CT", "UTR", "345678901")
          ))

        authEnrolments.filter(None, enrolments) mustBe Set(
          CustomerEnrolment("HMRC-CUS-ORG", "EORINumber", "GB1234567890"),
          CustomerEnrolment("HMRC-CUS-ORG", "EORINumber", "GB1234567891"),
          CustomerEnrolment("HMRC-CUS-ORG", "EORINumber", "GB1234567892")
        )
      }

      "returns multiple customer enrolments based on enrolment keys when multiple identifiers are held in auth" in {
        val authEnrolments = Enrolments(
          Set(uk.gov.hmrc.auth.core.Enrolment(
            key = "HMRC-CUS-ORG",
            identifiers = Seq(
              EnrolmentIdentifier("EORINumber", "GB1234567890"),
              EnrolmentIdentifier("EORINumber", "GB1234567891"),
              EnrolmentIdentifier("EORINumber", "GB1234567892")
            ),
            state = "",
            None
          )))

        val enrolmentKeys = Some(List("HMRC-CUS-ORG"))
        authEnrolments.filter(enrolmentKeys, None) mustBe Set(
          CustomerEnrolment("HMRC-CUS-ORG", "EORINumber", "GB1234567890"),
          CustomerEnrolment("HMRC-CUS-ORG", "EORINumber", "GB1234567891"),
          CustomerEnrolment("HMRC-CUS-ORG", "EORINumber", "GB1234567892")
        )
      }

      "returns specific customer enrolments when provided with customer enrolments filters (no enrolment keys) and a specific set of auth enrolments" in {
        val authEnrolments = Enrolments(
          Set(
            uk.gov.hmrc.auth.core.Enrolment(
              key = "HMRC-CUS-ORG",
              identifiers = Seq(EnrolmentIdentifier("EORINumber", "GB1234567890")),
              state = "",
              None),
            uk.gov.hmrc.auth.core
              .Enrolment(key = "IR-CT", identifiers = Seq(EnrolmentIdentifier("UTR", "345678901")), state = "", None)
          ))

        val enrolments = Some(
          List(
            CustomerEnrolment("HMRC-CUS-ORG", "EORINumber", "GB1234567890"),
            CustomerEnrolment("IR-SA", "UTR", "123456789"),
            CustomerEnrolment("IR-CT", "UTR", "345678901")
          ))

        authEnrolments.filter(None, enrolments) mustBe
          Set(
            CustomerEnrolment("HMRC-CUS-ORG", "EORINumber", "GB1234567890"),
            CustomerEnrolment("IR-CT", "UTR", "345678901")
          )
      }

      "returns a specific enrolment out of all the enrolment keys provided and ensures only the one available as an auth enrolment are returned" in {
        val authEnrolments = Enrolments(
          Set(
            uk.gov.hmrc.auth.core.Enrolment(
              key = "HMRC-CUS-ORG",
              identifiers = Seq(EnrolmentIdentifier("EORINumber", "GB1234567890")),
              state = "",
              None)))

        val enrolmentKeys = Some(List("HMRC-CUS-ORG", "IR-SA", "IR-CT"))

        authEnrolments.filter(enrolmentKeys, None) mustBe Set(
          CustomerEnrolment("HMRC-CUS-ORG", "EORINumber", "GB1234567890"))
      }

      "returns another specific enrolment out of all the enrolment keys provided and ensures only the ones available as auth enrolments are returned" in {
        val authEnrolments = Enrolments(
          Set(
            uk.gov.hmrc.auth.core.Enrolment(
              key = "HMRC-CUS-ORG",
              identifiers = Seq(EnrolmentIdentifier("EORINumber", "GB1234567890")),
              state = "",
              None),
            uk.gov.hmrc.auth.core
              .Enrolment(key = "IR-SA", identifiers = Seq(EnrolmentIdentifier("UTR", "123456789")), state = "", None)
          ))

        val enrolmentKeys = Some(List("HMRC-CUS-ORG", "IR-SA", "IR-CT"))

        authEnrolments.filter(enrolmentKeys, None) mustBe Set(
          CustomerEnrolment("HMRC-CUS-ORG", "EORINumber", "GB1234567890"),
          CustomerEnrolment("IR-SA", "UTR", "123456789"))
      }

      "returns customer enrolments out of all the ones provided plus enrolment keys and ensures only the ones available as auth enrolments are returned" in {
        val authEnrolments = Enrolments(
          Set(
            uk.gov.hmrc.auth.core.Enrolment(
              key = "HMRC-CUS-ORG",
              identifiers = Seq(EnrolmentIdentifier("EORINumber", "GB1234567890")),
              state = "",
              None),
            uk.gov.hmrc.auth.core
              .Enrolment(key = "IR-CT", identifiers = Seq(EnrolmentIdentifier("UTR", "345678901")), state = "", None)
          ))

        val enrolmentKeys = Some(List("HMRC-CuS-ORG", "ir-SA", "IR-Ct"))
        val enrolments = Some(
          List(
            CustomerEnrolment("HMRC-CUS-ORG", "EORINumber", "GB1234567890"),
            CustomerEnrolment("ir-sa", "UTR", "123456789"),
            CustomerEnrolment("IR-CT", "UTR", "345678901")
          ))

        authEnrolments.filter(enrolmentKeys, enrolments) mustBe
          Set(
            CustomerEnrolment("HMRC-CUS-ORG", "EORINumber", "GB1234567890"),
            CustomerEnrolment("IR-CT", "UTR", "345678901")
          )
      }

      "returns specific customer enrolments out of all the ones available as auth enrolments when no filters are passed" in {
        val authEnrolments = Enrolments(
          Set(
            uk.gov.hmrc.auth.core.Enrolment(
              key = "HMRC-CUS-ORG",
              identifiers = Seq(EnrolmentIdentifier("EORINumber", "GB1234567890")),
              state = "",
              None),
            uk.gov.hmrc.auth.core
              .Enrolment(key = "IR-CT", identifiers = Seq(EnrolmentIdentifier("UTR", "345678901")), state = "", None)
          ))

        val expectedResult =
          Set(
            CustomerEnrolment("HMRC-CUS-ORG", "EORINumber", "GB1234567890"),
            CustomerEnrolment("IR-CT", "UTR", "345678901")
          )

        authEnrolments.filter(None, None) mustBe expectedResult
        authEnrolments.filter(Some(List()), None) mustBe expectedResult
        authEnrolments.filter(None, Some(List())) mustBe expectedResult
        authEnrolments.filter(Some(List()), Some(List())) mustBe expectedResult
      }
    }
  }
}
