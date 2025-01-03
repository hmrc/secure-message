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

package uk.gov.hmrc.securemessage.services

import com.google.inject.Inject
import play.api.i18n.Messages
import play.api.libs.json.{ JsNull, JsValue }
import play.api.mvc.Request
import play.twirl.api.{ Html, HtmlFormat }
import uk.gov.hmrc.common.message.model.{ ContentParameters, MessageContentParameters }
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.securemessage.models.{ JourneyStep, ShowLinkJourneyStep }
import uk.gov.hmrc.securemessage.models.core.{ Details, Letter }
import uk.gov.hmrc.securemessage.templates.SATemplates
import uk.gov.hmrc.securemessage.templates.satemplates.ignorePaperFiling.TemplateIgnorePaperFiling_v1
import uk.gov.hmrc.securemessage.templates.satemplates.r002a.TemplateR002A_v1
import uk.gov.hmrc.securemessage.templates.satemplates.views.html
import uk.gov.hmrc.securemessage.templates.satemplates.helpers.{ Body, PlatformUrls, PortalUrlBuilder, RenderingData }

import javax.inject.Singleton
import scala.concurrent.{ ExecutionContext, Future }

//Message renderer service for SA messages
class SAMessageRendererService @Inject() (
  servicesConfig: ServicesConfig,
  messageRenderer: SAMessageRenderer,
  portalUrlBuilder: PortalUrlBuilder
)(implicit ec: ExecutionContext) {
  val saPaymentsUrl: String = servicesConfig.getConfString(
    "platform.saPaymentsUrl",
    throw new RuntimeException(
      s"Could not find payments URL under platform.saPaymentsUrl"
    )
  )
  val viewTaxSummaryUrl: String = servicesConfig.getConfString(
    "platform.viewTaxSummaryUrl",
    throw new RuntimeException(
      s"Could not find payments URL under platform.viewTaxSummaryUrl"
    )
  )
  val platUrls: PlatformUrls = PlatformUrls(saPaymentsUrl, viewTaxSummaryUrl)

  def apply(message: Letter, journeyStep: Option[JourneyStep], utr: String)(implicit
    request: Request[AnyRef],
    messages: Messages
  ): Future[Html] = {
    val inferParams: Option[String] => Option[MessageContentParameters] = {
      case Some("SA300") =>
        Some(MessageContentParameters(JsNull, "SA300_v1"))
      case Some("SS300") =>
        Some(MessageContentParameters(JsNull, "SS300_v1"))
      case _ => None
    }

    val maybeParameters =
      message.contentParameters.orElse(inferParams(message.body.flatMap(_.form)))
    render(message, maybeParameters, journeyStep, utr)
  }

  private def render(
    message: Letter,
    maybeContentParameters: Option[MessageContentParameters],
    mayBeJourneyStep: Option[JourneyStep] = None,
    utr: String
  )(implicit messages: Messages): Future[Html] = {
    val bodyF: Future[Body] = maybeContentParameters
      .map { contentParameters =>
        val renderingData =
          RenderingData(portalUrlBuilder, Some(utr), platUrls, contentParameters.data)
        def messageBodyPart = messageRenderer.render(contentParameters.templateId, renderingData)

        def secureMessageBodyPartF() =
          (contentParameters.templateId, mayBeJourneyStep) match {
//            case (secureTemplateIntegration: WithSecureMessageIntegration, Some(js)) =>
//              secureMessageService.secureMessagePartial(
//                SecureMessageEvent(message.id, secureTemplateIntegration, js, utr)
//              )
            case _ => Future.successful(HtmlFormat.empty)
          }
        val shrinkMessageFlag = mayBeJourneyStep match {
          case Some(ShowLinkJourneyStep(_)) => false
          case _                            => false
        }

        secureMessageBodyPartF().map(secureMessageBodyPart =>
          Body(messageBodyPart, secureMessageBodyPart, shrinkMessageFlag)
        )
      }
      .getOrElse(
        Future.successful(Body(HtmlFormat.empty, HtmlFormat.empty, shrinkMessage = false))
      )

    bodyF.map(body =>
      html.details_partial(
        subject = message.subject,
        sentInError = false,
        fromDate = message.validFrom,
        issueDate = message.issueDate,
        body = body
      )
    )
  }
}

@Singleton
class SAMessageRenderer @Inject() (implicit messages: Messages) {
  private val allTemplates: Seq[SATemplates] = Seq(
    TemplateIgnorePaperFiling_v1,
    TemplateR002A_v1
  )
  private val templateRenderer: Map[String, RenderingData => HtmlFormat.Appendable] =
    allTemplates.map(t => (t.templateKey, t.render _)).toMap
  def render(templateId: String, renderingData: RenderingData): HtmlFormat.Appendable =
    templateRenderer(templateId)(renderingData)
}
