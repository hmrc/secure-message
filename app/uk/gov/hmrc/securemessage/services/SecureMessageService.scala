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

import java.text.ParseException

import cats.data.{ NonEmptyList, _ }
import cats.implicits._
import cats.kernel.Eq
import com.google.inject.Inject
import org.apache.commons.codec.binary.Base64
import org.joda.time.DateTime
import play.api.i18n.Messages
import uk.gov.hmrc.auth.core.{ AuthorisationException, Enrolments }
import uk.gov.hmrc.emailaddress.EmailAddress
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.securemessage.connectors.{ ChannelPreferencesConnector, EmailConnector }
import uk.gov.hmrc.securemessage.controllers.models.generic.CaseworkerMessageRequest.SystemIdentifier
import uk.gov.hmrc.securemessage.controllers.models.generic._
import uk.gov.hmrc.securemessage.models.EmailRequest
import uk.gov.hmrc.securemessage.models.core.ParticipantType.Customer.eqCustomer
import uk.gov.hmrc.securemessage.models.core.ParticipantType.{ Customer => PCustomer }
import uk.gov.hmrc.securemessage.models.core._
import uk.gov.hmrc.securemessage.repository.ConversationRepository
import uk.gov.hmrc.securemessage.services.utils.HtmlValidator
import uk.gov.hmrc.securemessage._

import scala.concurrent.{ ExecutionContext, Future }

@SuppressWarnings(
  Array(
    "org.wartremover.warts.ImplicitParameter",
    "org.wartremover.warts.Product",
    "org.wartremover.warts.Serializable"))
class SecureMessageService @Inject()(
  repo: ConversationRepository,
  emailConnector: EmailConnector,
  channelPrefConnector: ChannelPreferencesConnector) {

  def createConversation(conversation: Conversation)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[Either[SecureMessageError, Boolean]] =
    (for {
      _                     <- EitherT(messageContentCheck(conversation.messages.head.content))
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

  def getConversation(client: String, conversationId: String, enrolment: CustomerEnrolment)(
    implicit ec: ExecutionContext): Future[Option[ApiConversation]] = {
    val enrolmentToIdentifier = Identifier(enrolment.name, enrolment.value, Some(enrolment.key))
    repo.getConversation(client, conversationId, enrolment).map {
      case Some(conversation) =>
        Some(ApiConversation.coreConversationToApiConversation(conversation, enrolmentToIdentifier))
      case _ => None
    }
  }

  def addCaseWorkerMessageToConversation(
    client: String,
    conversationId: String,
    messagesRequest: CaseworkerMessageRequest)(
    implicit ec: ExecutionContext,
    hc: HeaderCarrier): Future[Either[SecureMessageError, Unit]] =
    repo.conversationExists(client, conversationId).flatMap { exists =>
      if (exists) {
        messageContentCheck(messagesRequest.content).flatMap {
          case Right(true) =>
            getCaseworkerParticipant(client, conversationId, messagesRequest.sender.system.identifier).flatMap {
              case Some(participant) =>
                val message =
                  Message(participant.id, new DateTime(), messagesRequest.content, isForwarded = Some(false))
                createMessageAndSendEmail(client, conversationId, message, participant)
              case _ => Future.successful(Left(NoCaseworkerIdFound("Caseworker ID not found")))
            }
          case Left(value) => Future.successful(Left(value))
          case _           => Future.successful(Left(StoreError("Message not created", None)))
        }
      } else {
        Future.successful(Left(ConversationIdNotFound("Conversation ID not known")))
      }
    }

  def addCustomerMessageToConversation(
    client: String,
    conversationId: String,
    messagesRequest: CustomerMessageRequest,
    enrolments: Enrolments)(implicit ec: ExecutionContext): Future[Unit] =
    repo.conversationExists(client, conversationId).flatMap { exists =>
      if (exists) {
        getParticipant(client, conversationId, enrolments).flatMap {
          case Some(participant) =>
            val message =
              Message(participant.id, new DateTime(), messagesRequest.content, isForwarded = Some(false))
            repo.addMessageToConversation(client, conversationId, message).flatMap {
              case true => Future(())
              case _    => Future.failed[Unit](new ParseException("Message not created", 0))
            }
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
    getParticipant(client, conversationId, enrolments).flatMap {
      case Some(participant) =>
        repo.updateConversationWithReadTime(client, conversationId, participant.id, readTime)
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

  private def getParticipant(client: String, conversationId: String, enrolments: Enrolments)(
    implicit ec: ExecutionContext): Future[Option[Participant]] =
    repo.getConversationParticipants(client, conversationId).map {
      case Some(participants) =>
        val filteredParticipants = enrolmentMaybeParticpant(enrolments, participants).map(identifier =>
          participants.participants.filter(p => p.identifier.equals(identifier)))
        filteredParticipants.flatMap(x => x.headOption)
      case _ => None
    }

  private def getCaseworkerParticipant(client: String, conversationId: String, caseworkerIdentifier: SystemIdentifier)(
    implicit ec: ExecutionContext): Future[Option[Participant]] = {
    val identifier = Identifier(caseworkerIdentifier.name, caseworkerIdentifier.value, None)
    repo.getConversationParticipants(client, conversationId).map {
      case Some(participants) =>
        participants.participants.filter(p => p.identifier.equals(identifier)).headOption.map { participant =>
          participant
        }
      case _ => None
    }
  }

  private def messageContentCheck(content: String): Future[Either[SecureMessageError, Boolean]] =
    if (Base64.isBase64(content)) {
      if (HtmlValidator.isValidHtml(content)) {
        Future.successful(Right[SecureMessageError, Boolean](true))
      } else {
        Future.successful(Left[SecureMessageError, Boolean](InvalidHtmlContent("Not valid html content")))
      }
    } else {
      Future.successful(Left[SecureMessageError, Boolean](InvalidBase64Content("Not valid base64 content")))
    }

  private def createMessageAndSendEmail(
    client: String,
    conversationId: String,
    messagesRequest: Message,
    participant: Participant)(
    implicit ec: ExecutionContext,
    headerCarrier: HeaderCarrier): Future[Either[SecureMessageError, Unit]] =
    repo.getConversationParticipants(client, conversationId).flatMap {
      case Some(participants) =>
        repo.addMessageToConversation(client, conversationId, messagesRequest).flatMap {
          case true =>
            getEmailRequestForMessage(participants, participant) match {
              case Left(error) => Future.successful(Left(error))
              case Right(emailRequest) =>
                emailConnector.send(emailRequest).flatMap {
                  case Left(error) => Future.successful(Left(error))
                  case Right(_)    => Future.successful(Right(()))
                }
            }
          case _ => Future.successful(Left(StoreError("Message not create", None)))
        }
      case _ => Future.successful(Left(StoreError("Message not create", None)))
    }

  private def getEmailRequestForMessage(
    participants: Participants,
    participant: Participant): Either[SecureMessageError, EmailRequest] = {
    implicit val eqParticipant: Eq[Participant] = Eq.fromUniversalEquals
    val emailAddresses: List[EmailAddress] =
      participants.participants.toList
        .filter(_ =!= participant)
        .flatMap(p => p.email.toList)
    if (emailAddresses.nonEmpty) {
      Right[SecureMessageError, EmailRequest](EmailRequest(emailAddresses, "cdsTestTemplate"))
    } else {
      Left[SecureMessageError, EmailRequest](NoReceiverEmailError("No Verified email address could not be found"))
    }
  }

  case class GroupedParticipants(system: Seq[Participant], customer: CustomerParticipants) {
    def all: Seq[Participant] = system ++ customer.all
  }
  case class CustomerParticipants(success: Seq[Participant], failure: Seq[CustomerEmailError]) {
    def all: Seq[Participant] = success ++ failure.map(_.customer)
  }
  case class CustomerEmailError(customer: Participant, error: EmailLookupError)

}
