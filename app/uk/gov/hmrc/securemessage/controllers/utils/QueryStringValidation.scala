/*
 * Copyright 2022 HM Revenue & Customs
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
case object ValidQueryParameters extends QueryStringValidationSuccess

class InvalidQueryStringException(message: String) extends Exception(message) {}
final case class InvalidQueryParameterException(invalidParams: List[String])
    extends InvalidQueryStringException(s"Invalid query parameter(s) found: [${invalidParams.sorted.mkString(", ")}]") {}

trait QueryStringValidation {

  protected def validateQueryParameters(
    queryString: Map[String, Seq[String]],
    allowedParamKeys: String*): Either[InvalidQueryStringException, QueryStringValidationSuccess] =
    (queryString.keys.toList diff allowedParamKeys) match {
      case List()                      => Right(ValidQueryParameters)
      case invalidParams: List[String] => Left(InvalidQueryParameterException(invalidParams))
    }
}
