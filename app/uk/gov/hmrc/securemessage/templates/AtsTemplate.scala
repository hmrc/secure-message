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

import play.twirl.api.Html

object AtsTemplate {
 def apply(subject: String, fromDate: java.time.LocalDate): Html = {
   val date = java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy").format(fromDate)
   Html(
     s"""<h2>$subject</h2>
        |
        |<p class="message_time faded-text--small">This message was sent to you on $date</p>
        |
        |<p>This is a summary of how Government spends your tax and National Insurance contributions.</p>
        |<p>See your <a href="https://www.tax.service.gov.uk/annual-tax-summary">Annual Tax Summary</a>.</p>
        |<p>Tax summaries are for information only so you don't need to contact HMRC. But you can <a href="https://www.gov.uk/annual-tax-summary">comment on tax summaries</a>.</p>""".stripMargin
   )
 }
}
