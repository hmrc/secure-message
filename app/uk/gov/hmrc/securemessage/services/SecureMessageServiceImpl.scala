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

import cats.data._
import cats.implicits._
import com.google.inject.Inject
import org.apache.commons.codec.binary.Base64
import org.joda.time.{ DateTime, LocalDate }
import org.joda.time.format.DateTimeFormat
import org.mongodb.scala.bson.ObjectId
import play.api.i18n.Messages
import play.api.mvc.{ AnyContent, Request, Result }
import uk.gov.hmrc.auth.core.Enrolments
import uk.gov.hmrc.common.message.model.MessagesCount
import uk.gov.hmrc.domain.TaxIds.TaxIdWithName
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.securemessage._
import uk.gov.hmrc.securemessage.connectors.{ ChannelPreferencesConnector, EISConnector, EmailConnector, MessageConnector }
import uk.gov.hmrc.securemessage.controllers.model.cdcm.read.{ ApiConversation, ConversationMetadata }
import uk.gov.hmrc.securemessage.controllers.model.cdcm.write.CaseworkerMessage
import uk.gov.hmrc.securemessage.controllers.model.cdsf.read.ApiLetter
import uk.gov.hmrc.securemessage.controllers.model.common.read.MessageMetadata
import uk.gov.hmrc.securemessage.controllers.model.common.write.CustomerMessage
import uk.gov.hmrc.securemessage.controllers.{ Auditing, SecureMessageUtil }
import uk.gov.hmrc.securemessage.models._
import uk.gov.hmrc.securemessage.models.core.Language.{ English, Welsh }
import uk.gov.hmrc.securemessage.models.core.ParticipantType.Customer.eqCustomer
import uk.gov.hmrc.securemessage.models.core.ParticipantType.{ Customer => PCustomer }
import uk.gov.hmrc.securemessage.models.core.{ CustomerEnrolment, _ }
import uk.gov.hmrc.securemessage.models.v4.{ Content, SecureMessage }
import uk.gov.hmrc.securemessage.repository.{ ConversationRepository, MessageRepository }
import uk.gov.hmrc.securemessage.services.utils.ContentValidator

import java.util.UUID
import javax.inject.Singleton
import scala.concurrent.{ ExecutionContext, Future }
import scala.xml.XML

//TODO: refactor service to only accept core model classes as params
@Singleton
class SecureMessageServiceImpl @Inject()(
  conversationRepository: ConversationRepository,
  messageRepository: MessageRepository,
  secureMessageUtil: SecureMessageUtil,
  emailConnector: EmailConnector,
  messageConnector: MessageConnector,
  channelPrefConnector: ChannelPreferencesConnector,
  eisConnector: EISConnector,
  override val auditConnector: AuditConnector)
    extends SecureMessageService with Auditing with ImplicitClassesExtensions with OrderingDefinitions {

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

  def createSecureMessage(secureMessage: SecureMessage)(
    implicit request: Request[AnyContent],
    hc: HeaderCarrier,
    ec: ExecutionContext): Future[Result] =
    secureMessageUtil.validateAndCreateMessage(secureMessage)

  def findSecureMessageById(id: ObjectId): Future[Option[SecureMessage]] =
    secureMessageUtil.findById(id)

  def getConversations(authEnrolments: Enrolments, filters: Filters)(
    implicit ec: ExecutionContext,
    messages: Messages): Future[List[ConversationMetadata]] = {
    val filteredEnrolments = authEnrolments.filter(filters.enrolmentKeysFilter, filters.enrolmentsFilter)
    val identifiers: Set[Identifier] = filteredEnrolments.map(_.asIdentifier)
    conversationRepository.getConversations(identifiers, filters.tags).map {
      _.map(ConversationMetadata.coreToConversationMetadata(_, identifiers)) //TODO: move this to controllers
        .sortBy(_.issueDate.getMillis)(Ordering[Long].reverse)
    }
  }

  def getMessages(authEnrolments: Enrolments, filters: Filters)(
    implicit ec: ExecutionContext): Future[List[Message]] = {
    val filteredEnrolments = authEnrolments.filter(filters.enrolmentKeysFilter, filters.enrolmentsFilter)
    val identifiers: Set[Identifier] = filteredEnrolments.map(_.asIdentifier)
    for {
      conversations <- conversationRepository.getConversations(identifiers, filters.tags)
      letters       <- messageRepository.getLetters(identifiers, filters.tags)
      v4Messages    <- secureMessageUtil.getMessages(identifiers, filters.tags)
    } yield (conversations ++ letters ++ v4Messages).sortBy(_.issueDate)(dateTimeDescending)
  }

  def getMessagesList(authTaxIds: Set[TaxIdWithName])(
    implicit ec: ExecutionContext,
    hc: HeaderCarrier,
    messageFilter: MessageFilter): Future[List[Message]] =
    for {
      v3Messages <- messageRepository.findBy(authTaxIds)
      v4Messages <- secureMessageUtil.findBy(authTaxIds)
    } yield v3Messages ++ v4Messages

  def getMessagesCount(authTaxIds: Set[TaxIdWithName])(
    implicit ec: ExecutionContext,
    hc: HeaderCarrier,
    messageFilter: MessageFilter): Future[MessagesCount] =
    for {
      v3MessagesCount <- messageRepository.countBy(authTaxIds)
      v4MessagesCount <- secureMessageUtil.countBy(authTaxIds)
    } yield
      MessagesCount(v3MessagesCount.total + v4MessagesCount.total, v3MessagesCount.unread + v4MessagesCount.unread)

  def getMessagesCount(authEnrolments: Enrolments, filters: Filters)(
    implicit ec: ExecutionContext): Future[MessagesCount] = {
    val filteredEnrolments = authEnrolments.filter(filters.enrolmentKeysFilter, filters.enrolmentsFilter)
    val identifiers: Set[Identifier] = filteredEnrolments.map(_.asIdentifier)
    for {
      conversationsCount <- conversationRepository.getConversationsCount(identifiers, filters.tags)
      lettersCount       <- messageRepository.getLettersCount(identifiers, filters.tags)
      v4MessagesCount    <- secureMessageUtil.getSecureMessageCount(identifiers, filters.tags)
    } yield
      MessagesCount(
        total = conversationsCount.total + lettersCount.total + v4MessagesCount.total,
        unread = conversationsCount.unread + lettersCount.unread + v4MessagesCount.unread
      )
  }

  def getConversation(id: ObjectId, enrolments: Set[CustomerEnrolment])(
    implicit ec: ExecutionContext): Future[Either[SecureMessageError, ApiConversation]] = {
    val identifiers = enrolments.map(_.asIdentifier)
    for {
      conversation <- EitherT(conversationRepository.getConversation(id, identifiers))
      _            <- addReadTime(conversation, identifiers, DateTime.now())
    } yield ApiConversation.fromCore(conversation, identifiers)
  }.value

  def getLetter(id: ObjectId, enrolments: Set[CustomerEnrolment])(
    implicit ec: ExecutionContext): Future[Either[SecureMessageError, ApiLetter]] = {
    val identifiers = enrolments.map(_.asIdentifier)
    for {
      letter <- EitherT(messageRepository.getLetter(id, identifiers))
      _      <- EitherT(messageRepository.addReadTime(id))
    } yield ApiLetter.fromCore(letter)
  }.value

  def getSecureMessage(id: ObjectId, enrolments: Set[CustomerEnrolment])(
    implicit ec: ExecutionContext,
    language: Language): Future[Either[SecureMessageError, ApiLetter]] = {
    val identifiers = enrolments.map(_.asIdentifier)
    for {
      secureMessage <- EitherT(secureMessageUtil.getMessage(id, identifiers))
      _             <- EitherT(secureMessageUtil.addReadTime(id))
    } yield {
      val apiLetter = ApiLetter.fromSecureMessage(secureMessage)
      apiLetter.copy(content = decodeBase64String(apiLetter.content))
    }
  }.value

  def getSecureMessage(id: ObjectId)(implicit ec: ExecutionContext): Future[Option[SecureMessage]] =
    secureMessageUtil.getMessage(id, Set.empty[Identifier]) map {
      case Right(secureMessage) => Some(secureMessage)
      case _                    => None
    }

  def getLetter(id: ObjectId)(implicit ec: ExecutionContext): Future[Option[Letter]] =
    messageRepository.getLetter(id, Set.empty[Identifier]) map {
      case Right(letter) => Some(letter)
      case _             => None
    }

  def addCaseWorkerMessageToConversation(
    client: String,
    conversationId: String,
    messagesRequest: CaseworkerMessage,
    randomId: String,
    maybeReference: Option[Reference])(
    implicit ec: ExecutionContext,
    hc: HeaderCarrier): Future[Either[SecureMessageError, Unit]] = {
    val senderIdentifier: Identifier = messagesRequest.senderIdentifier(client, conversationId)
    def message(sender: Participant) =
      ConversationMessage(Some(randomId), sender.id, new DateTime(), messagesRequest.content, maybeReference)
    for {
      _            <- ContentValidator.validate(messagesRequest.content)
      conversation <- EitherT(conversationRepository.getConversation(client, conversationId, Set(senderIdentifier)))
      sender       <- EitherT(Future(conversation.participantWith(Set(senderIdentifier))))
      participants <- addMissingEmails(conversation.participants)
      _            <- EitherT(conversationRepository.addMessageToConversation(client, conversationId, message(sender)))
      _            <- sendAlert(participants.customer, conversation.alert)
    } yield ()
  }.value

  def addCustomerMessage(
    id: String,
    messagesRequest: CustomerMessage,
    enrolments: Enrolments,
    randomId: String,
    reference: Option[Reference])(
    implicit ec: ExecutionContext,
    request: Request[_]): Future[Either[SecureMessageError, Unit]] = {
    def message(sender: Participant) =
      ConversationMessage(
        Some(randomId),
        sender.id,
        new DateTime(),
        messagesRequest.content,
        reference
      )
    val identifiers: Set[Identifier] = enrolments.asIdentifiers
    for {
      conversation <- EitherT(conversationRepository.getConversation(new ObjectId(id), identifiers))
      sender       <- EitherT(Future(conversation.participantWith(identifiers)))
      _            <- forwardMessage(conversation.id, messagesRequest, randomId)
      _ <- EitherT(
            conversationRepository
              .addMessageToConversation(conversation.client, conversation.id, message(sender)))
    } yield ()
  }.value

  private def forwardMessage(conversationId: String, messagesRequest: CustomerMessage, requestId: String)(
    implicit request: Request[_]): EitherT[Future, SecureMessageError, Unit] = {
    val ACKNOWLEDGEMENT_REFERENCE_MAX_LENGTH = 32
    val randomId = UUID.randomUUID().toString
    val correlationId = request.headers
      .get("X-Correlation-ID")
      .getOrElse(randomId)
      .replace("-", "")
      .substring(0, ACKNOWLEDGEMENT_REFERENCE_MAX_LENGTH - 1)
    val queryMessageWrapper = QueryMessageWrapper(
      QueryMessageRequest(
        requestCommon = RequestCommon(
          originatingSystem = "dc-secure-message",
          receiptDate = DateTime.now(),
          acknowledgementReference = correlationId
        ),
        requestDetail = messagesRequest.asRequestDetail(requestId, conversationId)
      ))

    EitherT(eisConnector.forwardMessage(queryMessageWrapper))
  }

  private[services] def addReadTime(conversation: Conversation, identifiers: Set[Identifier], readTime: DateTime)(
    implicit ec: ExecutionContext): EitherT[Future, SecureMessageError, Unit] = {

    def addTime(
      reader: Participant,
      conversation: Conversation,
      readTime: DateTime): Future[Either[SecureMessageError, Unit]] =
      reader.lastReadTime match {
        case None =>
          conversationRepository
            .addReadTime(conversation.client, conversation.id, reader.id, readTime)
        case Some(lastReadTime) if lastReadTime.isBefore(conversation.latestMessage.created) =>
          conversationRepository
            .addReadTime(conversation.client, conversation.id, reader.id, readTime)
        case _ => Future.successful(Right[SecureMessageError, Unit](()))
      }

    for {
      reader <- EitherT(Future(conversation.participantWith(identifiers)))
      _      <- EitherT(addTime(reader, conversation, readTime))
    } yield ()
  }

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

  private def sendAlert(receivers: CustomerParticipants, alert: core.Alert)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): EitherT[Future, SecureMessageError, Unit] = {

    val enrolments = receivers.success.map(_.identifier).headOption.flatMap { identifier =>
      identifier.enrolment match {
        case Some(key) => Some(Tags(None, None, Some(s"$key~${identifier.name}~${identifier.value}")))
        case _         => None
      }
    }

    def emailRequest =
      EmailRequest(receivers.success.flatMap(_.email), alert.templateId, alert.parameters.getOrElse(Map()), enrolments)
    for {
      _ <- validateEmailReceivers(receivers)(errorCondition = receivers.success.isEmpty)
      _ <- EitherT(emailConnector.send(emailRequest))
      _ <- validateEmailReceivers(receivers)(errorCondition = receivers.failure.nonEmpty)
    } yield ()
  }

  private def validateEmailReceivers(emailReceivers: CustomerParticipants)(errorCondition: => Boolean)(
    implicit ec: ExecutionContext): EitherT[Future, SecureMessageError, Unit] =
    EitherT.fromEither[Future](
      Either.cond(
        !errorCondition,
        (),
        NoReceiverEmailError(s"Email lookup failed for: $emailReceivers")
      )
    )

  case class GroupedParticipants(system: List[Participant], customer: CustomerParticipants) {
    def all: List[Participant] = system ++ customer.all
  }
  case class CustomerParticipants(success: List[Participant], failure: List[CustomerEmailError]) {
    def all: List[Participant] = success ++ failure.map(_.customer)
  }
  case class CustomerEmailError(customer: Participant, error: EmailLookupError)

  def getContentBy(
    id: ObjectId
  )(implicit ec: ExecutionContext, messages: Messages): Future[Option[String]] =
    for {
      msg <- secureMessageUtil.findById(id)
      result <- msg match {
                 case Some(m) => Future.successful(Some(formatMessageContent(m)))
                 case None =>
                   messageConnector.getContent(id.toString) map { r =>
                     Some(r.body)
                   }
               }
    } yield result

  def setReadTime(
    id: ObjectId
  )(implicit ec: ExecutionContext): Future[Either[SecureMessageError, Unit]] =
    secureMessageUtil.addReadTime(id)

  def checkAndSetV3MessagesReadTime(id: ObjectId)(implicit ec: ExecutionContext): Future[Result] =
    messageConnector.setReadtime(id.toString)

  private def formatMessageContent(message: SecureMessage)(implicit messages: Messages) =
    if (messages.lang.language == "cy") {
      val welshContent: Option[Content] = MessageMetadata.contentForLanguage(Welsh, message.content)
      val welshBody = welshContent.map(_.body).getOrElse("")
      val welshSubject = welshContent.map(_.subject).getOrElse("")
      formatSubject(welshSubject, isWelshSubject = true) ++ addIssueDate(message) ++ decodeBase64String(welshBody)
    } else {
      val englishContent: Option[Content] = MessageMetadata.contentForLanguage(English, message.content)
      val body = englishContent.map(_.body).getOrElse("")
      val subject = englishContent.map(_.subject).getOrElse("")
      formatSubject(subject, isWelshSubject = false) ++ addIssueDate(message) ++ decodeBase64String(body)
    }

  private def formatSubject(messageSubject: String, isWelshSubject: Boolean): String =
    if (isWelshSubject) {
      <h1 lang="cy" class="govuk-heading-xl">{XML.loadString("<root>" + messageSubject + "</root>").child}</h1>.mkString
    } else {
      <h1 lang="en" class="govuk-heading-xl">{XML.loadString("<root>" + messageSubject + "</root>").child}</h1>.mkString
    }

  private def addIssueDate(message: SecureMessage)(implicit messages: Messages): String = {
    val issueDate = localizedFormatter(message.issueDate.toLocalDate)
    <p class='message_time faded-text--small govuk-body'>{s"${messages("date.text.advisor", issueDate)}"}</p><br/>.mkString
  }

  private def localizedFormatter(date: LocalDate)(implicit messages: Messages): String = {
    val formatter =
      if (messages.lang.language == "cy") {
        DateTimeFormat.forPattern(s"d '${messages(s"month.${date.getMonthOfYear}")}' yyyy")
      } else {
        DateTimeFormat.forPattern("dd MMMM yyyy")
      }
    date.toString(formatter)
  }

  def decodeBase64String(input: String): String =
    new String(Base64.decodeBase64(input.getBytes("UTF-8")))
}
