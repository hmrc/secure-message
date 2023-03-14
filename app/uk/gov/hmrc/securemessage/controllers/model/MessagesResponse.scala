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

package uk.gov.hmrc.securemessage.controllers.model

import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.common.message.model.MessagesCount
import uk.gov.hmrc.http.controllers.RestFormats
import uk.gov.hmrc.securemessage.controllers.model.common.read.MessageMetadata
import uk.gov.hmrc.securemessage.models.core.{ Language, Message }

import scala.annotation.tailrec

final case class MessagesResponse(items: Option[Seq[MessageMetadata]], count: MessagesCount) {

  def toConversations: MessagesResponse = {

    // algorithms supports only one child per parent
    // and assumes that child's id is greater than parent's one.
    def addCounter(xs: List[MessageMetadata]): List[MessageMetadata] = {
      @tailrec
      def addCounterAux(input: List[MessageMetadata], result: List[MessageMetadata]): List[MessageMetadata] =
        input match {
          case Nil                          => result
          case x :: xs if x.replyTo.isEmpty => addCounterAux(xs, result)
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

      val input = xs.sortWith(_.id < _.id)
      val result = xs.sortWith(_.id > _.id)
      addCounterAux(input, result)
    }

    items match {
      case Some(msgs) =>
        val (standalones, conversations) = msgs
          .groupBy(m => m.replyTo.getOrElse(m.id))
          .values
          .partition(m => m.size == 1 && m.head.replyTo.isEmpty)
        val msgsWithCounter = (standalones.flatten.toList ++ addCounter(conversations.flatten.toList))
          .sortWith(_.id > _.id)
        MessagesResponse(Some(msgsWithCounter), MessagesCount(msgsWithCounter.size, count.unread))
      case _ => this
    }
  }
}

object MessagesResponse extends RestFormats {

  implicit val messagesResponseWrites: Writes[MessagesResponse] = (
    (__ \ "count").write[MessagesCount] and
      (__ \ "items").writeNullable[Seq[MessageMetadata]]
  )(m => (m.count, m.items))

  def fromMessagesCount(count: MessagesCount): MessagesResponse = MessagesResponse(None, count)

  def fromMessages[A <: Message](items: Seq[A], language: Language): MessagesResponse =
    MessagesResponse(
      Some(items.sortWith(_._id.toString > _._id.toString).map(MessageMetadata(_)(language))),
      MessagesCount(
        items.size,
        items.count(message => message.readTime.isEmpty)
      )
    )
}
