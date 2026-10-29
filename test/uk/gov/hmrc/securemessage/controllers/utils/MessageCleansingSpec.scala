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

package uk.gov.hmrc.securemessage.controllers.utils

import org.scalatest.matchers.must.Matchers
import org.scalatestplus.play.PlaySpec

class MessageCleansingSpec extends PlaySpec with Matchers {

  private val allowedTags: List[String] = List(
    "a",
    "b",
    "blockquote",
    "br",
    "cite",
    "code",
    "col",
    "colgroup",
    "dd",
    "div",
    "dl",
    "dt",
    "em",
    "h1",
    "h2",
    "h3",
    "h4",
    "h5",
    "h6",
    "i",
    "img",
    "li",
    "ol",
    "p",
    "pre",
    "q",
    "small",
    "span",
    "strike",
    "strong",
    "sub",
    "sup",
    "table",
    "caption",
    "tbody",
    "td",
    "tfoot",
    "th",
    "thead",
    "tr",
    "u",
    "ul"
  )

  private val allowedTagHtml = allowedTags.map(tag => s"<$tag>").mkString("")
  private val allowedTagWithAttributesHtml = allowedTags.map(tag => s"""<$tag lang="en">""").mkString("")
  private val additionalBodyAllowedTagHtml = """<details lang="en"/><summary lang="en"/><section lang="en"/>"""

  private val messageCleansing = new MessageCleansing

  "cleanseSubject" should {
    "return empty string if the passed string is empty" in {
      messageCleansing.cleanseSubject("") mustBe Right("")
    }

    "remove unexpected tags" in {
      val htmlWithUnexpectedTags =
        s"$additionalBodyAllowedTagHtml<invalidTag1/><invalidTag2/>$allowedTagHtml<invalidTag3/><invalidTag4/>"
      // this library alters content trying to fix invalid html, for example, it will remove caption entries if they are not in the right place.
      val cleansedSubject = messageCleansing.cleanseSubject(htmlWithUnexpectedTags)
      cleansedSubject mustBe Right(
        "<a><b><blockquote><br /><cite><code><dd><div><dl><dt><em><h1></h1><h2></h2><h3></h3><h4></h4><h5></h5><h6><i><img />" +
          "<li><ol><p></p><pre><q><small><span><strike><strong><sub><sup><u><ul></ul></u><table><caption></caption><tbody><tr><td>" +
          "</td></tr></tbody><tfoot><tr><th></th></tr></tfoot><thead><tr></tr></thead></table></sup></sub></strong></strike></span>" +
          "</small></q></pre></ol></li></i></h6></em></dt></dl></div></dd></code></cite></blockquote></b></a>"
      )
    }

    "remove all attributes on tags that have not been allowed" in {
      val cleansedSubjectWithAttiributes = messageCleansing.cleanseSubject(allowedTagWithAttributesHtml)
      cleansedSubjectWithAttiributes mustBe Right(
        "<a><b><blockquote><br /><cite><code><dd><div><dl><dt><em><h1></h1><h2></h2><h3></h3><h4></h4><h5></h5><h6><i><img />" +
          "<li><ol><p></p><pre><q><small><span><strike><strong><sub><sup><u><ul></ul></u><table><caption></caption><tbody><tr><td>" +
          "</td></tr></tbody><tfoot><tr><th></th></tr></tfoot><thead><tr></tr></thead></table></sup></sub></strong></strike></span>" +
          "</small></q></pre></ol></li></i></h6></em></dt></dl></div></dd></code></cite></blockquote></b></a>"
      )
    }

    "allow target on links, but remove other attributes" in {
      val cleansedSubjectWithAttriibutes = messageCleansing.cleanseSubject("""<a href="a" target="b" madeup="c"/>""")

      cleansedSubjectWithAttriibutes mustBe Right("""<a target="b" />""")
    }
  }

  "cleanseBody" should {
    "return empty string if the passed string is empty" in {
      messageCleansing.cleanseBody("") mustBe Right("")
    }

    "remove unexpected tags" in {
      val htmlWithUnexpectedTags =
        s"$additionalBodyAllowedTagHtml<invalidTag1/><invalidTag2/>$allowedTagHtml<invalidTag3/><invalidTag4/>"
      // this library alters content trying to fix invalid html, for example, it will remove caption entries if they are not in the right place.
      val cleansedSubject = messageCleansing.cleanseBody(htmlWithUnexpectedTags)
      cleansedSubject mustBe Right(
        "<details><summary><section><a><b><blockquote><br /><cite><code><dd><div><dl><dt><em><h1></h1><h2></h2><h3></h3><h4></h4>" +
          "<h5></h5><h6><i><img /><li><ol><p></p><pre><q><small><span><strike><strong><sub><sup><u><ul></ul></u><table><caption></caption>" +
          "<tbody><tr><td></td></tr></tbody><tfoot><tr><th></th></tr></tfoot><thead><tr></tr></thead></table></sup></sub></strong></strike>" +
          "</span></small></q></pre></ol></li></i></h6></em></dt></dl></div></dd></code></cite></blockquote></b></a></section></summary></details>"
      )
    }

    "remove all attributes on tags that have not been allowed" in {
      val cleansedSubjectWithAttiributes = messageCleansing.cleanseBody(allowedTagWithAttributesHtml)
      cleansedSubjectWithAttiributes mustBe Right(
        "<a><b><blockquote><br /><cite><code><dd><div><dl><dt><em><h1></h1><h2></h2><h3></h3><h4></h4><h5></h5><h6><i><img />" +
          "<li><ol><p></p><pre><q><small><span><strike><strong><sub><sup><u><ul></ul></u><table><caption></caption><tbody><tr><td>" +
          "</td></tr></tbody><tfoot><tr><th></th></tr></tfoot><thead><tr></tr></thead></table></sup></sub></strong></strike></span>" +
          "</small></q></pre></ol></li></i></h6></em></dt></dl></div></dd></code></cite></blockquote></b></a>"
      )
    }

    "allow target on links, but remove other attributes" in {
      val cleansedSubjectWithAttriibutes = messageCleansing.cleanseBody("""<a href="a" target="b" madeup="c"/>""")

      cleansedSubjectWithAttriibutes mustBe Right("""<a target="b" />""")
    }
  }

}
