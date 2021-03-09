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

import cats.data.{ NonEmptyList, _ }
import cats.implicits._
import com.google.inject.Inject
import org.joda.time.DateTime
import uk.gov.hmrc.auth.core.{ AuthorisationException, Enrolments }
import uk.gov.hmrc.emailaddress.EmailAddress
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.securemessage.{ NoReceiverEmailError, SecureMessageError }
import uk.gov.hmrc.securemessage.connectors.{ ChannelPreferencesConnector, EmailConnector }
import uk.gov.hmrc.securemessage.controllers.models.generic._
import uk.gov.hmrc.securemessage.models.EmailRequest
import uk.gov.hmrc.securemessage.models.core.ParticipantType.Customer.eqCustomer
import uk.gov.hmrc.securemessage.models.core.ParticipantType.{ Customer => PCustomer, System => PSystem }
import uk.gov.hmrc.securemessage.models.core._
import uk.gov.hmrc.securemessage.repository.ConversationRepository

import scala.concurrent.{ ExecutionContext, Future }

@SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter"))
class SecureMessageService @Inject()(
  repo: ConversationRepository,
  emailConnector: EmailConnector,
  channelPrefConnector: ChannelPreferencesConnector) {

  def createConversation(conversation: Conversation)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[Either[SecureMessageError, Int]] =
    (for {
      cnv <- EitherT.right[SecureMessageError](addMissingEmails(conversation))
      _   <- EitherT(repo.insertIfUnique(cnv))
      res <- sendEmail(cnv.participants, cnv.alert.templateId)
    } yield res).value

  val hasEmail: Participant => Boolean = p =>
    p.participantType === PSystem || (p.participantType === PCustomer && p.email.isDefined)

  @SuppressWarnings(Array("org.wartremover.warts.Nothing", "org.wartremover.warts.Any"))
  private def sendEmail(customers: List[Participant], templateId: String)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): EitherT[Future, SecureMessageError, Int] = {
    val fEmails: EitherT[Future, SecureMessageError, List[EmailAddress]] = {
      val filteredEmails: List[List[EmailAddress]] =
        for (p <- customers if p.participantType === PCustomer && p.email.isDefined) yield p.email.toList
      val emails =
        filteredEmails match {
          case List() =>
            Left(NoReceiverEmailError("Verified email address could not be found"))
          case emails => Right(emails.flatten)
        }
      EitherT(Future.successful(emails))
    }
    for {
      emails <- fEmails
      res    <- EitherT(emailConnector.send(EmailRequest(emails, templateId))).leftWiden[SecureMessageError]
    } yield res
  }

  private def addMissingEmails(
    cnv: Conversation)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Conversation] =
    Future
      .sequence(cnv.participants.map(p => {
        if (p.participantType === PSystem || (p.participantType === PCustomer && p.email.isDefined)) {
          Future.successful(p)
        } else {
          channelPrefConnector
            .getEmailForEnrolment(p.identifier)
            .map(
              _.fold(_ => p, e => p.copy(email = Some(e)))
            )
        }
      }))
      .map(ps => cnv.copy(participants = ps))

  def getConversationsFiltered(customerEnrolments: Set[CustomerEnrolment], tags: Option[List[Tag]])(
    implicit ec: ExecutionContext): Future[List[ConversationMetadata]] =
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

  def addMessageToConversation(
    client: String,
    conversationId: String,
    messagesRequest: CustomerMessageRequest,
    enrolments: Enrolments)(implicit ec: ExecutionContext): Future[Unit] =
    repo.conversationExists(client, conversationId).flatMap { exists =>
      if (exists) {
        getParticipantId(client, conversationId, enrolments).flatMap {
          case Some(id) =>
            val message =
              Message(id, new DateTime(), messagesRequest.content, isForwarded = Some(false))
            repo.addMessageToConversation(client, conversationId, message)
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
}
