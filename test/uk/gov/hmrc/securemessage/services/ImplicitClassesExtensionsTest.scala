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
import uk.gov.hmrc.securemessage.models.core
import uk.gov.hmrc.securemessage.models.core.CustomerEnrolment

class ImplicitClassesExtensionsTest extends FreeSpec with MustMatchers with ImplicitClassesExtensions {

  "ConversationExtensions" - {}

  "EnrolmentsExtensions" - {
    val hmrcCusOrg = "HMRC-CUS-ORG"
    val eORINumber = "EORINumber"
    val eori89 = "GB123456789"

    "find" - {
      "returns a specific enrolment found within a list of enrolments designated by it's key and name" in {
        val expectedEnrolment = core.CustomerEnrolment(hmrcCusOrg, eORINumber, eori89)
        val enrolments = Enrolments(
          Set(uk.gov.hmrc.auth.core
            .Enrolment(key = hmrcCusOrg, identifiers = Seq(EnrolmentIdentifier(eORINumber, eori89)), state = "", None)))
        enrolments.find(hmrcCusOrg, eORINumber) mustBe Some(expectedEnrolment)
      }

      "returns a specific enrolment found within a list of enrolments designated by it's key and name in a case insensitive manner" in {
        val expectedEnrolment = core.CustomerEnrolment(hmrcCusOrg, eORINumber, eori89)
        val enrolments = Enrolments(
          Set(uk.gov.hmrc.auth.core
            .Enrolment(key = hmrcCusOrg, identifiers = Seq(EnrolmentIdentifier(eORINumber, eori89)), state = "", None)))
        enrolments.find("hmrc-cUs-oRG", "eoriNumber") mustBe Some(expectedEnrolment)
      }

      "returns None when a specific enrolment cannot be found within a list of enrolments" in {
        val enrolments = Enrolments(
          Set(
            uk.gov.hmrc.auth.core.Enrolment(
              key = hmrcCusOrg,
              identifiers = Seq(EnrolmentIdentifier("Some other name", "some value")),
              state = "",
              None)))
        enrolments.find(hmrcCusOrg, eORINumber) mustBe None
      }
    }

    "filter should" - {
      val eoriEnrolment = CustomerEnrolment(hmrcCusOrg, eORINumber, eori89 + "0")
      val eoriEnrolment1 = eoriEnrolment.copy(value = eori89 + "1")
      val eoriEnrolment2 = eoriEnrolment.copy(value = eori89 + "2")
      val saUtrEnrolment = CustomerEnrolment("IR-SA", "UTR", "123456789")
      val ctUtrEnrolment = CustomerEnrolment("IR-CT", "UTR", "345678901")
      val enrolmentIdentifier = EnrolmentIdentifier(eORINumber, eori89 + "0")
      val enrolmentIdentifier1 = EnrolmentIdentifier(eORINumber, eori89 + "1")
      val enrolmentIdentifier2 = EnrolmentIdentifier(eORINumber, eori89 + "2")

      "return a specific customer enrolment out of all the ones provided and ensures only the one available as an auth enrolment are returned" in {
        val authEnrolments = Enrolments(Set(
          uk.gov.hmrc.auth.core.Enrolment(key = hmrcCusOrg, identifiers = Seq(enrolmentIdentifier), state = "", None)))

        val enrolments = Set(
          eoriEnrolment,
          saUtrEnrolment,
          ctUtrEnrolment
        )

        authEnrolments.filter(Set(), enrolments) mustBe Set(eoriEnrolment)
      }

      "returns multiple customer enrolments for same enrolments with multiple identifiers provided and held in auth" in {
        val authEnrolments = Enrolments(
          Set(
            uk.gov.hmrc.auth.core.Enrolment(
              key = hmrcCusOrg,
              identifiers = Seq(
                enrolmentIdentifier,
                enrolmentIdentifier1,
                enrolmentIdentifier2
              ),
              state = "",
              None
            )))

        val enrolments = Set(
          eoriEnrolment,
          eoriEnrolment1,
          eoriEnrolment2,
          saUtrEnrolment,
          ctUtrEnrolment
        )

        authEnrolments.filter(Set(), enrolments) mustBe Set(
          eoriEnrolment,
          eoriEnrolment1,
          eoriEnrolment2
        )
      }

      "returns multiple customer enrolments based on enrolment keys when multiple identifiers are held in auth" in {
        val authEnrolments = Enrolments(
          Set(
            uk.gov.hmrc.auth.core.Enrolment(
              key = hmrcCusOrg,
              identifiers = Seq(
                enrolmentIdentifier,
                enrolmentIdentifier1,
                enrolmentIdentifier2
              ),
              state = "",
              None
            )))

        val enrolmentKeys = Set(hmrcCusOrg)
        authEnrolments.filter(enrolmentKeys, Set()) mustBe Set(
          eoriEnrolment,
          eoriEnrolment1,
          eoriEnrolment2
        )
      }

      val utrEnrolmentIdentifier = EnrolmentIdentifier("UTR", "345678901")
      "returns specific customer enrolments when provided with customer enrolments filters (no enrolment keys) and a specific set of auth enrolments" in {
        val authEnrolments = Enrolments(
          Set(
            uk.gov.hmrc.auth.core.Enrolment(key = hmrcCusOrg, identifiers = Seq(enrolmentIdentifier), state = "", None),
            uk.gov.hmrc.auth.core
              .Enrolment(key = "IR-CT", identifiers = Seq(utrEnrolmentIdentifier), state = "", None)
          ))

        val enrolments = Set(
          eoriEnrolment,
          saUtrEnrolment,
          ctUtrEnrolment
        )

        authEnrolments.filter(Set(), enrolments) mustBe
          Set(
            eoriEnrolment,
            ctUtrEnrolment
          )
      }

      "returns a specific enrolment out of all the enrolment keys provided and ensures only the one available as an auth enrolment are returned" in {
        val authEnrolments = Enrolments(Set(
          uk.gov.hmrc.auth.core.Enrolment(key = hmrcCusOrg, identifiers = Seq(enrolmentIdentifier), state = "", None)))

        val enrolmentKeys = Set(hmrcCusOrg, "IR-SA", "IR-CT")

        authEnrolments.filter(enrolmentKeys, Set()) mustBe Set(eoriEnrolment)
      }

      "returns another specific enrolment out of all the enrolment keys provided and ensures only the ones available as auth enrolments are returned" in {
        val authEnrolments = Enrolments(
          Set(
            uk.gov.hmrc.auth.core.Enrolment(key = hmrcCusOrg, identifiers = Seq(enrolmentIdentifier), state = "", None),
            uk.gov.hmrc.auth.core
              .Enrolment(key = "IR-SA", identifiers = Seq(EnrolmentIdentifier("UTR", "123456789")), state = "", None)
          ))

        val enrolmentKeys = Set(hmrcCusOrg, "IR-SA", "IR-CT")

        authEnrolments.filter(enrolmentKeys, Set()) mustBe Set(eoriEnrolment, saUtrEnrolment)
      }

      "returns customer enrolments out of all the ones provided plus enrolment keys and ensures only the ones available as auth enrolments are returned" in {
        val authEnrolments = Enrolments(
          Set(
            uk.gov.hmrc.auth.core.Enrolment(key = hmrcCusOrg, identifiers = Seq(enrolmentIdentifier), state = "", None),
            uk.gov.hmrc.auth.core
              .Enrolment(key = "IR-CT", identifiers = Seq(utrEnrolmentIdentifier), state = "", None)
          ))

        val enrolmentKeys = Set(hmrcCusOrg, "IR-SA", "IR-CT")
        val enrolments = Set(
          eoriEnrolment,
          CustomerEnrolment("IR-SA", "UTR", "123456789"),
          ctUtrEnrolment
        )

        authEnrolments.filter(enrolmentKeys, enrolments) mustBe
          Set(
            eoriEnrolment,
            ctUtrEnrolment
          )
      }

      "returns specific customer enrolments out of all the ones available as auth enrolments when no filters are passed" in {
        val authEnrolments = Enrolments(
          Set(
            uk.gov.hmrc.auth.core.Enrolment(key = hmrcCusOrg, identifiers = Seq(enrolmentIdentifier), state = "", None),
            uk.gov.hmrc.auth.core
              .Enrolment(key = "IR-CT", identifiers = Seq(utrEnrolmentIdentifier), state = "", None)
          ))

        val expectedResult =
          Set(
            eoriEnrolment,
            ctUtrEnrolment
          )

        authEnrolments.filter(Set(), Set()) mustBe expectedResult
      }
    }
  }
}
