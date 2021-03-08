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

package utils

import play.api.libs.json.{ JsValue, Json }
import uk.gov.hmrc.securemessage.models.core

object ConversationUtil {
  val alert: core.Alert = core.Alert("emailTemplateId", Some(Map("param1" -> "value1", "param2" -> "value2")))

  def getConversationRequest(content: String): JsValue =
    Json.parse(s"""{
                  |      "sender": {"system": {
                  |        "identifier": {
                  |        "name": "CDCM",
                  |        "value": "D-80542-20201120"
                  |      },
                  |        "display": "CDS Exports Team"
                  |      }},
                  |      "recipients": [{"customer": {
                  |      "enrolment": {
                  |      "key": "HMRC-CUS-ORG",
                  |      "name": "EORINumber",
                  |      "value": "GB1234567890"
                  |    },
                  |      "email": "jobloggs@test.com"
                  |    }}],
                  |      "alert": {"templateId": "emailTemplateId"},
                  |      "subject": "D-80542-20201120",
                  |      "message": "$content"
                  |    }""".stripMargin)

  def getCaseWorkerMessage(content: String): JsValue =
    Json.parse(s"""
                  |{
                  |  "sender":{
                  |    "system":{
                  |      "display":"ss",
                  |      "identifier":{
                  |        "name":"CDCM",
                  |        "value":"D-80542-20201120"
                  |      }
                  |    }
                  |  },
                  |  "content": "$content"
                  |}
                  |""".stripMargin)
}
