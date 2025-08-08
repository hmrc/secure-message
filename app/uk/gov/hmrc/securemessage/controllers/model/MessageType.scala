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

import play.api.libs.json.*

enum MessageType {
  case Conversation, Letter
  def entryName: String = this.toString.toLowerCase
}

object MessageType {

  def withName(name: String): MessageType =
    values
      .find(_.entryName == name)
      .getOrElse(throw new NoSuchElementException(s"$name is not a valid MessageType"))

  def withNameOption(name: String): Option[MessageType] =
    values.find(_.entryName == name)

  implicit val reads: Reads[MessageType] = Reads {
    case JsString(value) =>
      values.find(_.toString.equalsIgnoreCase(value)) match {
        case Some(mt) => JsSuccess(mt)
        case None     => JsError(s"Unknown MessageType: $value")
      }
    case _ => JsError("MessageType must be a string")
  }

  implicit val writes: Writes[MessageType] = Writes { mt =>
    JsString(mt.toString.toLowerCase)
  }

  implicit val format: Format[MessageType] =
    Format(reads, writes)
}
