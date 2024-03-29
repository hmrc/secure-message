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

package uk.gov.hmrc.securemessage.models.core
import enumeratum._
import play.api.libs.json._

import scala.collection.immutable

sealed abstract class Language(override val entryName: String) extends EnumEntry

case object Language extends Enum[Language] with PlayJsonEnum[Language] {

  val values: immutable.IndexedSeq[Language] = findValues

  case object English extends Language("en")

  case object Welsh extends Language("cy")

  implicit val languageReads: Reads[Language] = Reads[Language] {
    case JsString(value) => JsSuccess[Language](Language.withNameInsensitiveOption(value).getOrElse(Language.English))
    case _               => JsSuccess[Language](Language.English)
  }
  implicit val languageWrites: Writes[Language] = (e: Language) => JsString(e.entryName)

  implicit val languageFormat: Format[Language] = Format(languageReads, languageWrites)
}
