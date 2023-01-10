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

package uk.gov.hmrc.securemessage.controllers.binders

import org.scalatestplus.play._
import play.api.mvc.QueryStringBindable
import uk.gov.hmrc.common.message.model.Regime
import uk.gov.hmrc.securemessage.models.core.{ CustomerEnrolment, FilterTag, MessageFilter }

class PackageTest extends PlaySpec {
  "queryStringBindableCustomerEnrolment" must {
    "return None if Enrolment not in the params" in {
      queryStringBindableCustomerEnrolment.bind("", Map.empty) mustBe None
    }

    "return CustomerEnrolment if Enrolment in the params" in {
      queryStringBindableCustomerEnrolment.bind("", Map("enrolment" -> List("HMRC~EORI~EORIVAL"))) mustBe
        Some(Right(CustomerEnrolment("HMRC", "EORI", "EORIVAL")))
    }

    "return an error if the enrolment parameter is incorrectly formatted" in {
      queryStringBindableCustomerEnrolment.bind("", Map("enrolment" -> List("HMRCEORIEORIVAL"))) mustBe
        Some(Left("Unable to bind a CustomerEnrolment"))
    }

    "unbind a CustomerEnrolment" in {
      queryStringBindableCustomerEnrolment
        .unbind("", CustomerEnrolment("HMRC", "EORI", "EORIVAL")) mustBe "HMRC~EORI~EORIVAL"
    }
  }

  "queryStringBindableTag" must {
    "return None if tag not in the params" in {
      queryStringBindableTag.bind("", Map.empty) mustBe None
    }
    "return FilterTag if tag in the params" in {
      queryStringBindableTag.bind("", Map("tag" -> List("notifcation~DirectDEBIT"))) mustBe
        Some(Right(FilterTag("notifcation", "DirectDEBIT")))
    }

    "return an error if the tag enrolment parameter is incorrectly formatted" in {
      queryStringBindableTag.bind("", Map("tag" -> List("notifcation~type~DirectDEBIT"))) mustBe
        Some(Left("Unable to bind a Tag"))
    }

    "parse a FilterTag" in {
      queryStringBindableTag.unbind("", FilterTag("notifcation", "DirectDEBIT")) mustBe "notifcation~DirectDEBIT"
    }

  }

  "messageFilterBinder" must {

    val testBinder = implicitly[QueryStringBindable[MessageFilter]]

    "bind map with all paremeters present" in {
      testBinder.bind(
        "key",
        Map("taxIdentifiers" -> Seq("foo", "bar"), "regimes" -> Seq("fhdds"), "countOnly" -> Seq("true"))) must be(
        Some(Right(MessageFilter(List("foo", "bar"), List(Regime.fhdds)))))
    }
    "bind  with missing countOnly" in {
      testBinder.bind("key", Map("taxIdentifiers" -> Seq("foo", "bar"), "regimes" -> Seq("fhdds"))) must be(
        Some(Right(MessageFilter(List("foo", "bar"), List(Regime.fhdds)))))
    }
    "bind  with missing taxIdentifiers " in {
      testBinder.bind("key", Map("regimes" -> Seq("fhdds"), "countOnly" -> Seq("true"))) must be(
        Some(Right(MessageFilter(List(), List(Regime.fhdds)))))
    }
    "bind  with missing regimes" in {
      testBinder.bind("key", Map("taxIdentifiers" -> List("foo", "bar"), "countOnly" -> Seq("true"))) must be(
        Some(Right(MessageFilter(List("foo", "bar"), List()))))
    }

    "bind with wrong regime" in {
      testBinder.bind("key", Map("regime" -> List("wrong-regime"))) must be(Some(Right(MessageFilter(List(), List()))))
    }

  }

}
