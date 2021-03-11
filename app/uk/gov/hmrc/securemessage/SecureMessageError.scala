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

package uk.gov.hmrc.securemessage

@SuppressWarnings(Array("org.wartremover.warts.DefaultArguments", "org.wartremover.warts.Null"))
sealed class SecureMessageError(val message: String, val cause: Option[Throwable] = None)
    extends Exception(message, cause.orNull)

final case class StoreError(override val message: String, override val cause: Option[Throwable])
    extends SecureMessageError(message, cause)
final case class DuplicateConversationError(override val message: String, override val cause: Option[Throwable])
    extends SecureMessageError(message, cause)
final case class NoReceiverEmailError(override val message: String) extends SecureMessageError(message)
final case class EmailError(override val message: String) extends SecureMessageError(message)
final case class EmailLookupError(override val message: String) extends SecureMessageError(message)
final case class InvalidHtmlContent(override val message: String) extends SecureMessageError(message)
final case class InvalidBase64Content(override val message: String) extends SecureMessageError(message)
final case class NoCaseworkerIdFound(override val message: String) extends SecureMessageError(message)
final case class ConversationIdNotFound(override val message: String) extends SecureMessageError(message)
