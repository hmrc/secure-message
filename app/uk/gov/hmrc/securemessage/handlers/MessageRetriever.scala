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

package uk.gov.hmrc.securemessage.handlers

import play.api.Logging
import play.api.i18n.Messages
import play.api.libs.json.JsValue
import uk.gov.hmrc.common.message.model.Language
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.securemessage.SecureMessageError
import uk.gov.hmrc.securemessage.controllers.model.ApiMessage
import uk.gov.hmrc.securemessage.models.core.MessageRequestWrapper

import scala.concurrent.Future

trait MessageRetriever extends Logging {
  def fetch(requestWrapper: MessageRequestWrapper, language: Language)(implicit
    hc: HeaderCarrier,
    messages: Messages
  ): Future[JsValue]
  def messageCount(
    requestWrapper: MessageRequestWrapper
  )(implicit hc: HeaderCarrier, messages: Messages): Future[JsValue]
  def getMessage(
    readRequest: MessageReadRequest
  )(implicit hc: HeaderCarrier, messages: Messages, language: Language): Future[Either[SecureMessageError, ApiMessage]]
}
