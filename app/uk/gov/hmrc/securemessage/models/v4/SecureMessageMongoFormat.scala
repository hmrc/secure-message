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

package uk.gov.hmrc.securemessage.models.v4

import java.time.{ Instant, LocalDate }
import org.mongodb.scala.bson.ObjectId
import play.api.libs.json.{ Format, JsError, JsSuccess, Json, OFormat, Reads, Writes, __ }
import uk.gov.hmrc.common.message.model.TaxEntity.{ Epaye, HmceVatdecOrg, HmrcCusOrg, HmrcIossOrg, HmrcPodsOrg, HmrcPodsPpOrg, HmrcPptOrg }
import uk.gov.hmrc.common.message.model.EmailAlert
import uk.gov.hmrc.domain.{ SerialisableTaxId, TaxIds }
import uk.gov.hmrc.domain.TaxIds.TaxIdWithName
import uk.gov.hmrc.mongo.play.json.formats.{ MongoFormats, MongoJavatimeFormats }
import uk.gov.hmrc.mongo.workitem.ProcessingStatus
import play.api.libs.functional.syntax._

object SecureMessageMongoFormat {

  // ProcessingStatus
  implicit val format: Format[ProcessingStatus] = ProcessingStatus.format

  // LocalDate
  implicit val localDateFormat: Format[LocalDate] = MongoJavatimeFormats.localDateFormat
  // ObjectId
  implicit val objectIdFormat: Format[ObjectId] = MongoFormats.objectIdFormat

  implicit val dateTimeFormat: Format[Instant] = MongoJavatimeFormats.instantFormat
  implicit val emailAlertFormat: OFormat[EmailAlert] = Json.format[EmailAlert]

  val taxIdentifierReads: Reads[TaxIdWithName] =
    ((__ \ "name").read[String] and (__ \ "value").read[String]).tupled.flatMap[TaxIdWithName] { case (name, value) =>
      (TaxIds.defaultSerialisableIds :+ SerialisableTaxId("EMPREF", Epaye.apply)
        :+ SerialisableTaxId("HMCE-VATDEC-ORG", HmceVatdecOrg.apply)
        :+ SerialisableTaxId("HMRC-CUS-ORG", HmrcCusOrg.apply)
        :+ SerialisableTaxId("HMRC-IOSS-ORG", HmrcIossOrg.apply)
        :+ SerialisableTaxId("ETMPREGISTRATIONNUMBER", HmrcPptOrg.apply)
        :+ SerialisableTaxId("PSAID", HmrcPodsOrg.apply)
        :+ SerialisableTaxId("PSPID", HmrcPodsPpOrg.apply))
        .find(_.taxIdName == name)
        .map(_.build(value)) match {
        case Some(taxIdWithName) =>
          Reads[TaxIdWithName] { _ =>
            JsSuccess(taxIdWithName)
          }
        case None =>
          Reads[TaxIdWithName] { _ =>
            JsError(s"could not determine tax id with name = $name and value = $value")
          }
      }
    }

  val taxIdentifierWrites = Writes[TaxIdWithName] { taxId =>
    Json.obj("name" -> taxId.name, "value" -> taxId.value)
  }

  implicit val mongoTaxIdentifierFormat: Format[TaxIdWithName] =
    Format(taxIdentifierReads, taxIdentifierWrites)
  // SecureMessage
  implicit val mongoMessageFormat: OFormat[SecureMessage] = Json.format[SecureMessage]
}
