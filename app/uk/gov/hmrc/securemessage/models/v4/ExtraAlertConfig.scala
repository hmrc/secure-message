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

package uk.gov.hmrc.securemessage.models.v4

import org.joda.time.{ Duration => JodaDuration }
import play.api.Configuration

import scala.jdk.CollectionConverters._

case class ExtraAlertConfig(mainTemplate: String, extraTemplate: String, delay: JodaDuration)

object ExtraAlertConfig {
  def apply(m: Map[String, AnyRef]): ExtraAlertConfig = {
    val mainTemplate =
      m.getOrElse("mainTemplate", throw new RuntimeException("mainTemplate is missing")).toString
    val extraTemplate =
      m.getOrElse("extraTemplate", throw new RuntimeException("extraTemplate is missing")).toString

    val secondsMap: Map[String, Long] =
      Map("H" -> 1000 * 60 * 60, "D" -> 1000 * 60 * 60 * 24, "M" -> 1000 * 60 * 60 * 24 * 30, "m" -> 1000, "s" -> 1000)
    val delay = try {
      val pattern = "([0-9]+)(H|D|M|m|s)".r
      m.getOrElse("delay", throw new RuntimeException("delay is missing")).toString match {
        case pattern(u, c) => new JodaDuration(u.toLong * secondsMap(c))
        case _             => throw new RuntimeException("can not read alertProfile")
      }
    } catch {
      case m: MatchError            => throw new RuntimeException(s"invalid duration unit format $m")
      case m: NumberFormatException => throw new RuntimeException(s"duration is not an integer $m")
    }
    ExtraAlertConfig(mainTemplate, extraTemplate, delay)
  }

  def apply(configuration: Configuration): List[ExtraAlertConfig] =
    configuration.underlying
      .getObjectList("alertProfile")
      .asScala
      .map(_.unwrapped().asScala.toMap)
      .map(ExtraAlertConfig(_))
      .toList
}
