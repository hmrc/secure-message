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

import cats.data._
import cats.implicits._
import com.google.inject.Inject
import org.joda.time.DateTime
import play.api.i18n.Messages
import play.api.mvc.Request
import uk.gov.hmrc.auth.core.Enrolments
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.securemessage._
import uk.gov.hmrc.securemessage.connectors.{ ChannelPreferencesConnector, EISConnector, EmailConnector }
import uk.gov.hmrc.securemessage.controllers.model.cdcm.read.{ ApiConversation, ConversationMetadata }
import uk.gov.hmrc.securemessage.controllers.model.cdcm.write.CaseworkerMessage
import uk.gov.hmrc.securemessage.controllers.model.cdsf.read.ApiLetter
import uk.gov.hmrc.securemessage.controllers.model.common.write.CustomerMessage
import uk.gov.hmrc.securemessage.models.core.ParticipantType.Customer.eqCustomer
import uk.gov.hmrc.securemessage.models.core.ParticipantType.{ Customer => PCustomer }
import uk.gov.hmrc.securemessage.models.core.{ CustomerEnrolment, _ }
import uk.gov.hmrc.securemessage.models._
import uk.gov.hmrc.securemessage.repository.{ ConversationRepository, MessageRepository }
import uk.gov.hmrc.securemessage.services.utils.ContentValidator
import java.util.UUID
import scala.concurrent.{ ExecutionContext, Future }

//TODO: refactor service to only accept core model classes as params
@SuppressWarnings(
  Array(
    "org.wartremover.warts.ImplicitParameter",
    "org.wartremover.warts.Product",
    "org.wartremover.warts.Serializable"))
class SecureMessageService @Inject()(
  conversationRepository: ConversationRepository,
  messageRepository: MessageRepository,
  emailConnector: EmailConnector,
  channelPrefConnector: ChannelPreferencesConnector,
  eisConnector: EISConnector,
  override val auditConnector: AuditConnector)
    extends Auditing with ImplicitClassesExtensions {

  def createConversation(conversation: Conversation)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[Either[SecureMessageError, Unit]] = {
    for {
      _            <- ContentValidator.validate(conversation.messages.head.content)
      participants <- addMissingEmails(conversation.participants)
      _            <- EitherT(conversationRepository.insertIfUnique(conversation.copy(participants = participants.all)))
      _            <- sendAlert(participants.customer, conversation.alert)
    } yield ()
  }.value

  def getConversationsFiltered(authEnrolments: Enrolments, filters: ConversationFilters)(
    implicit ec: ExecutionContext,
    messages: Messages): Future[List[ConversationMetadata]] = {
    val filteredEnrolments = authEnrolments.filter(filters.enrolmentKeysFilter, filters.enrolmentsFilter)
    val identifiers: Set[Identifier] = filteredEnrolments.map(_.asIdentifier)
    conversationRepository.getConversationsFiltered(identifiers, filters.tags).map {
      _.map(ConversationMetadata.coreToConversationMetadata(_, identifiers)) //TODO: move this to controllers
        .sortBy(_.issueDate.getMillis)(Ordering[Long].reverse)
    }
  }

  def getConversation(client: String, conversationId: String, enrolments: Set[CustomerEnrolment])(
    implicit ec: ExecutionContext): Future[Either[ConversationNotFound, ApiConversation]] = {
    val identifiers = enrolments.map(_.asIdentifier)
    for {
      conversation <- EitherT(conversationRepository.getConversation(client, conversationId, identifiers))
    } yield ApiConversation.fromCore(conversation, identifiers)
  }.value

  def getConversation(id: String, enrolments: Set[CustomerEnrolment])(
    implicit ec: ExecutionContext): Future[Either[SecureMessageError, ApiConversation]] = {
    val identifiers = enrolments.map(_.asIdentifier)
    for {
      conversation <- EitherT(conversationRepository.getConversation(id, identifiers))
    } yield ApiConversation.fromCore(conversation, identifiers)
  }.value

  def getLetter(id: String, enrolments: Set[CustomerEnrolment])(
    implicit ec: ExecutionContext): Future[Either[SecureMessageError, ApiLetter]] = {
    val identifiers = enrolments.map(_.asIdentifier)
    for {
      letter <- EitherT(messageRepository.getLetter(id, identifiers))
    } yield ApiLetter.fromCore(letter)
  }.value

  def addCaseWorkerMessageToConversation(client: String, conversationId: String, messagesRequest: CaseworkerMessage)(
    implicit ec: ExecutionContext,
    hc: HeaderCarrier): Future[Either[SecureMessageError, Unit]] = {
    val senderIdentifier: Identifier = messagesRequest.senderIdentifier(client, conversationId)
    def message(sender: Participant) = Message(sender.id, new DateTime(), messagesRequest.content)
    for {
      _            <- ContentValidator.validate(messagesRequest.content)
      conversation <- EitherT(conversationRepository.getConversation(client, conversationId, Set(senderIdentifier)))
      sender       <- EitherT(Future(conversation.participantWith(Set(senderIdentifier))))
      participants <- addMissingEmails(conversation.participants)
      _            <- EitherT(conversationRepository.addMessageToConversation(client, conversationId, message(sender)))
      _            <- sendAlert(participants.customer, conversation.alert)
    } yield ()
  }.value

  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  def addCustomerMessageToConversation(
    client: String,
    conversationId: String,
    messagesRequest: CustomerMessage,
    enrolments: Enrolments)(
    implicit ec: ExecutionContext,
    request: Request[_]): Future[Either[SecureMessageError, Unit]] = {
    def message(sender: Participant) =
      Message(sender.id, new DateTime(), messagesRequest.content)
    val identifiers: Set[Identifier] = enrolments.asIdentifiers
    for {
      conversation <- EitherT(conversationRepository.getConversation(client, conversationId, identifiers))
      sender       <- EitherT(Future(conversation.participantWith(identifiers)))
      _            <- forwardMessage(conversationId, messagesRequest)
      _ <- EitherT(conversationRepository.addMessageToConversation(client, conversationId, message(sender)))
            .leftWiden[SecureMessageError]
    } yield ()
  }.value

  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  private def forwardMessage(conversationId: String, messagesRequest: CustomerMessage)(
    implicit ec: ExecutionContext,
    request: Request[_]): EitherT[Future, SecureMessageError, Unit] = {
    val ACKNOWLEDGEMENT_REFERENCE_MAX_LENGTH = 32
    val randomId = UUID.randomUUID().toString
    val correlationId = request.headers
      .get("X-Correlation-ID")
      .getOrElse(randomId)
      .replace("-", "")
      .substring(0, ACKNOWLEDGEMENT_REFERENCE_MAX_LENGTH - 1)
    val requestId = request.headers.get("X-Request-ID").getOrElse(s"govuk-tax-$randomId")
    val queryMessageWrapper = QueryMessageWrapper(
      QueryMessageRequest(
        requestCommon = RequestCommon(
          originatingSystem = "dc-secure-message",
          receiptDate = DateTime.now(),
          acknowledgementReference = correlationId
        ),
        requestDetail = messagesRequest.asRequestDetail(requestId, conversationId)
      ))

    EitherT(eisConnector.forwardMessage(queryMessageWrapper)).leftWiden[SecureMessageError]
  }

  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  def updateReadTime(client: String, conversationId: String, enrolments: Enrolments, readTime: DateTime)(
    implicit ec: ExecutionContext): Future[Either[SecureMessageError, Unit]] = {
    val identifiers: Set[Identifier] = enrolments.asIdentifiers
    for {
      conversation <- EitherT(conversationRepository.getConversation(client, conversationId, identifiers))
                       .leftWiden[SecureMessageError]
      reader <- EitherT(Future(conversation.participantWith(identifiers))).leftWiden[SecureMessageError]
      _ <- EitherT(conversationRepository.addReadTime(client, conversationId, reader.id, readTime))
            .leftWiden[SecureMessageError]
    } yield ()
  }.value

  private def addMissingEmails(participants: List[Participant])(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): EitherT[Future, SecureMessageError, GroupedParticipants] = {
    val (customers, systems) = participants.partition(_.participantType === PCustomer)
    val (noEmailCustomers, emailCustomers) = customers.partition(_.email.isEmpty)
    val result = for {
      customersWithEmail <- lookupEmail(noEmailCustomers)
      success = customersWithEmail.collect { case Right(v) => v }
      failure = customersWithEmail.collect { case Left(v)  => v }
    } yield GroupedParticipants(systems, CustomerParticipants(emailCustomers ++ success, failure))
    EitherT.right[SecureMessageError](result)
  }

  private def lookupEmail(noEmailCustomers: List[Participant])(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[List[Either[CustomerEmailError, Participant]]] =
    Future.sequence(noEmailCustomers.map(customerParticipant =>
      channelPrefConnector.getEmailForEnrolment(customerParticipant.identifier).map {
        case Right(email) =>
          val _ = auditRetrieveEmail(Some(email))
          Right(customerParticipant.copy(email = Some(email)))
        case Left(elr) =>
          val _ = auditRetrieveEmail(None)
          Left(CustomerEmailError(customerParticipant, elr))
    }))

  @SuppressWarnings(
    Array("org.wartremover.warts.Nothing", "org.wartremover.warts.Any", "org.wartremover.warts.Product"))
  private def sendAlert(receivers: CustomerParticipants, alert: core.Alert)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): EitherT[Future, SecureMessageError, Unit] = {
    def emailRequest =
      EmailRequest(receivers.success.flatMap(_.email), alert.templateId, alert.parameters.getOrElse(Map()))
    for {
      _ <- validateEmailReceivers(receivers)(errorCondition = receivers.success.isEmpty)
      _ <- EitherT(emailConnector.send(emailRequest)).leftWiden[SecureMessageError]
      _ <- validateEmailReceivers(receivers)(errorCondition = receivers.failure.nonEmpty)
    } yield ()
  }

  private def validateEmailReceivers(emailReceivers: CustomerParticipants)(errorCondition: => Boolean)(
    implicit ec: ExecutionContext): EitherT[Future, SecureMessageError, Unit] =
    EitherT(Future {
      if (errorCondition) {
        Left(NoReceiverEmailError(s"Email lookup failed for: $emailReceivers"))
      } else {
        Right(())
      }
    })

  case class GroupedParticipants(system: List[Participant], customer: CustomerParticipants) {
    def all: List[Participant] = system ++ customer.all
  }
  case class CustomerParticipants(success: List[Participant], failure: List[CustomerEmailError]) {
    def all: List[Participant] = success ++ failure.map(_.customer)
  }
  case class CustomerEmailError(customer: Participant, error: EmailLookupError)

}
