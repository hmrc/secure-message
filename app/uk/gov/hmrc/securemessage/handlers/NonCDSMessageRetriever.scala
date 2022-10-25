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

package uk.gov.hmrc.securemessage.handlers

import play.api.i18n.Messages
import play.api.libs.json.{ JsValue, Json }
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.securemessage.connectors.AuthIdentifiersConnector
import uk.gov.hmrc.securemessage.controllers.model.MessagesResponse
import uk.gov.hmrc.securemessage.models.core.{ MessageFilter, MessageRequestWrapper }
import uk.gov.hmrc.securemessage.services.SecureMessageServiceImpl

import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

class NonCDSMessageRetriever @Inject()(
  val authIdentifiersConnector: AuthIdentifiersConnector,
  secureMessageService: SecureMessageServiceImpl)(implicit ec: ExecutionContext)
    extends MessageRetriever {
  def fetch(requestWrapper: MessageRequestWrapper)(implicit hc: HeaderCarrier, messages: Messages): Future[JsValue] = {
    implicit val mf: MessageFilter =
      requestWrapper.messageFilter.copy(taxIdentifiers = requestWrapper.messageFilter.taxIdentifiers.flatMap { taxId =>
        taxId match {
          case "HMRC-MTD-VAT" => List(taxId, "vrn")
          case _              => List(taxId)
        }
      })

    for {
      authTaxIds <- authIdentifiersConnector.currentEffectiveTaxIdentifiers
      _          <- Future(logger.warn(s"MessagesController: authTaxIds $authTaxIds"))
      result <- secureMessageService.getMessagesList(authTaxIds).map { items =>
                 MessagesResponse.fromMessages(items).toConversations
               }
    } yield Json.toJson(result)
  }
}
