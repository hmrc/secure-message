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

class CancellableOperationSpec extends AnyWordSpec with Matchers {

  private val cop: CancellableOperation = new CancellableOperation {
    var cancelled = false
    override def isCancelled: Boolean = cancelled
    override def cancel(): Boolean = { cancelled = true; true }
  }

  "CancellableOperation" should {

    "return false initially for isCancelled" in {
      cop.isCancelled mustBe false
    }

    "update cancellation state when cancel() is invoked" in {
      cop.cancel()
      cop.isCancelled mustBe true
    }
  }
}
