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

package uk.gov.hmrc.securemessage.models

import java.time.{ Instant, ZoneId, ZoneOffset }
import play.api.libs.json.{ JsObject, JsString, Json, OWrites, Writes }

import java.time.format.DateTimeFormatter

final case class RequestCommon(originatingSystem: String, receiptDate: Instant, acknowledgementReference: String)
object RequestCommon {
  implicit val instantWrites: Writes[Instant] = {
    val df: DateTimeFormatter =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneId.from(ZoneOffset.UTC))
    Writes[Instant] { d =>
      JsString(df.format(d))
    }
  }
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
  implicit val queryMessageWrapperWrites: Writes[QueryMessageWrapper] = new Writes[QueryMessageWrapper] {
    def writes(queryMessageWrapper: QueryMessageWrapper): JsObject = Json.obj(
      "querymessageRequest" -> queryMessageWrapper.queryMessageRequest
    )
  }
}
