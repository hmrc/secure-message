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

import org.joda.time.{ DateTime, LocalDate }
import org.mongodb.scala.bson.ObjectId
import play.api.libs.json.{ Format, Json, OFormat }
import uk.gov.hmrc.common.message.model.EmailAlert
import uk.gov.hmrc.mongo.play.json.formats.{ MongoFormats, MongoJodaFormats }
import uk.gov.hmrc.mongo.workitem.ProcessingStatus

object SecureMessageMongoFormat {

  import uk.gov.hmrc.common.message.model.MongoTaxIdentifierFormats._

  //ProcessingStatus
  implicit val format: Format[ProcessingStatus] = ProcessingStatus.format

  //LocalDate
  implicit val localDateFormat: Format[LocalDate] = MongoJodaFormats.localDateFormat
  //ObjectId
  implicit val objectIdFormat: Format[ObjectId] = MongoFormats.objectIdFormat

  implicit val dateTimeFormat: Format[DateTime] = MongoJodaFormats.dateTimeFormat
  implicit val emailAlertFormat: OFormat[EmailAlert] = Json.format[EmailAlert]

  //SecureMessage
  implicit val mongoMessageFormat: OFormat[SecureMessage] = Json.format[SecureMessage]
}
