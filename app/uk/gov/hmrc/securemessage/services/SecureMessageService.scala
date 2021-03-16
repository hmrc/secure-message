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

import java.util.UUID

import cats.data.{ NonEmptyList, _ }
import cats.implicits._
import com.google.inject.Inject
import javax.naming.CommunicationException
import org.joda.time.DateTime
import play.api.i18n.Messages
import play.api.mvc.Request
import uk.gov.hmrc.auth.core.{ AuthorisationException, Enrolments }
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.securemessage.connectors.{ ChannelPreferencesConnector, EISConnector, EmailConnector }
import uk.gov.hmrc.securemessage.controllers.models.generic.CustomerMessageRequest.asQueryReponse
import uk.gov.hmrc.securemessage.controllers.models.generic._
import uk.gov.hmrc.securemessage.models.core.ParticipantType.Customer.eqCustomer
import uk.gov.hmrc.securemessage.models.core.ParticipantType.{ Customer => PCustomer }
import uk.gov.hmrc.securemessage.models.core._
import uk.gov.hmrc.securemessage.models.{ EmailRequest, QueryResponseWrapper }
import uk.gov.hmrc.securemessage.repository.ConversationRepository
import uk.gov.hmrc.securemessage.{ EmailLookupError, NoReceiverEmailError, SecureMessageError }

import scala.concurrent.{ ExecutionContext, Future }

@SuppressWarnings(
  Array(
    "org.wartremover.warts.ImplicitParameter",
    "org.wartremover.warts.Product",
    "org.wartremover.warts.Serializable"))
class SecureMessageService @Inject()(
  repo: ConversationRepository,
  emailConnector: EmailConnector,
  channelPrefConnector: ChannelPreferencesConnector,
  eisConnector: EISConnector) {

  def createConversation(conversation: Conversation)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[Either[SecureMessageError, Boolean]] =
    (for {
      participantsWithEmail <- addMissingEmails(conversation.participants)
      _                     <- EitherT(repo.insertIfUnique(conversation.copy(participants = participantsWithEmail.all.toList)))
      _ <- validateCustomerParticipants(participantsWithEmail)(
            errorCondition = participantsWithEmail.customer.success.isEmpty)
      _ <- sendEmail(participantsWithEmail.customer.success, conversation.alert.templateId)
      res <- validateCustomerParticipants(participantsWithEmail)(
              errorCondition = participantsWithEmail.customer.failure.nonEmpty)

    } yield res).value

  private def validateCustomerParticipants(participants: GroupedParticipants)(errorCondition: => Boolean)(
    implicit ec: ExecutionContext): EitherT[Future, SecureMessageError, Boolean] =
    EitherT(Future {
      if (errorCondition) {
        Left(NoReceiverEmailError(s"Email lookup failed for: ${participants.customer.failure}"))
      } else {
        Right(true)
      }
    })

  @SuppressWarnings(
    Array("org.wartremover.warts.Nothing", "org.wartremover.warts.Any", "org.wartremover.warts.Product"))
  private def sendEmail(customers: Seq[Participant], templateId: String)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): EitherT[Future, SecureMessageError, Int] =
    for {
      emails <- EitherT.right[SecureMessageError](Future { customers.flatMap(_.email).toList })
      res    <- EitherT(emailConnector.send(EmailRequest(emails, templateId))).leftWiden[SecureMessageError]
    } yield res

  private def addMissingEmails(participants: Seq[Participant])(
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

  private def lookupEmail(noEmailCustomers: Seq[Participant])(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[Seq[Either[CustomerEmailError, Participant]]] =
    Future.sequence(noEmailCustomers.map(customerParticipant =>
      channelPrefConnector.getEmailForEnrolment(customerParticipant.identifier).map {
        case Right(email) => Right(customerParticipant.copy(email = Some(email)))
        case Left(elr)    => Left(CustomerEmailError(customerParticipant, elr))
    }))

  def getConversationsFiltered(customerEnrolments: Set[CustomerEnrolment], tags: Option[List[Tag]])(
    implicit ec: ExecutionContext,
    messages: Messages): Future[List[ConversationMetadata]] =
    repo.getConversationsFiltered(customerEnrolments, tags).map { coreConversations =>
      coreConversations.map(conversation => {
        val enrolmentToIdentifiers = customerEnrolments.map(customerEnrolment =>
          Identifier(customerEnrolment.name, customerEnrolment.value, Some(customerEnrolment.key)))
        ConversationMetadata.coreToConversationMetadata(conversation, enrolmentToIdentifiers)
      })
    }

  def getConversation(client: String, conversationId: String, customerEnrolments: Set[CustomerEnrolment])(
    implicit ec: ExecutionContext): Future[Option[ApiConversation]] =
    repo.getConversation(client, conversationId, customerEnrolments).map {
      case Some(conversation) => {
        val enrolmentToIdentifiers = customerEnrolments.map(customerEnrolment =>
          Identifier(customerEnrolment.name, customerEnrolment.value, Some(customerEnrolment.key)))
        Some(ApiConversation.coreConversationToApiConversation(conversation, enrolmentToIdentifiers))
      }
      case _ => None
    }

  def addMessageToConversation(
    client: String,
    conversationId: String,
    messagesRequest: CustomerMessageRequest,
    enrolments: Enrolments)(implicit ec: ExecutionContext, request: Request[_]): Future[Unit] =
    repo.conversationExists(client, conversationId).flatMap { exists =>
      if (exists) {
        getParticipantId(client, conversationId, enrolments).flatMap {
          case Some(id) =>
            val requestId = request.headers.get("X-Request-ID").getOrElse(s"govuk-tax-${UUID.randomUUID()}")
            val queryResponse = asQueryReponse(requestId, conversationId, messagesRequest)
            eisConnector
              .forwardMessage(QueryResponseWrapper(queryResponse))
              .flatMap(success =>
                if (success) {
                  val message =
                    Message(id, new DateTime(), messagesRequest.content, isForwarded = Some(false))
                  repo.addMessageToConversation(client, conversationId, message)
                } else {
                  Future.failed[Unit](new CommunicationException("Failed to forward message to EIS"))
              })
          case _ => Future.failed[Unit](AuthorisationException.fromString("InsufficientEnrolments"))
        }
      } else {
        Future.failed[Unit](new IllegalArgumentException("Conversation ID not known"))
      }
    }

  private def enrolmentMaybeParticpant(enrolments: Enrolments, participants: Participants): Option[Identifier] = {
    val participantIdentifiers = getIdentifiersFromParticipants(participants).toList
    getIdentifiersFromEnrolments(enrolments).intersect(participantIdentifiers).headOption
  }

  def updateReadTime(client: String, conversationId: String, enrolments: Enrolments, readTime: DateTime)(
    implicit ec: ExecutionContext): Future[Boolean] =
    getParticipantId(client, conversationId, enrolments).flatMap {
      case Some(id) =>
        repo.updateConversationWithReadTime(client, conversationId, id, readTime)
      case _ => Future.successful(false)
    }

  private def getIdentifiersFromEnrolments(enrolments: Enrolments): List[Identifier] =
    enrolments.enrolments
      .flatMap(enr => {
        enr.identifiers.map { id =>
          Identifier(id.key, id.value, Some(enr.key))
        }
      })
      .toList

  private def getIdentifiersFromParticipants(participants: Participants): NonEmptyList[Identifier] =
    participants.participants.map(p1 => p1.identifier)

  private def getParticipantId(client: String, conversationId: String, enrolments: Enrolments)(
    implicit ec: ExecutionContext): Future[Option[Int]] =
    repo.getConversationParticipants(client, conversationId).map {
      case Some(participants) =>
        val filteredParticipants = enrolmentMaybeParticpant(enrolments, participants).map(identifier =>
          participants.participants.filter(p => p.identifier.equals(identifier)))
        val firstMatching = filteredParticipants.flatMap(x => x.headOption)
        firstMatching.map(f => f.id)
      case _ => None
    }

  case class GroupedParticipants(system: Seq[Participant], customer: CustomerParticipants) {
    def all: Seq[Participant] = system ++ customer.all
  }
  case class CustomerParticipants(success: Seq[Participant], failure: Seq[CustomerEmailError]) {
    def all: Seq[Participant] = success ++ failure.map(_.customer)
  }
  case class CustomerEmailError(customer: Participant, error: EmailLookupError)
}
