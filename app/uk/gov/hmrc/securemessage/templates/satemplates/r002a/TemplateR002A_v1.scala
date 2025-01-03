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

package uk.gov.hmrc.securemessage.templates.satemplates.r002a

import play.api.i18n.Messages
import play.api.libs.json.{ Format, JsString, JsValue, Json, JsonValidationError, Reads, Writes }
import play.api.{ Configuration, Mode }
import play.twirl.api.HtmlFormat
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.securemessage.templates.SATemplates
import uk.gov.hmrc.securemessage.templates.satemplates.helpers.RenderingData

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.matching.Regex

case object TemplateR002A_v1 extends SATemplates {
  val templateKey = "R002A_v1"

  def render(renderingData: RenderingData)(implicit messages: Messages): HtmlFormat.Appendable =
    html.R002A_v1(
      renderingData.contentParametersData.as[R002A_v1ContentParams],
      renderingData.portalUrlBuilder,
      renderingData.saUtr
    )
}

sealed trait Role

case object Taxpayer extends Role

case object Agent extends Role

case object Nominee extends Role

object Role {
  implicit val roleReads: Reads[Role] = Reads.StringReads.collect(
    JsonValidationError("Not a valid role. Expected one of {'Taxpayer', 'Agent', 'Nominee'}")
  ) {
    case "Taxpayer" => Taxpayer
    case "Agent"    => Agent
    case "Nominee"  => Nominee
  }
  implicit val roleWrites: Writes[Role] = new Writes[Role] {
    def writes(o: Role): JsValue = JsString(o.toString)
  }
}

sealed trait Method

case object Electronic extends Method

case object Cheque extends Method

object Method {
  implicit val methodReads: Reads[Method] = Reads.StringReads.collect(
    JsonValidationError("Not a valid repayment method. Expected one of {'B', 'P'}")
  ) {
    case "B"          => Electronic
    case "P"          => Cheque
    case "Electronic" => Electronic
    case "Cheque"     => Cheque
  }
  implicit val methodWrites: Writes[Method] = new Writes[Method] {
    def writes(o: Method): JsValue = JsString(o.toString)
  }
}

case class R002A_v1ContentParams(
  amount: BigDecimal,
  supplement: Option[BigDecimal],
  method: Method,
  name: String,
  role: Role
)

object R002A_v1ContentParams {
  implicit val jsonFormat: Format[R002A_v1ContentParams] =
    Json.format[R002A_v1ContentParams]
}
