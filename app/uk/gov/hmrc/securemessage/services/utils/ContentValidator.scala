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

import cats.data.EitherT
import org.jsoup.Jsoup
import org.jsoup.nodes.{ Document, Element }
import org.jsoup.parser.Parser
import org.jsoup.safety.Whitelist
import uk.gov.hmrc.securemessage.InvalidContent
import java.nio.charset.StandardCharsets
import java.util.Base64
import scala.collection.JavaConverters._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

object ContentValidator {
  type NonEmptyString = String
  type EncodedBase64 = String
  type DecodedBase64 = String
  type Html = String
  private val decoder: Base64.Decoder = Base64.getDecoder

  def validate(content: EncodedBase64)(
    implicit executionContext: ExecutionContext): EitherT[Future, InvalidContent, String] = {
    val result = for {
      nonEmptyContent <- validateNotEmpty(content)
      decodedBase64   <- decodeBase64(nonEmptyContent)
      validHtmlDom    <- validateHtml(decodedBase64)
      allowedHtml     <- validateAllowedHtml(decodedBase64, validHtmlDom)
    } yield allowedHtml
    EitherT(Future(result))
  }

  private[utils] def validateNotEmpty(content: String): Either[InvalidContent, NonEmptyString] =
    if (content.isEmpty) {
      Left(InvalidContent("Empty content not allowed"))
    } else {
      Right(content)
    }

  private[utils] def decodeBase64(content: EncodedBase64): Either[InvalidContent, DecodedBase64] =
    Try {
      decoder.decode(content.getBytes(StandardCharsets.UTF_8)).map(_.toChar).mkString
    } match {
      case Success(decodedBase64) => Right(decodedBase64)
      case Failure(e)             => Left(InvalidContent(s"Invalid base64 content: ${e.getMessage}.", Some(e)))
    }

  private[utils] def validateHtml(content: DecodedBase64): Either[InvalidContent, Document] = {
    val errorsCount = 100
    val parser = Parser.htmlParser().setTrackErrors(errorsCount)
    val dom: Document = Jsoup.parse(content, "", parser)
    val errors = parser.getErrors.asScala.map(_.toString)
    if (errors.nonEmpty) {
      Left(InvalidContent(s"Invalid html: $errors")) //TODO: test a valid xml but invalid html
    } else {
      Right(dom)
    }
  }

  private[utils] def validateAllowedHtml(content: Html, dom: Document): Either[InvalidContent, Html] = {
    val whitelist = Whitelist.relaxed()
    if (!Jsoup.isValid(content, whitelist)) {
      val disallowedElements = listDisallowedHtmlElements(content, dom, whitelist).mkString(", ")
      Left(
        InvalidContent(
          s"Html contains disallowed tags, attributes or protocols within the tags: $disallowedElements. " +
            s"For allowed elements see ${whitelist.getClass}.relaxed()"))
    } else {
      Right(Jsoup.clean(content, whitelist))
    }
  }

  private[utils] def listDisallowedHtmlElements(content: Html, dom: Document, whitelist: Whitelist): Seq[String] =
    dom.getAllElements.asScala.collect {
      case e: Element if content.contains(e.tagName()) && !Jsoup.isValid(e.toString, whitelist) => e.tagName()
    }

}
