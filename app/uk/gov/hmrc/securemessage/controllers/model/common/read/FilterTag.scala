/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.securemessage.controllers.model.common.read

import play.api.libs.json.{ Json, Reads }

//TODO: this is a common read model
final case class FilterTag(key: String, value: String)

object FilterTag {
  implicit val tagReads: Reads[FilterTag] = {
    Json.reads[FilterTag]
  }

  def parse(tagString: String): FilterTag = {
    val tag = tagString.split('~')
    FilterTag(tag.head, tag(1))
  }
}
