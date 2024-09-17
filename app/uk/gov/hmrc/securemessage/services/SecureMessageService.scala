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

package uk.gov.hmrc.securemessage.services

import org.mongodb.scala.bson.ObjectId
import play.api.i18n.Messages
import play.api.mvc.{ AnyContent, Request, Result }
import uk.gov.hmrc.auth.core.Enrolments
import uk.gov.hmrc.common.message.model.MessagesCount
import uk.gov.hmrc.domain.TaxIds.TaxIdWithName
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.securemessage.SecureMessageError
import uk.gov.hmrc.securemessage.controllers.model.cdcm.read.{ ApiConversation, ConversationMetadata }
import uk.gov.hmrc.securemessage.controllers.model.cdcm.write.CaseworkerMessage
import uk.gov.hmrc.securemessage.controllers.model.cdsf.read.ApiLetter
import uk.gov.hmrc.securemessage.controllers.model.common.write.CustomerMessage
import uk.gov.hmrc.securemessage.models.core._
import uk.gov.hmrc.securemessage.models.core.{ Message => CDSMessage }
import uk.gov.hmrc.securemessage.models.v4.SecureMessage

import scala.concurrent.{ ExecutionContext, Future }

trait SecureMessageService {

  def createConversation(
    conversation: Conversation
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[SecureMessageError, Unit]]

  def createSecureMessage(
    secureMessage: SecureMessage
  )(implicit request: Request[AnyContent], hc: HeaderCarrier, ec: ExecutionContext): Future[Result]

  def getConversations(authEnrolments: Enrolments, filters: Filters)(implicit
    ec: ExecutionContext,
    messages: Messages
  ): Future[List[ConversationMetadata]]

  def getMessages(authEnrolments: Enrolments, filters: Filters)(implicit ec: ExecutionContext): Future[List[CDSMessage]]

  def getMessagesList(
    authTaxIds: Set[TaxIdWithName]
  )(implicit ec: ExecutionContext, hc: HeaderCarrier, messageFilter: MessageFilter): Future[List[Message]]

  def getMessagesCount(authEnrolments: Enrolments, filters: Filters)(implicit
    ec: ExecutionContext
  ): Future[MessagesCount]

  def getMessagesCount(
    authTaxIds: Set[TaxIdWithName]
  )(implicit ec: ExecutionContext, hc: HeaderCarrier, messageFilter: MessageFilter): Future[MessagesCount]

  def getConversation(id: ObjectId, enrolments: Set[CustomerEnrolment])(implicit
    ec: ExecutionContext
  ): Future[Either[SecureMessageError, ApiConversation]]

  def getLetter(id: ObjectId, enrolments: Set[CustomerEnrolment])(implicit
    ec: ExecutionContext
  ): Future[Either[SecureMessageError, ApiLetter]]

  def getLetter(id: ObjectId)(implicit ec: ExecutionContext): Future[Option[Letter]]

  def getSecureMessage(id: ObjectId, enrolments: Set[CustomerEnrolment])(implicit
    ec: ExecutionContext,
    language: Language
  ): Future[Either[SecureMessageError, ApiLetter]]

  def getSecureMessage(id: ObjectId)(implicit ec: ExecutionContext): Future[Option[SecureMessage]]

  def addCaseWorkerMessageToConversation(
    client: String,
    conversationId: String,
    messagesRequest: CaseworkerMessage,
    randomId: String,
    maybeReference: Option[Reference]
  )(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Either[SecureMessageError, Unit]]

  def addCustomerMessage(
    id: String,
    messagesRequest: CustomerMessage,
    enrolments: Enrolments,
    randomId: String,
    reference: Option[Reference]
  )(implicit ec: ExecutionContext, request: Request[_]): Future[Either[SecureMessageError, Unit]]

  def getUserMessage(identifier: Set[Identifier])(implicit executionContext: ExecutionContext): Future[List[Letter]]

}
