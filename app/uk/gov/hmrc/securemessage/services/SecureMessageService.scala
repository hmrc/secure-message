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

import com.google.inject.Inject
import uk.gov.hmrc.securemessage.controllers.models.generic.{ ApiConversation, ConversationDetails, Enrolment }
import uk.gov.hmrc.securemessage.models.core._
import uk.gov.hmrc.securemessage.repository.ConversationRepository

import scala.concurrent.{ ExecutionContext, Future }

@SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter"))
class SecureMessageService @Inject()(conversationRepository: ConversationRepository) {

  def getConversations(enrolment: Enrolment)(implicit ec: ExecutionContext): Future[List[ConversationDetails]] = {
    val enrolmentToIdentifier = Identifier(enrolment.name, enrolment.value, Some(enrolment.key))
    conversationRepository.getConversations(enrolment).map { coreConversations =>
      coreConversations.map(conversations =>
        ConversationDetails.coreToConversationDetails(conversations, enrolmentToIdentifier))
    }
  }

  def getConversation(client: String, conversationId: String, enrolment: Enrolment)(
    implicit ec: ExecutionContext): Future[Option[ApiConversation]] = {
    val enrolmentToIdentifier = Identifier(enrolment.name, enrolment.value, Some(enrolment.key))
    conversationRepository.getConversation(client, conversationId, enrolment).map {
      case Some(conversation) =>
        Some(ApiConversation.coreConversationToApiConversation(conversation, enrolmentToIdentifier))
      case _ => None
    }
  }
}
