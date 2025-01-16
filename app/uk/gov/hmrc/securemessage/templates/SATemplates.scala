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

package uk.gov.hmrc.securemessage.templates

import org.playframework.cachecontrol.CacheDirectives.Private
import play.api.i18n.Messages
import play.twirl.api.{ Html, HtmlFormat }
import uk.gov.hmrc.securemessage.models.{ AckJourneyStep, JourneyStep, ReplyFormJourneyStep, ShowLinkJourneyStep }
import uk.gov.hmrc.securemessage.templates.satemplates.helpers.{ RenderingData, SecureMessageIntegration }

import scala.concurrent.{ ExecutionContext, Future }

trait SATemplates {
  def templateKey: String

  def render(renderingData: RenderingData)(implicit messages: Messages): play.twirl.api.HtmlFormat.Appendable
}

trait WithSecureMessageIntegration {
  private def secureMessageIntegration: SecureMessageIntegration =
    SecureMessageIntegration(
      linkPartial = uk.gov.hmrc.securemessage.templates.satemplates.views.html.secure_message_show_link.apply,
      ackPartial = () =>
        uk.gov.hmrc.securemessage.templates.satemplates.views.html.secure_message_ack
          .apply()
    )

  private def getPartialHtml(journeyStep: JourneyStep) =
    journeyStep match {
      case ShowLinkJourneyStep(returnUrl) =>
        Some(
          Future.successful(secureMessageIntegration.linkPartial(returnUrl))
        )

      case ReplyFormJourneyStep(returnUrl) =>
        // Some(dfsConnector.renderForm(returnUrl, event.utr))
        // dfs-frontend service doesn't exist
        None

      case AckJourneyStep =>
        Some(Future.successful(secureMessageIntegration.ackPartial()))
    }

  def secureTemplateRender(journeyStep: JourneyStep)(implicit ec: ExecutionContext): Future[Html] = getPartialHtml(
    journeyStep
  ) match {
    case Some(pHtml) =>
      pHtml.map(uk.gov.hmrc.securemessage.templates.satemplates.views.html.secure_message_display(_))
    case _ => Future.successful(HtmlFormat.empty)
  }
}
