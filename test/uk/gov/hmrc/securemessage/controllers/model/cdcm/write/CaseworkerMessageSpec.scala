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

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{ JsSuccess, Json }
import uk.gov.hmrc.securemessage.controllers.model.cdcm.write.CaseworkerMessage.{ Sender, System, SystemIdentifier }
import uk.gov.hmrc.securemessage.models.core.Identifier

import java.util.UUID

class CaseworkerMessageSpec extends AnyWordSpec with Matchers {
  val conversionId = UUID.randomUUID().toString

  "CaseworkerMessage" must {
    "create a sender identifier with correct values" in {
      val message = CaseworkerMessage("some content")
      val identifier = message.senderIdentifier("some-name", conversionId)

      identifier.name mustBe "some-name"
      identifier.value mustBe conversionId
      identifier.enrolment mustBe None
    }

    "deserialize from JSON" in {
      val json = Json.obj("content" -> "some message content")
      val result = json.validate[CaseworkerMessage]

      result mustBe a[JsSuccess[_]]
      result.get.content mustBe "some message content"
    }

  }

  "SystemIdentifier" must {

    "convert to Identifier correctly" in {
      val systemId = SystemIdentifier("some-name", "some-value")
      val identifier = systemId.asIdentifier

      identifier mustBe Identifier("some-name", "some-value", None)
    }

    "correctly deserialize from JSON" in {
      val json = Json.obj("name" -> "some-name", "value" -> "some-value")
      val result = json.validate[SystemIdentifier]

      result mustBe a[JsSuccess[_]]
      result.get.name mustBe "some-name"
      result.get.value mustBe "some-value"
    }
  }

  "System" must {

    "deserialize from JSON with valid identifier" in {
      val json = Json.obj(
        "identifier" -> Json.obj(
          "name"  -> "some-name",
          "value" -> "some-value"
        )
      )
      val result = json.validate[System]

      result mustBe a[JsSuccess[_]]
      result.get.identifier.name mustBe "some-name"
      result.get.identifier.value mustBe "some-value"
    }

    "deserialize complex JSON structure" in {
      val json = Json.obj(
        "identifier" -> Json.obj(
          "name"  -> "HMRC-CUS-ORG",
          "value" -> "GB123456789000"
        )
      )
      val result = json.validate[System]

      result mustBe a[JsSuccess[_]]
      result.get.identifier.name mustBe "HMRC-CUS-ORG"
      result.get.identifier.value mustBe "GB123456789000"
    }
  }

  "Sender" must {

    "correctly deserialize from JSON with valid system" in {
      val json = Json.obj(
        "system" -> Json.obj(
          "identifier" -> Json.obj(
            "name"  -> "some-system",
            "value" -> "some-value"
          )
        )
      )
      val result = json.validate[Sender]

      result mustBe a[JsSuccess[_]]
      result.get.system.identifier.name mustBe "some-system"
      result.get.system.identifier.value mustBe "some-value"
    }

    "deserialize complete sender structure from JSON" in {
      val json = Json.obj(
        "system" -> Json.obj(
          "identifier" -> Json.obj(
            "name"  -> "CDCM",
            "value" -> conversionId
          )
        )
      )
      val result = json.validate[Sender]

      result mustBe a[JsSuccess[_]]
      val sender = result.get
      sender.system.identifier.name mustBe "CDCM"
      sender.system.identifier.value mustBe conversionId
    }
  }

}
