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

import java.time.Duration
import org.scalatestplus.play.PlaySpec
import play.api.Configuration

class ExtraAlertConfigSpec extends PlaySpec {

  "handle empty configuration" in {
    def config: Configuration = Configuration.from(
      Map(
        "alertProfile" -> List()
      )
    )
    ExtraAlertConfig(config) must be(List())
  }

  "handle non-empty configuration" in {
    def config: Configuration = Configuration.from(
      Map(
        "alertProfile.0" ->
          Map(
            "mainTemplate"  -> "main0",
            "extraTemplate" -> "extra0",
            "delay"         -> "2D"
          ),
        "alertProfile.1" ->
          Map(
            "mainTemplate"  -> "main1",
            "extraTemplate" -> "extra1",
            "delay"         -> "2H"
          ),
        "alertProfile.2" ->
          Map(
            "mainTemplate"  -> "main2",
            "extraTemplate" -> "extra2",
            "delay"         -> "2m"
          ),
        "alertProfile.3" ->
          Map(
            "mainTemplate"  -> "main3",
            "extraTemplate" -> "extra3",
            "delay"         -> "2s"
          )
      )
    )
    ExtraAlertConfig(config) must be(
      List(
        ExtraAlertConfig("main0", "extra0", Duration.ofSeconds(2 * 24 * 60 * 60)),
        ExtraAlertConfig("main1", "extra1", Duration.ofSeconds(2 * 60 * 60)),
        ExtraAlertConfig("main2", "extra2", Duration.ofSeconds(2 * 60)),
        ExtraAlertConfig("main3", "extra3", Duration.ofSeconds(2))
      )
    )
  }
}
