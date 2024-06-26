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

import uk.gov.hmrc.auth.core.Enrolments
import uk.gov.hmrc.securemessage.controllers.model.MessageType
import uk.gov.hmrc.securemessage.controllers.utils.IdCoder.DecodedId
import uk.gov.hmrc.securemessage.controllers.utils.{ QueryStringValidationSuccess, ValidCDSQueryParameters }

import javax.inject.{ Inject, Singleton }

@Singleton
class MessageBroker @Inject() (cdsMessageRetriever: CDSMessageRetriever, other: NonCDSMessageRetriever) {
  def messageRetriever(queryResult: QueryStringValidationSuccess): MessageRetriever =
    messageRetriever(queryResult match {
      case ValidCDSQueryParameters => CDS
      case _                       => NonCDS
    })

  def messageRetriever(retrieverType: RetrieverType): MessageRetriever = retrieverType match {
    case CDS    => cdsMessageRetriever
    case NonCDS => other
  }

  def default: NonCDSMessageRetriever = other
}

sealed trait RetrieverType
object CDS extends RetrieverType
object NonCDS extends RetrieverType

case class MessageReadRequest(messageType: MessageType, authEnrolments: Enrolments, messageId: DecodedId)
