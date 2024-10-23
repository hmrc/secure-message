/*
 * Copyright 2024 HM Revenue & Customs
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

import play.twirl.api.Html
import uk.gov.hmrc.common.message.model.ConversationItem
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import scala.concurrent.Future
import scala.util.{Failure, Success}
import scala.xml.{Elem, Node, NodeSeq, Unparsed, Utility, Xhtml}
import scala.util.{Failure, Success, Try}
import scala.xml.{Node, SAXParseException, Text}
import HtmlUtil._
import XmlConversion._

class HtmlCreatorService @Inject()(servicesConfig: ServicesConfig) {

  def createConversation(
                                   latestMessageId: String,
                                   messages: List[ConversationItem],
                                   replyType: RenderType.ReplyType): Future[Either[String, Html]] = {

    val conversation = createConversationList(messages.sortWith(_.id > _.id), replyType)
    val fullConversation = conversation.mkString(Xhtml.toXhtml(<hr/>))

    Future.successful(Right(Html.apply(fullConversation)))
  }

  private def createConversationList(messages: List[ConversationItem], replyType: RenderType.ReplyType): List[String] =
    replyType match {
      case RenderType.CustomerLink =>
        messages
          .sortWith(_.id > _.id)
          .headOption
          .map { hm =>
            format2wsMessageForCustomer(hm, ItemMetadata(isLatestMessage = true)) :: messages.tail.map(m =>
              format2wsMessageForCustomer(m, ItemMetadata(isLatestMessage = false)))
          }
          .getOrElse(List.empty)
      case RenderType.CustomerForm =>
        messages
          .sortWith(_.id > _.id)
          .headOption
          .map { hm =>
            format2wsMessageForCustomer(hm, ItemMetadata(isLatestMessage = true, hasSmallSubject = true)) :: messages.tail
              .map(m => format2wsMessageForCustomer(m, ItemMetadata(isLatestMessage = false)))
          }
          .getOrElse(List.empty)
      case RenderType.Adviser => messages.sortWith(_.id > _.id).map(msg => format2wsMessageForAdviser(msg))
    }

  private def format2wsMessageForCustomer(item: ConversationItem, metadata: ItemMetadata): String =
    Xhtml.toXhtml(
      getHeader(metadata, item.subject) ++ <p class="faded-text--small">{getCustomerDateText(item)}</p>
        ++ getContentDiv(item.content) ++
        item.body
          .flatMap(_.enquiryType)
          .map(_.startsWith("p800"))
          .flatMap(s => if (s) None else Some(s))
          .fold(getContactLink(metadata, item))(_ => getReplyLink(metadata, item))
          .getOrElse(NodeSeq.Empty)
    )

  private def format2wsMessageForAdviser(item: ConversationItem): String =
    Xhtml.toXhtml(<p class="faded-text--small">{getAdviserDatesText(item)}</p> ++ getContentDiv(item.content))

  private def getHeader(metadata: ItemMetadata, subject: String): Elem = {
    val headingClass = "govuk-heading-xl margin-top-small margin-bottom-small"
    if (metadata.isLatestMessage && !metadata.hasSmallSubject) {
      <h1 class={headingClass}>{Unparsed(escapeForXhtml(subject))}</h1>
    } else {
      <h2 class={headingClass}>{Unparsed(escapeForXhtml(subject))}</h2>
    }
  }

  private def fixHtmlString(htmlString: String): String = {
    // makes line breaks XHTML valid
    val lineBreakFix = ("<br>", "<br/>")
    // preserves space in front of links
    val linkSpaceFix = ("<a", "&#160;<a")
    val nonBreakingSpaceFix = ("&nbsp;", "&#160;")
    htmlString
      .replace(lineBreakFix._1, lineBreakFix._2)
      .replace(linkSpaceFix._1, linkSpaceFix._2)
      .replace(nonBreakingSpaceFix._1, nonBreakingSpaceFix._2)
  }

  private def getContentDiv(maybeContent: Option[String]): Node =
    maybeContent match {
      case Some(content) =>
        XmlConversion.stringToXmlNodes(fixHtmlString(content)) match {
          case Success(nodes) => Utility.trim(<div>{nodes}</div>)
          case Failure(_)     => <div>There was a problem reading the message content</div>
        }
      case None => <div/>
    }

  private def getContactLink(metadata: ItemMetadata, conversationItem: ConversationItem): Option[Elem] =
    if (metadata.isLatestMessage && metadata.hasLink) {
      conversationItem.body.flatMap(_.`type` match {
        case Some("2wsm-advisor")=>
          val contactUrl = servicesConfig.getString("contact-hmrc-url")
          Some(
            <a href={contactUrl} target="_blank" rel="noopener noreferrer">Contact HMRC (opens in a new window or tab)</a>)
        case _ => None
      })
    } else {
      None
    }

  private def getReplyLink(metadata: ItemMetadata, conversationItem: ConversationItem): Option[Elem] =
    if (metadata.isLatestMessage && metadata.hasLink) {
      val enquiryType = conversationItem.body
        .flatMap {
          _.enquiryType
        }
        .getOrElse("")
      val formActionUrl =
        s"/two-way-message-frontend/message/customer/$enquiryType/" + conversationItem.id + "/reply#reply-input-label"
      conversationItem.body.flatMap(_.`type` match {
        case Some("2wsm-advisor")=>
          val link = <a href={formActionUrl}>Send another message about this</a>
          Some(<p>{getReplyIcon(formActionUrl) ++ link}</p>)
        case _ => None
      })
    } else {
      None
    }

  private def getReplyIcon(formActionUrl: String): Node =
    Utility.trim(<span>
      <a style="text-decoration:none;" href={formActionUrl}>
        <svg style="vertical-align:text-top;padding-right:5px;" width="21px" height="20px" viewBox="0 0 33 31" version="1.1" xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink">
          <title>Reply</title>
          <g id="Page-1" stroke="none" stroke-width="1" fill="none" fill-rule="evenodd">
            <g id="icon-reply" fill="#000000" fill-rule="nonzero">
              <path d="M20.0052977,9.00577935 C27.0039418,9.21272548 32.6139021,14.9512245 32.6139021,22 C32.6139021,25.5463753 31.1938581,28.7610816 28.8913669,31.1065217 C29.2442668,30.1082895 29.4380446,29.1123203 29.4380446,28.1436033 C29.4380446,21.8962314 25.9572992,21.1011463 20.323108,21 L15,21 L15,30 L-1.42108547e-14,15 L15,2.25597319e-13 L15,9 L20,9 L20.0052977,9.00577935 Z" id="Combined-Shape"></path>
            </g>
          </g>
        </svg>
      </a>
    </span>)

  private def getCustomerDateText(message: ConversationItem): String = {
    val messageDate = extractMessageDate(message)
    message.body match {
      case Some(conversationItemDetails) =>
        conversationItemDetails.`type` match {
          case Some("2wsm-customer") => s"You sent this message on $messageDate"
          case Some("2wsm-advisor") => s"This message was sent to you on $messageDate"
          case _                    => defaultDateText(messageDate)
        }
      case _ => defaultDateText(messageDate)
    }
  }

  def getAdviserDatesText(message: ConversationItem): String = {
    val messageDate = extractMessageDate(message)
    message.body match {
      case Some(conversationItemDetails) =>
        conversationItemDetails.`type` match {
          case Some("2wsm-advisor") => s"$messageDate by HMRC:"
          case Some("2wsm-customer") => s"$messageDate by the customer:"
          case _                    => defaultDateText(messageDate)
        }
      case _ => defaultDateText(messageDate)
    }
  }

  private def defaultDateText(dateStr: String) = s"This message was sent on $dateStr"

  private def extractMessageDate(message: ConversationItem): String =
    message.body.flatMap(_.issueDate) match {
      case Some(issueDate) => formatter(issueDate)
      case None            => formatter(message.validFrom)
    }

  private val dateFormatter = DateTimeFormatter.ofPattern("dd MMMM yyyy")

  private def formatter(date: LocalDate): String = date.format(dateFormatter)
}

object RenderType {

  sealed trait ReplyType

  case object CustomerLink extends ReplyType

  case object CustomerForm extends ReplyType

  case object Adviser extends ReplyType

}

object HtmlUtil {

  def escapeForHtml(text: String): String =
    text.replaceAll("<", "&lt;").replaceAll(">", "&gt;")

  def escapeForXhtml(text: String): String =
    escapeForHtml(text).replaceAll("&\\s", "&amp; ")

}

object XmlConversion {

  /**
    * Returns one or more XML nodes parsed from the given string or an exception if the parsing fails.
    * If the string is empty an empty text node will be returned.
    */
  def stringToXmlNodes(string: String): Try[Seq[Node]] =
    try {
      val xml = scala.xml.XML.loadString("<root>" + string.trim + "</root>")
      val result = xml.child
      if (result.isEmpty) {
        Success(Text(""))
      } else {
        Success(result)
      }
    } catch {
      case e: SAXParseException => Failure(e)
    }

}

case class ItemMetadata(
                         isLatestMessage: Boolean,
                         hasLink: Boolean = true,
                         hasSmallSubject: Boolean = false
                       )