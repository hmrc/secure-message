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

import org.apache.commons.codec.binary.Base64.decodeBase64
import org.jsoup.Jsoup
import org.jsoup.safety.Whitelist

import scala.xml.SAXParseException

object HtmlValidator {

  def isValidHtml(content: String): Boolean =
    if (Jsoup.isValid(decodeBase64(content).map(_.toChar).mkString, Whitelist.relaxed)) {
      stringToXmlNodes(decodeBase64(content).map(_.toChar).mkString)
    } else {
      false
    }

  private def stringToXmlNodes(content: String): Boolean =
    try {
      val xml = scala.xml.XML.loadString("<root>" + content + "</root>")
      val result = xml.child
      if (result.isEmpty) {
        false
      } else {
        true
      }
    } catch {
      case _: SAXParseException => false
    }
}
