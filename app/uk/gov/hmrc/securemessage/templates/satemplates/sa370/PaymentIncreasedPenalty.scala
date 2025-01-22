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

package uk.gov.hmrc.securemessage.templates.satemplates.sa370

import play.api.libs.json.{ Format, JsValue, Json, OFormat }
import uk.gov.hmrc.securemessage.templates.satemplates.sa37X.ParamType
import uk.gov.hmrc.securemessage.templates.satemplates.sa370.fragments.html.*

case class PaymentIncreasedPenaltyParams(
  latePaymentPenaltyRate: String,
  revisedAmount: String,
  latePaymentTriggerDate: String,
  previouslyCharged: String,
  toPay: String
)

object PaymentIncreasedPenaltyParams extends ParamType[PaymentIncreasedPenaltyParams] {
  implicit val formats: OFormat[PaymentIncreasedPenaltyParams] =
    Json.format[PaymentIncreasedPenaltyParams]

  override val templates = Map(
    "Payment6MonthsIncreasedPenalty_v1"  -> Payment6MonthsIncreasedPenalty_v1.f,
    "Payment30DaysIncreasedPenalty_v1"   -> Payment30DaysIncreasedPenalty_v1.f,
    "Payment12MonthsIncreasedPenalty_v1" -> Payment12MonthsIncreasedPenalty_v1.f
  )

  override def convertParams(contentParams: JsValue): PaymentIncreasedPenaltyParams =
    contentParams.as[PaymentIncreasedPenaltyParams]
}
