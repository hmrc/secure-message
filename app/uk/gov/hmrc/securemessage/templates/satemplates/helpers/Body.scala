/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.securemessage.templates.satemplates.helpers

import play.twirl.api.Html
import uk.gov.hmrc.common.message.model.ContentParameters
import com.google.inject.Inject

import javax.inject.Singleton
import play.api.{ Configuration, Logger, Mode }
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.annotation.tailrec
import scala.util.Try

case class Body(messageBodyPart: Html, secureMessageBodyPart: Html, shrinkMessage: Boolean)

case class PlatformUrls(saPaymentsUrl: String, viewTaxSummaryUrl: String)
case class RenderingData(
  portalUrlBuilder: PortalUrlBuilder,
  saUtr: Option[String],
  platformUrls: PlatformUrls,
  contentParametersData: ContentParameters,
  secureMessagePartial: Option[Html] = None,
  shrinkMessage: Boolean = false
)

case class SecureMessageIntegration(
  linkPartial: String => play.twirl.api.HtmlFormat.Appendable,
  ackPartial: () => play.twirl.api.HtmlFormat.Appendable
)

@Singleton
class PortalUrlBuilder @Inject() (config: ServicesConfig) {
  def getPath(pathKey: String): String =
    Try(
      config
        .getString(s"govuk-tax.portal.destinationPath.$pathKey")
    ).getOrElse("")

  def buildPortalUrl(saUtr: Option[String], destinationPathKey: String): String =
    buildUrl(
      getPath(destinationPathKey),
      Seq(
        ("<utr>", saUtr) // Add other placeholder strategies here as required
      )
    )

  private def buildUrl(destinationUrl: String, tags: Seq[(String, Option[Any])]): String =
    resolvePlaceHolder(destinationUrl, tags)

  @tailrec
  private def resolvePlaceHolder(url: String, tags: Seq[(String, Option[Any])]): String =
    if (tags.isEmpty) {
      url
    } else {
      resolvePlaceHolder(replace(url, tags.head), tags.tail)
    }

  private def replace(url: String, tags: (String, Option[Any])): String = {
    val (tagName, tagValueOption) = tags
    tagValueOption match {
      case Some(valueOfTag) => url.replace(tagName, valueOfTag.toString)
      case _                => url
    }
  }
}
