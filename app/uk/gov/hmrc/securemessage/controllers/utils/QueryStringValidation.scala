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

trait QueryStringValidationSuccess
case object ValidCDSQueryParameters extends QueryStringValidationSuccess
case object ValidNonCDSQueryParameters extends QueryStringValidationSuccess

class InvalidQueryStringException(message: String) extends Exception(message) {}
final case class InvalidQueryParameterException(invalidParams: List[String])
    extends InvalidQueryStringException(
      s"Invalid query parameter(s) found: [${invalidParams.sorted.toSet.mkString(", ")}]"
    ) {}

trait QueryStringValidation {

  val validCdsQueryParams = List("enrolment", "enrolmentKey", "tag", "lang")
  val validNonCdsQueryParams = List("taxIdentifiers", "regimes", "lang")

  protected def validateQueryParameters(
    queryString: Map[String, Seq[String]]
  ): Either[InvalidQueryStringException, QueryStringValidationSuccess] = {
    val cdsParams = queryString.keys.toList diff validCdsQueryParams
    val nonCdsParams = queryString.keys.toList diff validNonCdsQueryParams
    (cdsParams, nonCdsParams) match {
      case (_, _) if queryString.isEmpty => Right(ValidNonCDSQueryParameters)
      case (List(), _)                   => Right(ValidCDSQueryParameters)
      case (_, List())                   => Right(ValidNonCDSQueryParameters)
      case (invalidParams1: List[String], invalidParams2: List[String]) =>
        Left(InvalidQueryParameterException(invalidParams1 ++ invalidParams2))
    }
  }
}
