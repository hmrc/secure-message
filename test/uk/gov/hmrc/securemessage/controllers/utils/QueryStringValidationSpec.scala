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

package uk.gov.hmrc.securemessage.controllers.utils

import org.scalatestplus.play.PlaySpec

class QueryStringValidationSpec extends PlaySpec with QueryStringValidation {

  "InvalidQueryParameterException class" must {
    "return an InvalidQueryStringException exception with a formatted error text when provided with a list of invalid parameters" in {
      InvalidQueryParameterException(List("a", "b", "c")).getMessage mustBe "Invalid query parameter(s) found: [a, b, c]"
    }
  }

  "validateQueryParameters method" must {

    "return a valid result when no other parameters are present in the query string" in {
      val queryString: Map[String, Seq[String]] = Map(
        "enrolment" -> Seq("3"),
        "enrolment" -> Seq("2"),
        "enrolment" -> Seq("4"),
        "enrolment" -> Seq("5"),
        "enrolment" -> Seq("1")
      )
      val result = validateQueryParameters(queryString)
      result mustBe Right(ValidCDSQueryParameters)
    }

    "return an invalid result when unknown parameters are present in the query string" in {
      val queryString: Map[String, Seq[String]] = Map(
        "c" -> Seq("3"),
        "b" -> Seq("2"),
        "d" -> Seq("4"),
        "e" -> Seq("5"),
        "a" -> Seq("1")
      )
      val result = validateQueryParameters(queryString).left.getOrElse(new Exception())
      result.getMessage mustBe "Invalid query parameter(s) found: [e, a, b, c, d]"
      result.isInstanceOf[InvalidQueryParameterException] mustBe true
    }
  }
}
