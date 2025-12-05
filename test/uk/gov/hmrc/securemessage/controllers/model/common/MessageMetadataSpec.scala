/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.securemessage.controllers.model.common

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.i18n.{ Lang, Messages, MessagesApi }
import uk.gov.hmrc.securemessage.controllers.model.MessageType
import uk.gov.hmrc.securemessage.models.core.*
import uk.gov.hmrc.common.message.model.{ Language, TaxpayerName }
import uk.gov.hmrc.securemessage.models.v4.{ Content, SecureMessage }
import uk.gov.hmrc.auth.core.{ Enrolment, Enrolments }
import uk.gov.hmrc.securemessage.controllers.model.common.read.MessageMetadata
import uk.gov.hmrc.securemessage.helpers.Resources

import java.time.LocalDate

class MessageMetadataSpec extends AnyWordSpec with Matchers {

  implicit val messagesApi: MessagesApi = stubMessagesApi()
  implicit val messages: Messages = messagesApi.preferred(Seq(Lang("en")))

  "MessageMetadata.apply(Message)(implicit language)" should {

    "map a Letter correctly" in {
      implicit val lang: Language = Language.English

      val letter: Letter = Resources.readJson("model/core/full-db-letter.json").as[Letter]

      val result: MessageMetadata = MessageMetadata(letter)

      result.messageType shouldBe MessageType.Letter
      result.id shouldBe "609a5bd50100006c1800272d"
      result.validFrom shouldBe Some(LocalDate.parse("2021-04-26"))
      result.subject shouldBe "Test have subjects11"
    }

    "map a SecureMessage correctly" in {
      implicit val lang: Language = Language.English

      val sm: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]

      val result = MessageMetadata(sm)

      result.messageType shouldBe MessageType.Letter
      result.subject shouldBe "Reminder to file a Self Assessment return"
      result.language shouldBe Some("en")
      result.unreadMessages shouldBe true
    }
  }

  "MessageMetadata.apply(Message, reader, language)" should {

    "map a Conversation correctly" in {
      val lang: Language = Language.English

      val readerEnrolments = Enrolments(Set(Enrolment("HMRC-NI", Seq.empty, "active")))

      val conversation: Conversation = Resources.readJson("model/core/conversation.json").as[Conversation]

      val result = MessageMetadata(conversation, readerEnrolments, lang)

      result.messageType shouldBe MessageType.Conversation
      result.subject shouldBe "MRN: 19GB4S24GC3PPFGVR7"
      result.id should not be empty
    }
  }

  "contentForLanguage" should {

    "return content for matching language" in {
      val c = Content(Language.English, "subject", "body")
      val result = MessageMetadata.contentForLanguage(Language.English, List(c))

      result shouldBe Some(c)
    }

    "fallback to headOption when language not found" in {
      val c = Content(Language.English, "subject", "body")
      val result = MessageMetadata.contentForLanguage(Language.Welsh, List(c))

      result shouldBe Some(c)
    }
  }

  "taxpayerNameToRecipientName" should {

    "convert TaxpayerName to RecipientName correctly" in {
      val t = TaxpayerName(
        title = Some("Mr"),
        forename = Some("John"),
        secondForename = Some("A"),
        surname = Some("Smith"),
        honours = None,
        line1 = Some("Line 1")
      )

      val r = MessageMetadata.taxpayerNameToRecipientName(t)

      r.forename shouldBe Some("John")
      r.surname shouldBe Some("Smith")
      r.line1 shouldBe Some("Line 1")
    }
  }

  private def stubMessagesApi(): MessagesApi =
    new play.api.i18n.DefaultMessagesApi()
}
