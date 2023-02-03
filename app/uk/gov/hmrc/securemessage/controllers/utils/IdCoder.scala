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
import uk.gov.hmrc.securemessage.controllers.model.MessageType
import uk.gov.hmrc.securemessage.controllers.model.MessageType.Letter
import uk.gov.hmrc.securemessage.handlers.{ CDS, NonCDS, RetrieverType }
import uk.gov.hmrc.securemessage.{ InvalidRequest, SecureMessageError }

import java.nio.charset.StandardCharsets

object IdCoder {
  type EncodedId = String
  type DecodedId = String

  private[controllers] def decodeId(
    encodedId: EncodedId): Either[SecureMessageError, (MessageType, DecodedId, RetrieverType)] = {
    val decodedString = new String(Base64.decodeBase64(encodedId.getBytes(StandardCharsets.UTF_8)))
    def isMessageType(messageType: EncodedId) = MessageType.withNameOption(messageType).isDefined
    decodedString.split("/").toList match {
      case messageType :: id :: _ if (isMessageType(messageType) && id.trim.nonEmpty) =>
        Right((MessageType.withName(messageType), id, CDS))
      case l: List[String] if l.size == 1 =>
        Right((MessageType.withName(Letter.entryName), l.head, NonCDS))
      case _ => Left(InvalidRequest(s"Invalid encoded id: $encodedId, decoded string: $decodedString"))
    }
  }

  private[controllers] def encodeId(messageType: MessageType, id: DecodedId): EncodedId = {
    val decodedString: DecodedId = messageType.entryName + "/" + id
    Base64.encodeBase64String(decodedString.getBytes(StandardCharsets.UTF_8))
  }
}
