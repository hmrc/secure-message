/*
 * Copyright 2022 HM Revenue & Customs
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

import uk.gov.hmrc.auth.core.Enrolments
import uk.gov.hmrc.securemessage.models.core.{ CustomerEnrolment, Identifier }

trait ImplicitClassesExtensions {
  implicit class EnrolmentsExtensions(enrolments: Enrolments) {
    def asIdentifiers: Set[Identifier] =
      for {
        enr <- enrolments.enrolments
        id  <- enr.identifiers
      } yield Identifier(id.key, id.value, Some(enr.key))

    def asCustomerEnrolments: Set[CustomerEnrolment] =
      for {
        enr <- enrolments.enrolments
        id  <- enr.identifiers
      } yield CustomerEnrolment(enr.key, id.key, id.value)

    def find(enrolmentKey: String, enrolmentName: String): Option[CustomerEnrolment] =
      for {
        eoriEnrolment       <- enrolments.getEnrolment(enrolmentKey)
        enrolmentIdentifier <- eoriEnrolment.getIdentifier(enrolmentName)
      } yield CustomerEnrolment(eoriEnrolment.key, enrolmentIdentifier.key, enrolmentIdentifier.value)

    def filter(enrolmentKeys: Set[String], customerEnrolments: Set[CustomerEnrolment]): Set[CustomerEnrolment] = {
      val originalEnrolments: Set[CustomerEnrolment] = enrolments.asCustomerEnrolments

      def enrolmentKeysFiltered: Set[CustomerEnrolment] =
        originalEnrolments.filter(oe => enrolmentKeys.exists(ek => ek.equalsIgnoreCase(oe.key)))

      def customerEnrolmentsFiltered: Set[CustomerEnrolment] =
        originalEnrolments.filter(or => customerEnrolments.exists(ce => ce.upper == or.upper))

      (enrolmentKeys.isEmpty, customerEnrolments.isEmpty) match {
        case (true, true)   => originalEnrolments
        case (false, true)  => enrolmentKeysFiltered
        case (true, false)  => customerEnrolmentsFiltered
        case (false, false) => enrolmentKeysFiltered ++ customerEnrolmentsFiltered
      }
    }
  }
}
