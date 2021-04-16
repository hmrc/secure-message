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

import java.util.Locale
import uk.gov.hmrc.auth.core.Enrolments
import cats.implicits._
import uk.gov.hmrc.securemessage.controllers.model.common.CustomerEnrolment

object EnrolmentHelper {

  def findEnrolment(enrolments: Enrolments, enrolmentKey: String, enrolmentName: String): Option[CustomerEnrolment] =
    enrolments.getEnrolment(enrolmentKey).flatMap { eoriEnrolment =>
      eoriEnrolment
        .getIdentifier(enrolmentName)
        .map(enrolmentIdentifier =>
          CustomerEnrolment(eoriEnrolment.key, enrolmentIdentifier.key, enrolmentIdentifier.value))
    }
  //TODO: move this in service within getConversationsFiltered
  @SuppressWarnings(Array("org.wartremover.warts.Option2Iterable"))
  def filterEnrolments(
    authEnrolments: Enrolments,
    enrolmentKeys: Option[List[String]],
    customerEnrolments: Option[List[CustomerEnrolment]]): Set[CustomerEnrolment] = {

    //authEnrolments.enrolments.intersect(customerEnrolments).filter(ce => enrolmentKeys.contains(ce.key))

    val enrolmentsFromKeys =
      authEnrolments.enrolments.filter(e =>
        enrolmentKeys.getOrElse(List()).map(_.toUpperCase(Locale.ENGLISH)).contains(e.key.toUpperCase(Locale.ENGLISH)))
    val enrolmentsFromCustomerEnrolments = authEnrolments.enrolments.filter(
      ae =>
        customerEnrolments
          .getOrElse(List())
          .exists(
            ce =>
              (ae.key.toUpperCase(Locale.ENGLISH) === ce.key.toUpperCase(Locale.ENGLISH)) &&
                (ae.identifiers.exists(i =>
                  (i.key.toUpperCase(Locale.ENGLISH) === ce.name.toUpperCase(Locale.ENGLISH)) &&
                    (i.value.toUpperCase(Locale.ENGLISH) === ce.value.toUpperCase(Locale.ENGLISH))))))

    val newCustomerEnrolments = (enrolmentKeys, customerEnrolments) match {
      case (None, None) | (Some(List()), None) | (None, Some(List())) | (Some(List()), Some(List())) =>
        authEnrolments.enrolments
      case _ => enrolmentsFromKeys union enrolmentsFromCustomerEnrolments
    }
    newCustomerEnrolments.flatMap(e => e.identifiers.map(i => CustomerEnrolment(e.key, i.key, i.value)))
  }
}
