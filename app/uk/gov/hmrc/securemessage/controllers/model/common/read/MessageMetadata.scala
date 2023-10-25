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

package uk.gov.hmrc.securemessage.controllers.model.common.read

import org.joda.time.{ DateTime, LocalDate }
import play.api.i18n.Messages
import play.api.libs.json.{ Json, OFormat }
import uk.gov.hmrc.auth.core.Enrolments
import uk.gov.hmrc.common.message.model.TaxpayerName
import uk.gov.hmrc.securemessage.controllers.model.cdcm.read.ConversationMetadata
import uk.gov.hmrc.securemessage.controllers.model.cdsf.read.ApiLetter
import uk.gov.hmrc.securemessage.controllers.model.{ ApiFormats, MessageType }
import uk.gov.hmrc.securemessage.controllers.utils.IdCoder
import uk.gov.hmrc.securemessage.models.core.{ Conversation, Language, Letter, Message, RecipientName }
import uk.gov.hmrc.securemessage.models.v4.{ Content, SecureMessage }
import uk.gov.hmrc.securemessage.services.ImplicitClassesExtensions

final case class MessageMetadata(
  messageType: MessageType = MessageType.Letter,
  id: String,
  subject: String,
  issueDate: DateTime,
  senderName: Option[String] = None,
  unreadMessages: Boolean = false,
  count: Int = 1,
  taxpayerName: Option[RecipientName] = None,
  validFrom: Option[LocalDate] = None,
  readTime: Option[DateTime] = None,
  replyTo: Option[String] = None,
  sentInError: Option[Boolean] = None,
  messageDesc: Option[String] = None,
  counter: Option[Int] = None,
  language: Option[String] = None
)

object MessageMetadata extends ApiFormats with ImplicitClassesExtensions {

  def apply(message: Message)(implicit language: Language): MessageMetadata = mapForMessage(message, language)

  def apply(message: Message, reader: Enrolments, language: Language)(implicit messages: Messages): MessageMetadata =
    message match {
      case c: Conversation  => map(c, reader)
      case l: Letter        => map(l)
      case s: SecureMessage => map(s, language)
      case _                => throw new IllegalArgumentException("Unsupported Message")
    }

  private def map(conversation: Conversation, reader: Enrolments)(implicit messages: Messages): MessageMetadata = {
    val cm = ConversationMetadata.coreToConversationMetadata(conversation, reader.asIdentifiers)
    val messageType = MessageType.Conversation
    MessageMetadata(
      messageType = messageType,
      id = IdCoder.encodeId(messageType, conversation._id.toString),
      subject = conversation.subject,
      issueDate = conversation.issueDate,
      senderName = cm.senderName,
      unreadMessages = conversation.unreadMessagesFor(reader.asIdentifiers).nonEmpty,
      count = cm.count
    )
  }
  private def map(letter: Letter): MessageMetadata = {
    val messageType = MessageType.Letter
    val al = ApiLetter.fromCore(letter)
    new MessageMetadata(
      messageType = messageType,
      id = IdCoder.encodeId(messageType, letter._id.toString),
      subject = letter.subject,
      issueDate = letter.issueDate,
      senderName = Some(al.senderInformation.name),
      unreadMessages = letter.readTime.isEmpty,
      count = 1
    )
  }

  private def map(secureMessage: SecureMessage, language: Language): MessageMetadata = {
    val messageType = MessageType.Letter
    val content = contentForLanguage(language, secureMessage.content)
    new MessageMetadata(
      messageType = messageType,
      id = IdCoder.encodeId(messageType, secureMessage._id.toString),
      subject = content.map(_.subject).getOrElse(""),
      issueDate = secureMessage.issueDate,
      senderName = Some("HMRC"),
      unreadMessages = secureMessage.readTime.isEmpty,
      count = 1,
      language = content.map(_.lang.entryName)
    )
  }

  private def mapForMessage(message: Message, language: Language): MessageMetadata =
    message match {
      case l: Letter        => mapForLetter(l)
      case s: SecureMessage => mapForSecureMessage(s, language)
      case _                => throw new IllegalArgumentException("Unsupported Message")
    }

  private def mapForLetter(letter: Letter): MessageMetadata = {
    val messageType = MessageType.Letter
    val al = ApiLetter.fromCore(letter)
    new MessageMetadata(
      messageType = messageType,
      id = letter._id.toString,
      subject = letter.subject,
      issueDate = letter.issueDate,
      senderName = Some(al.senderInformation.name),
      unreadMessages = letter.readTime.isEmpty,
      count = 1,
      taxpayerName = letter.alertDetails.recipientName,
      validFrom = Some(letter.validFrom),
      readTime = letter.readTime,
      replyTo = letter.body.flatMap(_.replyTo),
      sentInError = Some(letter.rescindment.isDefined),
      messageDesc = letter.body.flatMap(_.`type`)
    )
  }

  private def mapForSecureMessage(secureMessage: SecureMessage, language: Language): MessageMetadata = {
    val messageType = MessageType.Letter
    val content = contentForLanguage(language, secureMessage.content)
    new MessageMetadata(
      messageType = messageType,
      id = secureMessage._id.toString,
      subject = content.map(_.subject).getOrElse(""),
      issueDate = secureMessage.issueDate,
      senderName = Some("HMRC"),
      unreadMessages = secureMessage.readTime.isEmpty,
      count = 1,
      taxpayerName = secureMessage.alertDetails.recipientName map taxpayerNameToRecipientName,
      validFrom = Some(secureMessage.validFrom),
      readTime = secureMessage.readTime,
      replyTo = secureMessage.details.flatMap(_.replyTo),
      sentInError = Some(false),
      language = content.map(_.lang.entryName)
    )
  }

  def taxpayerNameToRecipientName(t: TaxpayerName): RecipientName =
    RecipientName(t.title, t.forename, t.secondForename, t.surname, t.honours, t.line1)

  def contentForLanguage(lang: Language, content: List[Content]): Option[Content] =
    content.find(_.lang == lang).orElse(content.headOption)

  implicit val messageMetadataFormat: OFormat[MessageMetadata] =
    Json.format[MessageMetadata]
}
