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

package uk.gov.hmrc.securemessage.models

import org.joda.time.DateTime
import play.api.libs.json.{ JodaWrites, JsObject, Json, OWrites, Writes }

final case class RequestCommon(originatingSystem: String, receiptDate: DateTime, acknowledgementReference: String)
object RequestCommon {
  // ISO-8601
  val dateFormat = "yyyy-MM-dd'T'HH:mm:ssZ"
  implicit val JodaDateWrites: Writes[org.joda.time.DateTime] = JodaWrites.jodaDateWrites(dateFormat)
  implicit val requestCommonWrites: OWrites[RequestCommon] = Json.writes[RequestCommon]
}

final case class RequestDetail(id: String, conversationId: String, message: String)
object RequestDetail {
  implicit val requestDetailWrites: OWrites[RequestDetail] = Json.writes[RequestDetail]
}

final case class QueryMessageRequest(requestCommon: RequestCommon, requestDetail: RequestDetail)
object QueryMessageRequest {
  implicit val queryMessageRequestWrites: OWrites[QueryMessageRequest] = Json.writes[QueryMessageRequest]
}

final case class QueryMessageWrapper(queryMessageRequest: QueryMessageRequest)
object QueryMessageWrapper {
  implicit val queryMessageWrapperWrites = new Writes[QueryMessageWrapper] {
    def writes(queryMessageWrapper: QueryMessageWrapper): JsObject = Json.obj(
      "querymessageRequest" -> queryMessageWrapper.queryMessageRequest
    )
  }
}
