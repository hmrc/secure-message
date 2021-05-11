/*
 * Copyright 2021 HM Revenue & Customs
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
import org.scalatest.{ FreeSpec, MustMatchers }
import uk.gov.hmrc.securemessage.controllers.model.MessageType

class IdCoderSpec extends FreeSpec with MustMatchers {

  "decodeId should" - {
    "decode a valid encoded id" in {}
    "return error for decoded String without separator" in {}

    "return error for invalid message type" in {}

    "return error for empty id" in {}

    "return messageType letter and id" in {
      val nakedPath = "letter/6086dc1f4700009fed2f5745"
      val path = encodedPath(nakedPath)

      IdCoder.decodeId(path).right.get mustBe MessageType.Letter -> "6086dc1f4700009fed2f5745"
    }
    "return messageType conversation and id" in {
      val nakedPath = "conversation/6086dc1f4700009fed2f5745"
      val path = encodedPath(nakedPath)
      IdCoder.decodeId(path).right.get mustBe MessageType.Conversation -> "6086dc1f4700009fed2f5745"
    }
    "return only messageType and Id" in {
      val nakedPath = "conversation/6086dc1f4700009fed2f5745/test"
      val path = encodedPath(nakedPath)
      IdCoder.decodeId(path).right.get mustBe MessageType.Conversation -> "6086dc1f4700009fed2f5745"
    }
    "return InvalidPath if path is not valid" in {
      val nakedPath = "123456"
      val path = encodedPath(nakedPath)
      IdCoder.decodeId(path).left.get.message mustBe "Invalid encoded id: MTIzNDU2, decoded string: 123456"
    }

  }

  "encodeId should" - {
    "encode message type and id" in {
      IdCoder.encodeId(MessageType.Conversation, "a4b5c4") mustBe "Q29udmVyc2F0aW9uL2E0YjVjNA=="
    }
  }

  protected def encodedPath(path: String): String = Base64.encodeBase64String(path.getBytes("UTF-8"))
}
