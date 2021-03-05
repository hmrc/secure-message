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

package uk.gov.hmrc.securemessage.services.utils

import org.scalatestplus.play.PlaySpec

class HtmlValidatorSpec extends PlaySpec {

  "HtmlValidator.isValidHtml" should {
    "return false when an empty string is supplied as the message content" in {
      HtmlValidator.isValidHtml("") mustBe false
    }

    "return false when invalid HTML is supplied" in {
      HtmlValidator.isValidHtml("PG1hdHQ+PGRpdj48cD5hZGFzZGE8L3A+PGRpdj48L21hdHQ+") mustBe false
    }

    "return false when obscure characters are with the HTML that is supplied" in {
      HtmlValidator.isValidHtml("PHRlc3Q+wrDCpsKpwq7SgtaN1o3WjtiO2I/bntup273igJnigJzigJQ8L3Rlc3Q+") mustBe false
    }

    "return true when a simple valid HTML content is supplied" in {
      HtmlValidator.isValidHtml("PHA+SGksIGhvdyBtdWNoIHRheCBkbyBJIG93ZSB0aGlzIG1vbnRoPC9wPg==") mustBe true
    }

    "return true when a valid HTML content is supplied with line breaks" in {
      HtmlValidator.isValidHtml("PGgxPk15IEZpcnN0IEhlYWRpbmc8L2gxPjxwPm15IHRheGVzIDwvcD4=") mustBe true
    }

    "return true when a simple text content supplied" in {
      HtmlValidator.isValidHtml("QmxhaCBibGFoIGJsYWg=") mustBe true
    }
  }
}
