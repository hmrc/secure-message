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

import uk.gov.hmrc.securemessage.SpecBase
import play.api.libs.json.{ JsResultException, Json }
import uk.gov.hmrc.securemessage.controllers.model.cdcm.write.CdcmNotificationType.CDSExports

class CdcmConversationSpec extends SpecBase {

  "CdcmSystem.systemReads" must {
    import CdcmSystem.systemReads

    "read the json correctly" in new Setup {
      Json.parse(cdcmSystemJsonString).as[CdcmSystem] mustBe cdcmSystem
    }

    "throw the exception for invalid json" in new Setup {
      intercept[JsResultException] {
        Json.parse(cdcmSystemInvalidJsonString).as[CdcmSystem]
      }
    }
  }

  "CdcmTags.tagsFormats" must {
    import CdcmTags.tagsFormats

    "read the json correctly" in new Setup {
      Json.parse(cdcmTagsJsonString).as[CdcmTags] mustBe cdcmTags
    }

    "throw the exception for invalid json" in new Setup {
      intercept[JsResultException] {
        Json.parse(cdcmTagsInvalidJsonString).as[CdcmTags]
      }
    }

    "write the object correctly" in new Setup {
      Json.toJson(cdcmTags) mustBe Json.parse(cdcmTagsJsonString)
    }
  }

  trait Setup {
    val testSystem = "test"
    val testMrn = "test_mrn"
    val testEntryName = "test_entry"

    val cdcmSystem: CdcmSystem = CdcmSystem(testSystem)
    val cdcmTags: CdcmTags = CdcmTags(mrn = testMrn, notificationType = CDSExports)

    val cdcmSystemJsonString: String = """{"display":"test"}""".stripMargin
    val cdcmSystemInvalidJsonString: String = """{}""".stripMargin

    val cdcmTagsJsonString: String = """{"mrn":"test_mrn","notificationType":"CDS-EXPORTS"}""".stripMargin
    val cdcmTagsInvalidJsonString: String = """{}""".stripMargin
  }
}
