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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.mongodb.scala.bson.ObjectId
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.common.message.model.ConversationItem
import uk.gov.hmrc.common.message.model.TaxEntity.HmrcCusOrg
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.securemessage.connectors.AuthIdentifiersConnector
import uk.gov.hmrc.securemessage.helpers.MessageUtil
import uk.gov.hmrc.securemessage.models.core.{ Details, Identifier, Letter, Recipient }

import java.time.{ Instant, LocalDate }
import scala.concurrent.{ ExecutionContext, Future }

class MessageV3ServiceSpec extends PlaySpec with ScalaFutures with TestHelpers {
  "formatMessageContent" must {
    "return subject and issueDate from message content only once, if subject is also included in body" in {
      val letter = MessageUtil.getMessage(
        subject = "File your next Self Assessment tax return",
        content = "\\n\\n\\n\\n\\n\\n\\n\\t<h2>\\n\\tFile your next Self Assessment tax return\\n</h2>\\n\\t\\n\\t" +
          "<p class=\\\"message_time faded-text--small\\\">This message was sent to you on 6 April 2018</p>\\n\\n\\t\\n\\n\\t\\n\\n<" +
          "div class=\\\"alert alert--info alert--info__light\\\">\\n ",
        validFrom = LocalDate.now(),
        readTime = Instant.now
      )

      val mockIdentifierConnector = mock[AuthIdentifiersConnector]

      val v3Service = new MessageV3Service {
        override val authIdentifiersConnector: AuthIdentifiersConnector = mockIdentifierConnector

        override def getLetter(id: ObjectId)(implicit ec: ExecutionContext): Future[Option[Letter]] =
          Future.successful(None)
      }
      val messageContent = v3Service.formatMessageContent(letter)
      val subject = letter.subject.r.findAllMatchIn(messageContent).toList
      val issueDateText = messageContent.split("This message was sent to you on").length - 1

      subject.toString() must include("File your next Self Assessment tax return")
      subject.length mustBe 1
      issueDateText mustBe 1
    }

    "return subject and issueDate with message content, if same subject is not included in body" in {
      val letter = MessageUtil.getMessage(
        subject = "some other subject text",
        content = "\\n\\n\\n\\n\\n\\n\\n\\t<h2>\\n\\tFile your next Self Assessment tax return\\n</h2>\\n\\t\\n\\t" +
          "<p class=\\\"message_time faded-text--small\\\">This message was sent to you on 6 April 2018</p>\\n\\n\\t\\n\\n\\t\\n\\n<" +
          "div class=\\\"alert alert--info alert--info__light\\\">\\n ",
        validFrom = LocalDate.now(),
        readTime = Instant.now
      )

      val mockIdentifierConnector = mock[AuthIdentifiersConnector]

      val v3Service = new MessageV3Service {
        override val authIdentifiersConnector: AuthIdentifiersConnector = mockIdentifierConnector

        override def getLetter(id: ObjectId)(implicit ec: ExecutionContext): Future[Option[Letter]] =
          Future.successful(None)
      }
      val messageContent = v3Service.formatMessageContent(letter)
      val subject = letter.subject.r.findAllMatchIn(messageContent).toList

      subject.toString() must include("some other subject text")
      messageContent must include("File your next Self Assessment tax return")
      messageContent must include("This message was sent to you on 6 April 2018")
    }
  }

  "getMessageListResponse" must {
    "return conversationItems from message list" in {
      implicit val hc: HeaderCarrier = HeaderCarrier()
      implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
      val letter: Letter = MessageUtil
        .getMessage(
          subject = "Two Way message subject",
          content = "\\n\\n\\n\\n\\n\\n\\n\\t<h2>\\n\\tTwo Way message content\\n</h2>\\n\\t\\n\\t" +
            "<p class=\\\"message_time faded-text--small\\\">This message was sent to you on 6 April 2018</p>\\n\\n\\t\\n\\n\\t\\n\\n<" +
            "div class=\\\"alert alert--info alert--info__light\\\">\\n "
        )
        .copy(recipient = Recipient("cds", Identifier("HMRC-CUS-ORG", "example eori", None), None))

      val mockIdentifierConnector = mock[AuthIdentifiersConnector]

      val v3Service = new MessageV3Service {
        override val authIdentifiersConnector: AuthIdentifiersConnector = mockIdentifierConnector

        override def getLetter(id: ObjectId)(implicit ec: ExecutionContext): Future[Option[Letter]] =
          Future.successful(Some(letter))
      }

      when(mockIdentifierConnector.currentEffectiveTaxIdentifiers(any[HeaderCarrier]))
        .thenReturn(Future.successful(Set(HmrcCusOrg("example eori"))))

      val response = v3Service.findMessageListById(letter._id.toString).futureValue

      response mustBe Right(
        List(
          ConversationItem(
            letter._id.toString,
            letter.subject,
            None,
            letter.validFrom,
            letter.content
          )
        )
      )
    }
  }
}
