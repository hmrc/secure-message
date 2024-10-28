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

package uk.gov.hmrc.securemessage.controllers

import org.mongodb.scala.bson.ObjectId
import play.api.Logging
import play.api.i18n.I18nSupport
import play.api.libs.json.Json
import play.utils.UriEncoding
import play.api.mvc.*
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import uk.gov.hmrc.auth.core.*
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.securemessage.services.SecureMessageServiceImpl
import uk.gov.hmrc.securemessage.templates
import uk.gov.hmrc.common.message.model.ConversationItem

import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

class SecureMessageRenderer @Inject() (
  cc: ControllerComponents,
  val authConnector: AuthConnector,
  override val auditConnector: AuditConnector,
  messageService: SecureMessageServiceImpl
)(implicit ec: ExecutionContext)
    extends BackendController(cc) with AuthorisedFunctions with Auditing with Logging {

  private val ATS_v2_renderTemplateId = "ats_v2"

  def view(id: ObjectId): Action[AnyContent] = Action.async { implicit request =>
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)
    messageService.getLetter(id) map {
      case Some(letter) if letter.alertDetails.templateId == ATS_v2_renderTemplateId =>
        Ok(templates.html.ViewTaxSummary_v2(letter.subject, letter.validFrom))
          .withHeaders("X-Title" -> UriEncoding.encodePathSegment(letter.subject, "UTF-8"))
      case r => InternalServerError
    }
  }
}
