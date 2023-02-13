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

import com.eclipsesource.schema.drafts.Version7
import com.eclipsesource.schema.drafts.Version7._
import com.eclipsesource.schema.{ SchemaType, SchemaValidator }
import play.api.libs.json.{ JsResult, JsValue, Json }

import scala.io.Source

trait MessageSchemaValidator {

  val schemaToValidate =
    Json.fromJson[SchemaType](Json.parse(Source.fromFile("conf/message.v4.schema.json").mkString)).get

  def isValidJson(json: JsValue): JsResult[JsValue] = SchemaValidator(Some(Version7)).validate(schemaToValidate, json)

}
