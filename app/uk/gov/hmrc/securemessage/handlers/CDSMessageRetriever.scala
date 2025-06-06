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

import org.mongodb.scala.bson.ObjectId
import play.api.i18n.Messages
import play.api.libs.json.{ JsValue, Json }
import uk.gov.hmrc.auth.core.{ AuthConnector, AuthorisedFunctions }
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.common.message.model.Language
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.securemessage.SecureMessageError
import uk.gov.hmrc.securemessage.controllers.model.MessageType.{ Conversation, Letter }
import uk.gov.hmrc.securemessage.controllers.model.ApiMessage
import uk.gov.hmrc.securemessage.controllers.model.common.read.MessageMetadata
import uk.gov.hmrc.securemessage.controllers.model.common.read.MessageMetadata.EnrolmentsExtensions
import uk.gov.hmrc.securemessage.models.core.{ Filters, MessageRequestWrapper }
import uk.gov.hmrc.securemessage.services.SecureMessageServiceImpl
import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

class CDSMessageRetriever @Inject() (val authConnector: AuthConnector, secureMessageService: SecureMessageServiceImpl)(
  implicit ec: ExecutionContext
) extends MessageRetriever with AuthorisedFunctions {

  def fetch(requestWrapper: MessageRequestWrapper, language: Language)(implicit
    hc: HeaderCarrier,
    messages: Messages
  ): Future[JsValue] =
    authorised()
      .retrieve(Retrievals.allEnrolments) { authEnrolments =>
        val filters = Filters(requestWrapper.enrolmentKeys, requestWrapper.customerEnrolments, requestWrapper.tags)
        secureMessageService
          .getMessages(authEnrolments, filters)
          .map { messagesList =>
            val messageMetadataList: List[MessageMetadata] =
              messagesList.map(m => MessageMetadata(m, authEnrolments, language))
            Json.toJson(messageMetadataList)
          }
      }

  def messageCount(
    requestWrapper: MessageRequestWrapper
  )(implicit hc: HeaderCarrier, messages: Messages): Future[JsValue] =
    authorised()
      .retrieve(Retrievals.allEnrolments) { authEnrolments =>
        val filters = Filters(requestWrapper.enrolmentKeys, requestWrapper.customerEnrolments, requestWrapper.tags)
        secureMessageService
          .getMessagesCount(authEnrolments, filters)
          .map { count =>
            Json.toJson(count)
          }
      }

  def getMessage(readRequest: MessageReadRequest)(implicit
    hc: HeaderCarrier,
    messages: Messages,
    language: Language
  ): Future[Either[SecureMessageError, ApiMessage]] =
    readRequest.messageType match {
      case Conversation =>
        secureMessageService
          .getConversation(new ObjectId(readRequest.messageId), readRequest.authEnrolments.asCustomerEnrolments)
      case Letter =>
        for {
          letter <- secureMessageService
                      .getLetter(new ObjectId(readRequest.messageId), readRequest.authEnrolments.asCustomerEnrolments)
          secureMessage <- if (letter.isLeft) {
                             secureMessageService
                               .getSecureMessage(
                                 new ObjectId(readRequest.messageId),
                                 readRequest.authEnrolments.asCustomerEnrolments
                               )
                           } else {
                             Future.successful(letter)
                           }
        } yield secureMessage
    }
}
