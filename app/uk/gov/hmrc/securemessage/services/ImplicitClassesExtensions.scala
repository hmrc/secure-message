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

import uk.gov.hmrc.auth.core.Enrolments
import uk.gov.hmrc.securemessage.ParticipantNotFound
import uk.gov.hmrc.securemessage.controllers.model.common.CustomerEnrolment
import uk.gov.hmrc.securemessage.models.core.{ Conversation, Identifier, Participant }

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

    @SuppressWarnings(Array("org.wartremover.warts.Option2Iterable"))
    def filter(
      enrolmentKeys: Option[List[String]],
      customerEnrolments: Option[List[CustomerEnrolment]]): Set[CustomerEnrolment] = {
      val originalEnrolments: Set[CustomerEnrolment] = enrolments.asCustomerEnrolments
      val enrolmentKeysFilter: Set[String] = enrolmentKeys.toSet.flatten
      val customerEnrolmentsFilter: Set[CustomerEnrolment] = customerEnrolments.toSet.flatten
      val enrolmentKeysFiltered =
        if (enrolmentKeysFilter.isEmpty) {
          originalEnrolments
        } else {
          originalEnrolments.filter(e => enrolmentKeysFilter.contains(e.key))
        }
      val customerEnrolmentsFiltered =
        if (customerEnrolmentsFilter.isEmpty) {
          enrolmentKeysFiltered
        } else {
          enrolmentKeysFiltered.intersect(customerEnrolmentsFilter)
        }
      customerEnrolmentsFiltered
    }
  }

  implicit class ConversationExtensions(conversation: Conversation) {
    def participantWith(identifiers: Set[Identifier]): Either[ParticipantNotFound, Participant] =
      conversation.participants.find(p => identifiers.contains(p.identifier)) match {
        case Some(participant) => Right(participant)
        case None =>
          Left(ParticipantNotFound(
            s"No participant found for client: ${conversation.client}, conversationId: ${conversation.id}, indentifiers: $identifiers"))
      }
  }
}
