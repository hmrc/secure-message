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

package uk.gov.hmrc.securemessage.models

import uk.gov.hmrc.securemessage.SpecBase
import uk.gov.hmrc.securemessage.TestData.TEST_URL

class JourneyStepSpec extends SpecBase {

  "toJourneyStep" must {

    "return the correct Journey Step" when {

      "step is link" in {
        SecureMessageUrlStep.toJourneyStep("link", Some(TEST_URL)) mustBe Some(Right(ShowLinkJourneyStep(TEST_URL)))
      }

      "step is form" in {
        SecureMessageUrlStep.toJourneyStep("form", Some(TEST_URL)) mustBe Some(Right(ReplyFormJourneyStep(TEST_URL)))
      }

      "step is ack" in {
        SecureMessageUrlStep.toJourneyStep("ack", None) mustBe Some(Right(AckJourneyStep))
      }
    }

    "throw error" when {
      "step is unknown" in {
        SecureMessageUrlStep.toJourneyStep("unknown", Some(TEST_URL)) mustBe Some(
          Left("Unknown step value provided: unknown")
        )
      }
    }
  }
}
