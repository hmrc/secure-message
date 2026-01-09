/*
 * Copyright 2026 HM Revenue & Customs
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

import play.api.libs.json.{ JsResultException, Json }
import uk.gov.hmrc.common.message.model
import uk.gov.hmrc.common.message.model.Regime
import uk.gov.hmrc.securemessage.SpecBase
import uk.gov.hmrc.common.message.model.Regime.{ paye, sa }

class MessageFilterSpec extends SpecBase {

  "Json Reads" should {
    import MessageFilter.messageFilterReads

    "read the json correctly" in new Setup {
      Json.parse(messageFilterJsonString).as[MessageFilter] mustBe messageFilter
    }

    "throw exception for the invalid json" in new Setup {
      intercept[JsResultException] {
        Json.parse(messageFilterInvalidJsonString).as[MessageFilter]
      }
    }
  }

  trait Setup {
    val taxIdentifiersList: Seq[String] = Seq("test_1", "test_2")
    val regimes: List[model.Regime.Value] = List(paye, sa)

    val messageFilter: MessageFilter = MessageFilter(taxIdentifiers = taxIdentifiersList, regimes = regimes)

    val messageFilterJsonString: String =
      """{"taxIdentifiers":["test_1","test_2"],"regimes":["paye", "sa"]}""".stripMargin

    val messageFilterInvalidJsonString: String = """{"regimes":"test"}""".stripMargin
  }
}
