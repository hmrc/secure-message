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
import uk.gov.hmrc.securemessage.models.core.CustomerEnrolment

class ImplicitClassesExtensionsTest
    extends FreeSpec with MustMatchers with ImplicitClassesExtensions with EnrolmentsTestData {

  "EnrolmentsExtensions" - {

    "find must" - {
      "return the enrolment by case insensitive key and name if found" in {
        authEnrolments(uppercase(utrEnrolments))
          .find(ctUtrEnrolment.key.toLowerCase, ctUtrEnrolment.name.toLowerCase) mustBe Some(ctUtrEnrolment)
      }

      "returns None if key not found" in {
        allAuthEnrolments.find("non-existing-key", allEnrolments.head.name) mustBe None
      }

      "returns None if name not found" in {
        allAuthEnrolments.find(allEnrolments.head.key, "non-existing-name") mustBe None
      }
    }

    "filter must" - {
      "return original auth enrolments when all filters are empty" in {
        allAuthEnrolments
          .filter(enrolmentKeys = Set(), customerEnrolments = Set()) must contain theSameElementsAs allEnrolments
      }

      "return just 'customerEnrolments' filtered auth enrolments when enrolmentKeys filter is empty" in {
        allAuthEnrolments
          .filter(enrolmentKeys = Set(), customerEnrolments = eoriEnrolments) must contain theSameElementsAs eoriEnrolments
      }

      "return just 'enrolmentKey' filtered auth enrolments when customerEnrolments filter is empty" in {
        allAuthEnrolments
          .filter(enrolmentKeys = Set(saUtrEnrolment.key), customerEnrolments = Set()) must contain theSameElementsAs Set(
          saUtrEnrolment)
      }

      "combine the filtering results when all case insensitive matched filters are non empty" in {
        authEnrolments(uppercase(allEnrolments))
          .filter(
            enrolmentKeys = Set(saUtrEnrolment.key.toLowerCase),
            customerEnrolments = lowercase(Set(ctUtrEnrolment))) must contain theSameElementsAs utrEnrolments
      }
    }
  }
}

trait EnrolmentsTestData {
  val hmrcCusOrg = "HMRC-CUS-ORG"
  val eORINumber = "EORINumber"
  val eori89 = "GB123456789"
  val eoriEnrolment: CustomerEnrolment = CustomerEnrolment(hmrcCusOrg, eORINumber, eori89 + "0")
  val eoriEnrolment1: CustomerEnrolment = eoriEnrolment.copy(value = eori89 + "1")
  val eoriEnrolment2: CustomerEnrolment = eoriEnrolment.copy(value = eori89 + "2")
  val eoriEnrolments: Set[CustomerEnrolment] = Set(eoriEnrolment, eoriEnrolment1, eoriEnrolment2)

  val saUtrEnrolment: CustomerEnrolment = CustomerEnrolment("IR-SA", "UTR", "123456789")
  val ctUtrEnrolment: CustomerEnrolment = CustomerEnrolment("IR-CT", "UTR", "345678901")
  val utrEnrolments = Set(saUtrEnrolment, ctUtrEnrolment)

  val allEnrolments: Set[CustomerEnrolment] = eoriEnrolments ++ utrEnrolments

  def uppercase(customerEnrolments: Set[CustomerEnrolment]): Set[CustomerEnrolment] =
    customerEnrolments.map(e => e.copy(key = e.key.toUpperCase, name = e.name.toUpperCase, value = e.value.toUpperCase))

  def lowercase(customerEnrolments: Set[CustomerEnrolment]): Set[CustomerEnrolment] =
    customerEnrolments.map(e => e.copy(key = e.key.toLowerCase, name = e.name.toLowerCase, value = e.value.toLowerCase))

  def authEnrolments(customerEnrolments: Set[CustomerEnrolment]): Enrolments = {
    require(customerEnrolments.nonEmpty, "Customer enrolments cannot be empty")
    Enrolments(
      customerEnrolments.map { ce =>
        val enrolmentIdentifier = EnrolmentIdentifier(ce.name, ce.value)
        uk.gov.hmrc.auth.core.Enrolment(key = ce.key, identifiers = Seq(enrolmentIdentifier), state = "", None)
      }
    )
  }
  val allAuthEnrolments: Enrolments = authEnrolments(allEnrolments)
}
