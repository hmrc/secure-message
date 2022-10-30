/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.securemessage.controllers.model

import org.bson.types.ObjectId
import org.joda.time.{ DateTime, LocalDate }
import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.common.message.model.MessagesCount
import uk.gov.hmrc.http.controllers.RestFormats
import uk.gov.hmrc.mongo.play.json.formats.MongoFormats
import uk.gov.hmrc.securemessage.models.core.{ Letter, RecipientName }

import scala.annotation.tailrec

final case class MessageListItem(
  id: String,
  subject: String,
  taxpayerName: Option[RecipientName],
  validFrom: LocalDate,
  issueDate: Option[LocalDate],
  readTime: Option[DateTime],
  replyTo: Option[String] = None,
  sentInError: Boolean,
  messageType: Option[String] = None,
  counter: Option[Int] = None
)

object MessageListItem extends RestFormats {
  implicit val objectIdFormat: Format[ObjectId] = MongoFormats.objectIdFormat
  implicit val messageListItemWrites: Writes[MessageListItem] = Json.writes[MessageListItem]

  def from(message: Letter): MessageListItem =
    MessageListItem(
      message._id.toString,
      message.subject,
      message.alertDetails.recipientName,
      message.validFrom,
      message.body.flatMap(_.issueDate),
      message.readTime,
      message.body.flatMap(_.replyTo),
      sentInError = message.rescindment.isDefined,
      message.body.flatMap(_.`type`)
    )
}

final case class MessagesResponse(items: Option[Seq[MessageListItem]], count: MessagesCount) {

  def toConversations: MessagesResponse = {

    // algorithms supports only one child per parent
    // and assumes that child's id is greater than parent's one.
    def addCounter(xs: List[MessageListItem]): List[MessageListItem] = {
      @tailrec
      def addCounterAux(input: List[MessageListItem], result: List[MessageListItem]): List[MessageListItem] =
        input match {
          case Nil                             => result
          case x :: xs if !x.replyTo.isDefined => addCounterAux(xs, result)
          case x :: xs =>
            val (beforeChild, afterChild) = result.span(_.id != x.id)
            val (beforeParent, afterParent) = afterChild.span(_.id.toString != x.replyTo.get)
            val parentCount = afterParent.headOption.map(_.counter.getOrElse(1)).getOrElse(0)
            addCounterAux(
              xs,
              beforeChild ++ (x.copy(counter = Some(parentCount + 1)) :: beforeParent.drop(1))
                ++ afterParent.drop(1)
            )
        }
      val input = xs.sortWith(_.id.toString < _.id.toString)
      val result = xs.sortWith(_.id.toString > _.id.toString)
      addCounterAux(input, result)
    }

    items match {
      case Some(msgs) =>
        val (standalones, conversations) = msgs
          .groupBy(m => m.replyTo.getOrElse(m.id.toString))
          .values
          .partition(m => m.size == 1 && !m.head.replyTo.isDefined)
        val msgsWithCounter = (standalones.flatten.toList ++ addCounter(conversations.flatten.toList))
          .sortWith(_.id.toString > _.id.toString)
        MessagesResponse(Some(msgsWithCounter), MessagesCount(msgsWithCounter.size, count.unread))
      case _ => this
    }
  }
}

object MessagesResponse extends RestFormats {

  implicit val messagesResponseWrites: Writes[MessagesResponse] = (
    (__ \ "count").write[MessagesCount] and
      (__ \ "items").writeNullable[Seq[MessageListItem]]
  )(m => (m.count, m.items))

  def fromMessagesCount(count: MessagesCount): MessagesResponse = MessagesResponse(None, count)

  def fromMessages(items: Seq[Letter]): MessagesResponse =
    MessagesResponse(
      Some(items.sortWith(_._id.toString > _._id.toString).map(MessageListItem.from)),
      MessagesCount(
        items.size,
        items.count(message => message.readTime.isEmpty)
      )
    )
}

final case class ErrorResponse(reason: String)

object ErrorResponse {
  implicit val format: Format[ErrorResponse] = Json.format[ErrorResponse]
  implicit val reads: Reads[ErrorResponse] = Json.reads[ErrorResponse]
  implicit val writes: Writes[ErrorResponse] = Json.writes[ErrorResponse]
}
