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

package uk.gov.hmrc.securemessage.controllers.utils

import org.apache.commons.codec.binary.Base64
import org.scalatest.EitherValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import uk.gov.hmrc.securemessage.controllers.model.MessageType
import uk.gov.hmrc.securemessage.handlers.{ CDS, NonCDS }

class IdCoderSpec extends AnyFreeSpec with Matchers with EitherValues {

  val id = "6086dc1f4700009fed2f5745"
  "decodeId should" - {
    "return messageType letter and id" in {
      val nakedPath = "letter/" + id
      val path = encodedPath(nakedPath)

      IdCoder.decodeId(path) mustBe Right((MessageType.Letter, id, CDS))
    }
    "return messageType conversation and id" in {
      val nakedPath = "conversation/" + id
      val path = encodedPath(nakedPath)
      IdCoder.decodeId(path) mustBe Right((MessageType.Conversation, id, CDS))
    }
    "return only messageType and Id" in {
      val nakedPath = "conversation/" + id + "/test"
      val path = encodedPath(nakedPath)
      IdCoder.decodeId(path) mustBe Right((MessageType.Conversation, id, CDS))
    }

    "return the whole path as 'id' for non-CDS quries" in {
      val nakedPath = "123456"
      val path = encodedPath(nakedPath)
      IdCoder.decodeId(path) mustBe Right((MessageType.Letter, nakedPath, NonCDS))
    }

    "return InvalidPath if path is not valid" in {
      val nakedPath = "abc/def/12345"
      val path = encodedPath(nakedPath)
      IdCoder
        .decodeId(path)
        .left
        .value
        .message mustBe "Invalid encoded id: YWJjL2RlZi8xMjM0NQ==, decoded string: abc/def/12345"
    }

  }

  "encodeId should" - {
    "encode message type and id" in {
      IdCoder.encodeId(MessageType.Conversation, id) mustBe encodedPath(s"conversation/$id")
    }
    "encode message type with lower case" in {
      val result = IdCoder.encodeId(MessageType.Conversation, id)
      decodePath(result) mustBe s"conversation/$id"
    }
  }

  protected def encodedPath(path: String): String = Base64.encodeBase64String(path.getBytes("UTF-8"))
  protected def decodePath(path: String): String = new String(Base64.decodeBase64(path))
}
