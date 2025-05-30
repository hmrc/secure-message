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

package uk.gov.hmrc.securemessage.handlers

import org.bson.types.ObjectId

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import play.api.i18n.Messages
import play.api.libs.json.{ JsValue, Json }
import uk.gov.hmrc.common.message.model.Language
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.securemessage.connectors.AuthIdentifiersConnector
import uk.gov.hmrc.securemessage.controllers.model.{ ApiMessage, MessageResourceResponse, MessagesResponse }
import uk.gov.hmrc.securemessage.models.core.*
import uk.gov.hmrc.securemessage.models.v4.SecureMessage
import uk.gov.hmrc.securemessage.services.SecureMessageServiceImpl
import uk.gov.hmrc.securemessage.{ MessageNotFound, SecureMessageError, UserNotAuthorised }

import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }
import scala.xml.XML

class NonCDSMessageRetriever @Inject() (
  val authIdentifiersConnector: AuthIdentifiersConnector,
  secureMessageService: SecureMessageServiceImpl
)(implicit ec: ExecutionContext)
    extends MessageRetriever {
  def fetch(requestWrapper: MessageRequestWrapper, language: Language)(implicit
    hc: HeaderCarrier,
    messages: Messages
  ): Future[JsValue] = {
    implicit val mf: MessageFilter =
      requestWrapper.messageFilter.copy(taxIdentifiers = requestWrapper.messageFilter.taxIdentifiers.flatMap { taxId =>
        taxId match {
          case "HMRC-MTD-VAT" => List(taxId, "vrn")
          case _              => List(taxId)
        }
      })

    for {
      authTaxIds <- authIdentifiersConnector.currentEffectiveTaxIdentifiers
      _          <- Future(logger.warn(s"MessagesController: authTaxIds $authTaxIds"))
      result <- secureMessageService.getMessagesList(authTaxIds).map { items =>
                  MessagesResponse.fromMessages(items, language).toConversations
                }
    } yield Json.toJson(result)
  }

  def messageCount(
    requestWrapper: MessageRequestWrapper
  )(implicit hc: HeaderCarrier, messages: Messages): Future[JsValue] = {
    implicit val mf: MessageFilter =
      requestWrapper.messageFilter.copy(taxIdentifiers = requestWrapper.messageFilter.taxIdentifiers.flatMap { taxId =>
        taxId match {
          case "HMRC-MTD-VAT" => List(taxId, "vrn")
          case _              => List(taxId)
        }
      })

    for {
      authTaxIds <- authIdentifiersConnector.currentEffectiveTaxIdentifiers
      _          <- Future(logger.warn(s"MessagesController: authTaxIds $authTaxIds"))
      result <- secureMessageService.getMessagesCount(authTaxIds).map { items =>
                  MessagesResponse.fromMessagesCount(items).toConversations
                }
    } yield Json.toJson(result)
  }

  def getMessage(readRequest: MessageReadRequest)(implicit
    hc: HeaderCarrier,
    messages: Messages,
    language: Language
  ): Future[Either[SecureMessageError, ApiMessage]] =
    for {
      taxIds <- authIdentifiersConnector.currentEffectiveTaxIdentifiers
      identifiers = taxIds.map(s => Identifier(s.name, s.value, None))
      letter     <- secureMessageService.getLetter(new ObjectId(readRequest.messageId))
      strideUser <- authIdentifiersConnector.isStrideUser
      v4MessageOrLetter <- if (letter.isDefined) {
                             updateMessageContent(letter)
                           } else {
                             secureMessageService.getSecureMessage(new ObjectId(readRequest.messageId))
                           }
      result <- v4MessageOrLetter match {
                  case Some(m: Letter) if identifiers.contains(m.recipient.identifier) || strideUser =>
                    Future.successful(Right(MessageResourceResponse.from(m)))
                  case Some(m: SecureMessage) if taxIds.contains(m.recipient.identifier) || strideUser =>
                    Future.successful(Right(MessageResourceResponse.from(m)))
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
      secureMessageService.getLetter(id).flatMap {
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

  def formatMessageContent(letter: Letter)(implicit messages: Messages): String =
    formatSubject(
      letter.subject,
      letter.body.flatMap(_.form.map(_.toUpperCase)).fold(false)(_.endsWith("_CY"))
    ) ++ addIssueDate(letter) ++ letter.content.getOrElse("")

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

  def findAndSetReadTime(
    id: ObjectId
  )(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Either[SecureMessageError, Option[Message]]] =
    for {
      taxIds <- authIdentifiersConnector.currentEffectiveTaxIdentifiers
      identifiers = taxIds.map(s => Identifier(s.name, s.value, None))
      strideUser <- authIdentifiersConnector.isStrideUser
      letter     <- secureMessageService.getLetter(id)
      v3orv4     <- if (letter.isDefined) Future.successful(letter) else secureMessageService.getSecureMessage(id)
      message <- v3orv4 match {
                   case Some(l: Letter) if identifiers.contains(l.recipient.identifier) =>
                     secureMessageService.setReadTime(l)
                   case Some(m: SecureMessage) if taxIds.contains(m.recipient.identifier) || strideUser =>
                     secureMessageService.setReadTime(m)
                   case Some(_) =>
                     Future.successful(Left(UserNotAuthorised("Unauthorised for the requested identifiers")))
                   case None =>
                     Future.successful(Left(MessageNotFound(s"Message not found for $id")))
                 }
    } yield Right(message.toOption)

}
