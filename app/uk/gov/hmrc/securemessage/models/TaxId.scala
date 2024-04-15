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

package uk.gov.hmrc.securemessage.models

import play.api.libs.functional.syntax._
import play.api.libs.json.{ Format, Json, Reads, __ }

case class TaxId(_id: String, sautr: Option[String], nino: Option[String], hmrcMtdItsa: Option[String] = None)

object TaxId {
  implicit val formats: Format[TaxId] = {
    val taxIdReads: Reads[TaxId] = ((__ \ "_id").read[String] and
      (__ \ "sautr").readNullable[String] and
      (__ \ "nino").readNullable[String] and
      (__ \ "HMRC-MTD-IT").readNullable[String])((_id, sautr, nino, hmrcMtdItsa) =>
      TaxId(_id, sautr, nino, hmrcMtdItsa)
    )
    Format(taxIdReads, Json.writes[TaxId])
  }

}
