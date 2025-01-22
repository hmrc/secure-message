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

package uk.gov.hmrc.securemessage.templates.satemplates.sx300

import play.api.{ Configuration, Mode }
import play.api.i18n.Messages
import play.twirl.api.HtmlFormat
import uk.gov.hmrc.securemessage.templates.satemplates.helpers.RenderingData
import uk.gov.hmrc.securemessage.templates.{ SATemplates, WithSecureMessageIntegration }

import java.time.LocalDate
import scala.concurrent.{ ExecutionContext, Future }

sealed trait TemplateSX300_v1 extends SATemplates with WithSecureMessageIntegration {
  def render(renderingData: RenderingData)(implicit messages: Messages): HtmlFormat.Appendable =
    html.SX300_v1(renderingData)
}

case object TemplateSA300_v1 extends TemplateSX300_v1 {
  val templateKey = "SA300_v1"
}

case object TemplateSS300_v1 extends TemplateSX300_v1 {
  val templateKey = "SS300_v1"
}
