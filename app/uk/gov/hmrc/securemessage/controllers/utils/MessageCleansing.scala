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

import com.google.inject.{ Inject, Singleton }
import org.jsoup.Jsoup
import org.jsoup.nodes.Document.OutputSettings
import org.jsoup.safety.Safelist
import uk.gov.hmrc.securemessage.controllers.AllowedTagAndAttributes

import scala.util.Try

@Singleton
class MessageCleansing @Inject() {

  // We want to allow "class" for all allowed tags. Unfortunately, there is no way to do
  // getTags() on a Allowlist, so I have copied the list of tags from the Allowlist.relaxed()
  // implementation here. Obviously, this is not ideal, but there isn't a way around it.
  private val allTags = List(
    "a",
    "b",
    "blockquote",
    "br",
    "caption",
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
    "tbody",
    "td",
    "tfoot",
    "th",
    "thead",
    "tr",
    "u",
    "ul"
  )

  private val allTagsAndAttributes = allTags.map(t => AllowedTagAndAttributes(t))

  def cleanseSubject(subject: String): Either[Throwable, String] =
    cleanHtml(subject)

  def cleanseBody(body: String): Either[Throwable, String] =
    cleanHtml(
      body,
      List(
        AllowedTagAndAttributes("details"),
        AllowedTagAndAttributes("summary"),
        AllowedTagAndAttributes("section")
      )
    )

  private def cleanHtml(
    dirtyHtml: String,
    extraAllowedTags: List[AllowedTagAndAttributes] = List()
  ): Either[Throwable, String] = {
    val settings = new OutputSettings().prettyPrint(false).syntax(OutputSettings.Syntax.xml)
    Try(
      Jsoup.clean(
        dirtyHtml,
        "",
        relaxedAllowlistWithClassAttributes(extraAllowedTags),
        settings
      )
    ).toEither
  }

  private def relaxedAllowlistWithClassAttributes(extraTags: List[AllowedTagAndAttributes]): Safelist =
    (allTagsAndAttributes ++ extraTags)
      .foldLeft(Safelist.relaxed().addAttributes("a", "target")) { (allowlist, tagAndAttributes) =>
        val attributes = "class" :: tagAndAttributes.attributes
        allowlist.addAttributes(tagAndAttributes.tag, attributes: _*)
      }

}
