/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.securemessage.templates.satemplates.sa371.fragments

import play.twirl.api.HtmlFormat
import uk.gov.hmrc.securemessage.SpecBase
import uk.gov.hmrc.securemessage.TestData.TEST_DATE_STRING
import uk.gov.hmrc.securemessage.templates.satemplates.sa370.SA370FilingSecond3MonthsPenaltyParams
import uk.gov.hmrc.securemessage.templates.satemplates.sa37X.FilingSecond3MonthsPenaltyParams

class FilingSecond3MonthsPaperPenaltySpec extends SpecBase {

  "view" must {
    "display the correct content" in {
      val templates: Map[String, FilingSecond3MonthsPenaltyParams => HtmlFormat.Appendable] =
        SA370FilingSecond3MonthsPenaltyParams.templates

      val filingSecond3MonthsPenaltyTemplate: Option[FilingSecond3MonthsPenaltyParams => HtmlFormat.Appendable] =
        templates.get("FilingSecond3MonthsPaperPenalty_v1")

      val filingSecond3MonthsPenaltyParams = FilingSecond3MonthsPenaltyParams(
        dailyPenalty = "100",
        numberOfDays = "10",
        fromDate = TEST_DATE_STRING,
        toDate = TEST_DATE_STRING,
        totalPenalty = "500"
      )

      filingSecond3MonthsPenaltyTemplate.map { htmlFormat =>
        val content: HtmlFormat.Appendable = htmlFormat(filingSecond3MonthsPenaltyParams)

        val contentBody = content.body

        assert(contentBody.contains("3 months late – a daily penalty of &pound;100 a day for 10 days"))
        assert(contentBody.contains(<td class="text--right">&pound;500</td>.mkString))
      }
    }
  }
}
