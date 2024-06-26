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

package uk.gov.hmrc.securemessage.models.utils

import cats.data.NonEmptyList
import play.api.libs.json.{ Format, JsonValidationError, Reads, Writes }

trait NonEmptyListOps {

  implicit def nonEmptyListReads[T: Reads]: Reads[NonEmptyList[T]] =
    Reads
      .of[List[T]]
      .collect(
        JsonValidationError("expected a NonEmptyList but got an empty list")
      ) { case head :: tail =>
        NonEmptyList(head, tail)
      }

  implicit def nonEmptyListWrites[T: Writes]: Writes[NonEmptyList[T]] =
    Writes
      .of[List[T]]
      .contramap(_.toList)

  implicit def nonEmptyListFormat[T: Format]: Format[NonEmptyList[T]] =
    Format(nonEmptyListReads, nonEmptyListWrites)

}

object NonEmptyListOps extends NonEmptyListOps
