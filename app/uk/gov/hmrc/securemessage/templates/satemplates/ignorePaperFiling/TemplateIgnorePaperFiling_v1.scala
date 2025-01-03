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

package uk.gov.hmrc.securemessage.templates.satemplates.ignorePaperFiling

import play.api.i18n.Messages
import play.twirl.api.HtmlFormat
import uk.gov.hmrc.mongo.workitem.ProcessingStatus.ToDo
import uk.gov.hmrc.play.audit.model.EventTypes.Failed as TxFailed
import uk.gov.hmrc.securemessage.templates.SATemplates
import uk.gov.hmrc.securemessage.templates.satemplates.helpers.RenderingData

import java.time.format.DateTimeFormatter
import java.time.{ Instant, LocalDate, ZoneId }
import scala.concurrent.{ ExecutionContext, Future }

case object TemplateIgnorePaperFiling_v1 extends SATemplates {
  val templateKey = "IgnorePaperFiling_v1"

  def render(renderingData: RenderingData)(implicit messages: Messages): HtmlFormat.Appendable =
    html.IgnorePaperFiling_v1(
      renderingData.portalUrlBuilder,
      renderingData.saUtr
    )
}
