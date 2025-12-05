/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.securemessage.scheduler.cancellable

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.must.Matchers

class CancellableOperationStateSpec extends AnyWordSpec with Matchers {

  private val cop: CancellableOperationState = new CancellableOperationState {}

  "CancellableOperationState" should {

    "start as not cancelled" in {
      cop.isCancelled mustBe false
    }

    "become cancelled when cancel() is invoked" in {
      cop.cancel() mustBe true
      cop.isCancelled mustBe true
    }

    "remain cancelled when cancel() is called multiple times" in {
      cop.cancel()
      cop.cancel()
      cop.isCancelled mustBe true
    }
  }
}
