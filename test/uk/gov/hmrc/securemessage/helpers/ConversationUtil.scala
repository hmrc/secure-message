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

package uk.gov.hmrc.securemessage.helpers

import cats.data.NonEmptyList
import org.joda.time.DateTime
import org.mongodb.scala.bson.ObjectId
import uk.gov.hmrc.emailaddress.EmailAddress
import uk.gov.hmrc.securemessage.controllers.model.cdcm.write.CdcmConversation
import uk.gov.hmrc.securemessage.controllers.model.common.write.{ Customer, Recipient }
import uk.gov.hmrc.securemessage.models.core
import uk.gov.hmrc.securemessage.models.core.Language.English
import uk.gov.hmrc.securemessage.models.core.{ CustomerEnrolment, _ }

object ConversationUtil {
  val alert: core.Alert = core.Alert("emailTemplateId", Some(Map("param1" -> "value1", "param2" -> "value2")))
  val base64Content: String = "QmxhaCBibGFoIGJsYWg="

  def getConversationRequestWithMultipleCustomers: CdcmConversation = {
    val cnv: CdcmConversation = Resources.readJson("model/api/cdcm/write/create-conversation.json").as[CdcmConversation]
    cnv.copy(
      recipients = Recipient(
        Customer(
          CustomerEnrolment("HMRC-CUS-ORG", "EORINumber", "GB1234567890")
        )) :: cnv.recipients)
  }
  def getFullConversation(
    objectId: ObjectId = new ObjectId(),
    id: String,
    enrolmentKey: String,
    enrolmentName: String,
    enrolmentValue: String,
    tags: Option[Map[String, String]] = Some(
      Map(
        "mrn"              -> "DMS7324874993",
        "notificationType" -> "CDS-EXPORTS"
      )),
    alert: core.Alert = alert,
    messageCreationDate: String = "2020-11-10T15:00:01.000",
    readTimes: Option[List[DateTime]] = None,
    email: Option[EmailAddress] = None
  ): Conversation =
    Conversation(
      objectId,
      "CDCM",
      id,
      ConversationStatus.Open,
      tags,
      "MRN: 19GB4S24GC3PPFGVR7",
      English,
      List(
        Participant(
          1,
          ParticipantType.System,
          Identifier("CDCM", "D-80542-20201120", None),
          Some("CDS Exports Team"),
          None,
          None,
          None
        ),
        Participant(
          2,
          ParticipantType.Customer,
          Identifier(enrolmentName, enrolmentValue, Some(enrolmentKey)),
          None,
          email,
          None,
          readTimes
        )
      ),
      NonEmptyList.one(
        ConversationMessage(
          Some("6e78776f-48ff-45bd-9da2-926e35519803"),
          1,
          new DateTime(messageCreationDate),
          base64Content,
          Some(Reference(typeName = "X-Request-ID", value = "adsgr24frfvdc829r87rfsdf=="))
        )
      ),
      alert
    )

  def getMinimalConversation(id: String, objectId: ObjectId = new ObjectId()): Conversation =
    Conversation(
      objectId,
      "cdcm",
      id,
      ConversationStatus.Open,
      None,
      "D-80542-20201120",
      English,
      List(
        Participant(
          1,
          ParticipantType.System,
          Identifier("CDCM", "D-80542-20201120", None),
          Some("CDS Exports Team"),
          None,
          None,
          None),
        Participant(
          2,
          ParticipantType.Customer, //GB1234567890
          Identifier("EORINumber", "GB1234567890", Some("HMRC-CUS-ORG")),
          None,
          None,
          None,
          None)
      ),
      NonEmptyList.one(
        ConversationMessage(
          None,
          1,
          new DateTime("2020-11-10T15:00:01.000"),
          base64Content,
          None
        )
      ),
      alert.copy(parameters = None)
    )

}
