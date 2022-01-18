/*
 * Copyright 2022 HM Revenue & Customs
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

import org.apache.commons.codec.binary.Base64
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import org.jsoup.safety.Safelist
import org.scalatestplus.play.PlaySpec
import play.api.test.Helpers._
import uk.gov.hmrc.securemessage.InvalidContent
import uk.gov.hmrc.securemessage.helpers.Resources
import uk.gov.hmrc.securemessage.services.utils.ContentValidator.DecodedBase64

import scala.concurrent.ExecutionContext.Implicits.global

class ContentValidatorSpec extends PlaySpec {

  "validate" should {

    "return right for a valid base64 encoded allowed html" in new TestContext {
      await(ContentValidator.validate(allowedHtml.asBase64).value) mustBe Right(allowedHtml.clean)
    }

    "return right for a base64 encoded plain text" in new TestContext {
      await(ContentValidator.validate(plainText.asBase64).value) mustBe Right(plainText)
    }

    "return left for empty content" in {
      await(ContentValidator.validate("").value) mustBe Left(InvalidContent("Empty content not allowed"))
    }

    "return left for non base64 encoded plain text" in new TestContext {
      await(ContentValidator.validate(plainText).value).left.get.message must startWith("Invalid base64 content:")
    }

    "return left for disallowed html" in new TestContext {
      await(ContentValidator.validate(validDisallowedHtml.asBase64).value).left.get.message must startWith(
        disallowedErrorMessage)
    }
  }

  "validateNotEmpty" should {
    "return left for an empty content" in {
      ContentValidator.validateNotEmpty("") mustBe Left(InvalidContent("Empty content not allowed"))
    }

    "return right for non empty content" in new TestContext {
      ContentValidator.validateNotEmpty(plainText) mustBe Right(plainText)
    }
  }

  "decodeBase64" should {
    "return right for valid base64 string" in new TestContext {
      ContentValidator.decodeBase64(plainText.asBase64) mustBe Right(plainText)
    }

    "return left for invalid base64 string" in new TestContext {
      ContentValidator.decodeBase64(plainText).left.get.message must startWith("Invalid base64 content:")
    }
  }

  "validateHtml" should {
    "return right for a valid HTML" in new TestContext {
      private val html: String = validDisallowedHtml
      ContentValidator.validateHtml(html).map(_.toString) mustBe Right(html.asDom.toString)
    }
    "return left for an invalid" in new TestContext {
      ContentValidator.validateHtml(invalidHtml).left.get.message must startWith("Invalid html:")
    }
  }

  "validateAllowedHtml" should {
    "return right for an HTML with allowed elements" in new TestContext {
      private val html: String = allowedHtml
      ContentValidator.validateAllowedHtml(html, html.asDom) mustBe Right(html.clean)
    }

    "return left when an HTML with disallowed tags" in new TestContext {
      private val html: String = disallowedTagsHtml
      val error: InvalidContent = ContentValidator.validateAllowedHtml(html, html.asDom).left.get
      error.message must startWith(disallowedErrorMessage)
    }

    "return left when an HTML with disallowed attributes" in new TestContext {
      private val html: String = disallowedAttributesHtml
      val error: InvalidContent = ContentValidator.validateAllowedHtml(html, html.asDom).left.get
      error.message must startWith(disallowedErrorMessage)
    }
  }

  "listDisallowedHtmlElements" should {
    "return empty list for allowed html" in new TestContext {
      private val html: String = allowedHtml
      ContentValidator.listDisallowedHtmlElements(html, html.asDom, Safelist.relaxed()) mustBe Seq[String]()
    }

    "return disallowed tags" in new TestContext {
      private val html: String = disallowedTagsHtml
      private val disallowedTags: Seq[String] =
        ContentValidator.listDisallowedHtmlElements(html, html.asDom, Safelist.relaxed())
      disallowedTags must contain theSameElementsAs Seq("html", "body")
    }

    "return tags containing disallowed attributes" in new TestContext {
      private val html: String = disallowedAttributesHtml
      private val disallowedTags: Seq[String] =
        ContentValidator.listDisallowedHtmlElements(html, html.asDom, Safelist.relaxed())
      disallowedTags must contain theSameElementsAs Seq("h2", "p")
    }
  }

  "TestContext" should {
    "base64 encode a string" in new TestContext {
      val encoded: String = plainText.asBase64
      val decoded: Either[InvalidContent, DecodedBase64] = ContentValidator.decodeBase64(encoded)
      decoded mustBe Right(plainText)
    }
  }

  class TestContext {
    //for more html examples see https://www.w3schools.com/html/html_examples.asp
    val plainText = "some plain text"
    val validDisallowedHtml: String = Resources.readString("service/ContentValidator/validDisallowed.html.txt")
    val invalidHtml: String = Resources.readString("service/ContentValidator/invalid.html.txt")
    val allowedHtml: String = Resources.readString("service/ContentValidator/allowed.html.txt")
    val disallowedTagsHtml: String = Resources.readString("service/ContentValidator/disallowedTags.html.txt")
    val disallowedAttributesHtml: String =
      Resources.readString("service/ContentValidator/disallowedAttributes.html.txt")
    val disallowedErrorMessage: String = "Html contains disallowed tags, attributes or protocols within the tags:"
  }
  implicit class StringExtensions(string: String) {
    def asBase64: String = Base64.encodeBase64String(string.getBytes)
    def asDom: Document = Parser.parse(string, "")
    def clean: String = Jsoup.clean(string, Safelist.relaxed())
  }
}
