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

  private val allowedTagHtmlWithClassAttribute = allowedTags.map(tag => s"""<$tag class="allowedClass">""").mkString("")
  private val allowedTagWithAttributesHtml = allowedTags.map(tag => s"""<$tag lang="en">""").mkString("")
  private val additionalBodyAllowedTagHtml =
    """<details lang="en" class="allowedClass"/><summary lang="en" class="allowedClass"/><section lang="en" subject="xx" class="allowedClass"/>"""

  private val messageCleansing = new MessageCleansing

  "cleanseSubject" should {
    "return empty string if the passed string is empty" in {
      messageCleansing.cleanseSubject("") mustBe Right("")
    }

    "remove unexpected tags and allow class attribute as we added it to everything" in {
      val htmlWithUnexpectedTags =
        s"$additionalBodyAllowedTagHtml<invalidTag1/><invalidTag2/>$allowedTagHtmlWithClassAttribute<invalidTag3/><invalidTag4/>"
      // this library alters content trying to fix invalid html, for example, it will remove caption entries if they are not in the right place.
      val cleansedSubject = messageCleansing.cleanseSubject(htmlWithUnexpectedTags)
      cleansedSubject mustBe Right(
        """<a class="allowedClass"><b class="allowedClass"><blockquote class="allowedClass"><br class="allowedClass" />""" +
          """<cite class="allowedClass"><code class="allowedClass"><dd class="allowedClass"><div class="allowedClass">""" +
          """<dl class="allowedClass"><dt class="allowedClass"><em class="allowedClass"><h1 class="allowedClass">""" +
          """</h1><h2 class="allowedClass"></h2><h3 class="allowedClass"></h3><h4 class="allowedClass"></h4>""" +
          """<h5 class="allowedClass"></h5><h6 class="allowedClass"><i class="allowedClass"><img class="allowedClass" />""" +
          """<li class="allowedClass"><ol class="allowedClass"><p class="allowedClass"></p><pre class="allowedClass">""" +
          """<q class="allowedClass"><small class="allowedClass"><span class="allowedClass"><strike class="allowedClass">""" +
          """<strong class="allowedClass"><sub class="allowedClass"><sup class="allowedClass"><u class="allowedClass">""" +
          """<ul class="allowedClass"></ul></u><table class="allowedClass"><caption class="allowedClass"></caption>""" +
          """<tbody class="allowedClass"><tr><td class="allowedClass"></td></tr></tbody><tfoot class="allowedClass">""" +
          """<tr><th class="allowedClass"></th></tr></tfoot><thead class="allowedClass"><tr class="allowedClass"></tr>""" +
          """</thead></table></sup></sub></strong></strike></span></small></q></pre></ol></li></i></h6></em></dt></dl></div>""" +
          """</dd></code></cite></blockquote></b></a>""".stripMargin
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

    "allow additional target attribute on links, but remove unknown attributes that are not in the relaxed safelist" in {
      val cleansedSubjectWithAttriibutes =
        messageCleansing.cleanseSubject("""<a href="http://test.com" target="b" madeup="c" title="d"/>""")

      cleansedSubjectWithAttriibutes mustBe Right("""<a href="http://test.com" target="b" title="d" />""")
    }
  }

  "cleanseBody" should {
    "return empty string if the passed string is empty" in {
      messageCleansing.cleanseBody("") mustBe Right("")
    }

    "remove unexpected tags and allow class attribute as we added it to everything" in {
      val htmlWithUnexpectedTags =
        s"$additionalBodyAllowedTagHtml<invalidTag1/><invalidTag2/>$allowedTagHtmlWithClassAttribute<invalidTag3/><invalidTag4/>"
      // this library alters content trying to fix invalid html, for example, it will remove caption entries if they are not in the right place.
      val cleansedSubject = messageCleansing.cleanseBody(htmlWithUnexpectedTags)
      cleansedSubject mustBe Right(
        """<details class="allowedClass"><summary class="allowedClass"><section class="allowedClass">""" +
          """<a class="allowedClass"><b class="allowedClass"><blockquote class="allowedClass"><br class="allowedClass" />""" +
          """<cite class="allowedClass"><code class="allowedClass"><dd class="allowedClass"><div class="allowedClass">""" +
          """<dl class="allowedClass"><dt class="allowedClass"><em class="allowedClass"><h1 class="allowedClass">""" +
          """</h1><h2 class="allowedClass"></h2><h3 class="allowedClass"></h3><h4 class="allowedClass"></h4>""" +
          """<h5 class="allowedClass"></h5><h6 class="allowedClass"><i class="allowedClass"><img class="allowedClass" />""" +
          """<li class="allowedClass"><ol class="allowedClass"><p class="allowedClass"></p><pre class="allowedClass">""" +
          """<q class="allowedClass"><small class="allowedClass"><span class="allowedClass"><strike class="allowedClass">""" +
          """<strong class="allowedClass"><sub class="allowedClass"><sup class="allowedClass"><u class="allowedClass">""" +
          """<ul class="allowedClass"></ul></u><table class="allowedClass"><caption class="allowedClass"></caption>""" +
          """<tbody class="allowedClass"><tr><td class="allowedClass"></td></tr></tbody><tfoot class="allowedClass">""" +
          """<tr><th class="allowedClass"></th></tr></tfoot><thead class="allowedClass"><tr class="allowedClass"></tr>""" +
          """</thead></table></sup></sub></strong></strike></span></small></q></pre></ol></li></i></h6></em></dt></dl></div>""" +
          """</dd></code></cite></blockquote></b></a>""" +
          """</section></summary></details>""".stripMargin
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

    "allow additional target attribute on links, but remove unknown attributes that are not in the relaxed safelist" in {
      val cleansedSubjectWithAttriibutes =
        messageCleansing.cleanseBody("""<a href="http://test.com" target="b" madeup="c" title="d"/>""")

      cleansedSubjectWithAttriibutes mustBe Right("""<a href="http://test.com" target="b" title="d" />""")
    }
  }

}
