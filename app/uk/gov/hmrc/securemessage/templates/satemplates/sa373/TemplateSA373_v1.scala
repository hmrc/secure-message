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

package uk.gov.hmrc.securemessage.templates.satemplates.sa373

import play.api.{ Configuration, Mode }
import play.api.i18n.Messages
import play.api.libs.json.{ Json, OFormat }
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.securemessage.templates.SATemplates
import uk.gov.hmrc.securemessage.templates.satemplates.helpers.{ RenderingData, TaxYear }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.matching.Regex

object SA373_ContentParams {
  implicit val formats: OFormat[SA373_ContentParams] =
    Json.format[SA373_ContentParams]
}

case class SA373_ContentParams(taxYear: TaxYear, partnershipName: String, penaltyAmount: Int)

case object TemplateSA373_v1 extends SATemplates {
  val templateKey = "SA373_v1"

  override def render(
    renderingData: RenderingData
  )(implicit messages: Messages): play.twirl.api.HtmlFormat.Appendable =
    html.SA373_v1(
      renderingData.contentParametersData.as[SA373_ContentParams],
      renderingData.platformUrls
    )
}
