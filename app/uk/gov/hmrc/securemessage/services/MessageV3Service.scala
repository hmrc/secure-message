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

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.mongodb.scala.bson.ObjectId
import play.api.i18n.Messages
import uk.gov.hmrc.auth.core.{ Enrolment, Enrolments }
import uk.gov.hmrc.domain.TaxIds.TaxIdWithName
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.securemessage.connectors.AuthIdentifiersConnector
import uk.gov.hmrc.securemessage.handlers.MessageReadRequest
import uk.gov.hmrc.securemessage.models.core.{ Identifier, Letter, Message }
import uk.gov.hmrc.securemessage.{ MessageNotFound, SecureMessageError, UserNotAuthorised }
import uk.gov.hmrc.common.message.model.ConversationItem
import play.twirl.api.Html
import scala.concurrent.{ ExecutionContext, Future }
import scala.xml.XML

trait MessageV3Service {
  val authIdentifiersConnector: AuthIdentifiersConnector

  def getMessage(
    readRequest: MessageReadRequest
  )(implicit hc: HeaderCarrier, ec: ExecutionContext, messages: Messages): Future[Either[SecureMessageError, Message]] =
    for {
      taxIds <- authIdentifiersConnector.currentEffectiveTaxIdentifiers
      identifiers = taxIds.map(s => Identifier(s.name, s.value, None))
      letter     <- getLetter(new ObjectId(readRequest.messageId))
      strideUser <- authIdentifiersConnector.isStrideUser
      v3Message  <- if (letter.isDefined) updateMessageContent(letter) else Future.successful(None)
      result <- v3Message match {
                  case Some(m: Letter) if identifiers.contains(m.recipient.identifier) || strideUser =>
                    Future.successful(Right(m))
                  case Some(_) =>
                    Future.successful(Left(UserNotAuthorised("Unauthorised for the requested identifiers")))
                  case None =>
                    Future.successful(Left(MessageNotFound(s"Message not found for ${readRequest.messageId}")))
                }
    } yield result

  // updates the message content with the content from all the messages in the chain (if there is one)
  def updateMessageContent(
    letter: Option[Letter]
  )(implicit ec: ExecutionContext, messages: Messages): Future[Option[Letter]] =
    letter match {
      case Some(m) =>
        getContentChainString(letter, m._id).flatMap { content =>
          Future(Some(m.copy(content = Some(content))))
        }
      case None => Future(None)
    }

  def getContentChainString(letter: Option[Letter], id: ObjectId)(implicit
    ec: ExecutionContext,
    messages: Messages
  ): Future[String] =
    letter match {
      case Some(msg) =>
        msg.body.flatMap(_.replyTo) match {
          case Some(_) => getMessagesContentChain(id).flatMap(list => Future(list.reverse.mkString("<hr/>")))
          case None    => Future.successful(formatMessageContent(msg))
        }
      case None => Future.successful("")
    }

  def getMessagesContentChain(id: ObjectId)(implicit ec: ExecutionContext, messages: Messages): Future[List[String]] = {

    def getMessagesContentChain(id: ObjectId, contentList: List[String]): Future[List[String]] =
      getLetter(id).flatMap {
        case None => Future.successful(contentList)
        case Some(letter) =>
          letter.body.flatMap(_.replyTo) match {
            case None => Future.successful(formatMessageContent(letter) :: contentList)
            case Some(replyTo) =>
              getMessagesContentChain(
                new ObjectId(replyTo),
                formatMessageContent(letter) :: contentList
              )
          }
      }
    getMessagesContentChain(id, List())
  }

  def findMessageListById(
    id: String
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[String, List[ConversationItem]]] =
    for {
      taxIds <- authIdentifiersConnector.currentEffectiveTaxIdentifiers
      identifiers = taxIds.map(s => Identifier(s.name, s.value, None))
      msgList <- getMessageListResponse(id, identifiers)
    } yield msgList

  private def getMessageListResponse(id: String, taxIds: Set[Identifier])(implicit
    ec: ExecutionContext
  ): Future[Either[String, List[ConversationItem]]] = {

    def getMessageListResponse(
      id: String,
      messageList: List[ConversationItem]
    ): Future[Either[String, List[ConversationItem]]] =
      getLetter(new ObjectId(id)).flatMap {
        case None => Future.successful(Left("Message not found"))
        case Some(message) =>
          if (taxIds.contains(message.recipient.identifier)) {
            message.body.flatMap(_.replyTo) match {
              case None =>
                Future.successful(
                  Right(
                    ConversationItem(
                      message._id.toString,
                      message.subject,
                      None,
                      message.validFrom,
                      message.content
                    ) :: messageList
                  )
                )
              case Some(replyTo) =>
                getMessageListResponse(
                  replyTo,
                  ConversationItem(
                    message._id.toString,
                    message.subject,
                    None,
                    message.validFrom,
                    message.content
                  ) :: messageList
                )
            }
          } else {
            Future.successful(Left("Message unauthorised"))
          }
      }

    for {
      msgList <- getMessageListResponse(id, List())
    } yield msgList
  }

  def formatMessageContent(letter: Letter)(implicit messages: Messages): String = {
    val letterContent = letter.content.getOrElse("")
    if (letter.content.exists(c => c.contains(letter.subject))) {
      letterContent
    } else {
      formatSubject(
        letter.subject,
        letter.body.flatMap(_.form.map(_.toUpperCase)).fold(false)(_.endsWith("_CY"))
      ) ++ addIssueDate(letter) ++ letterContent
    }
  }

  // format: off
  private def formatSubject(messageSubject: String, isWelshSubject: Boolean): String =
    if (isWelshSubject) {
      <h1 lang="cy" class="govuk-heading-xl">{XML.loadString("<root>" + messageSubject + "</root>").child}</h1>.mkString
    } else {
      <h1 lang="en" class="govuk-heading-xl">{XML.loadString("<root>" + messageSubject + "</root>").child}</h1>.mkString
    }

  def addIssueDate(letter: Letter)(implicit messages: Messages): String = {
    val issueDate = localizedExtractMessageDate(letter)
    <p class='message_time faded-text--small govuk-body'>{s"${messages("date.text.advisor", issueDate)}"}</p><br/>.mkString
  }
  // format: on
  def localizedExtractMessageDate(letter: Letter)(implicit messages: Messages): String =
    letter.body.flatMap(_.issueDate) match {
      case Some(issueDate) => localizedFormatter(issueDate)
      case None            => localizedFormatter(letter.validFrom)
    }

  private val dateFormatter = DateTimeFormatter.ofPattern("dd MMMM yyyy")
  def formatter(date: LocalDate): String = date.format(dateFormatter)

  private def localizedFormatter(date: LocalDate)(implicit messages: Messages): String = {
    val formatter =
      if (messages.lang.language == "cy") {
        DateTimeFormatter.ofPattern(s"d '${messages(s"month.${date.getMonthValue}")}' yyyy")
      } else {
        dateFormatter
      }
    date.format(formatter)
  }
  def getLetter(id: ObjectId)(implicit ec: ExecutionContext): Future[Option[Letter]]
}
