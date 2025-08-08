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

package uk.gov.hmrc.securemessage.controllers.model.cdcm.write

import play.api.libs.json.*

enum CdcmNotificationType(val entryName: String) {
  case CDSExports extends CdcmNotificationType("CDS-EXPORTS")
  case CDSImports extends CdcmNotificationType("CDS-IMPORTS")
}

object CdcmNotificationType {

  implicit val reads: Reads[CdcmNotificationType] = Reads {
    case JsString(value) =>
      withNameOption(value) match
        case Some(nt) => JsSuccess(nt)
        case None     => JsError(s"Unknown CdcmNotificationType: $value")
    case _ => JsError("CdcmNotificationType must be a string")
  }

  implicit val writes: Writes[CdcmNotificationType] = Writes { nt =>
    JsString(nt.entryName)
  }

  implicit val format: Format[CdcmNotificationType] = Format(reads, writes)

  def withNameOption(name: String): Option[CdcmNotificationType] =
    values.find(_.entryName == name)
}
