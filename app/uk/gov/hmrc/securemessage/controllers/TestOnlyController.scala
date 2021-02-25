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

package uk.gov.hmrc.securemessage.controllers

import com.google.inject.Inject
import play.api.mvc.{ Action, AnyContent, ControllerComponents }
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.securemessage.repository.ConversationRepository
import scala.concurrent.ExecutionContext

@SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter"))
class TestOnlyController @Inject()(cc: ControllerComponents, repository: ConversationRepository)(
  implicit ec: ExecutionContext)
  extends BackendController(cc) {

  def deleteConversation(conversationId: String, client: String): Action[AnyContent] = Action.async { _ =>
    repository.deleteConversationForTestOnly(conversationId, client).map { _ =>
      Ok(s"$conversationId deleted successfully")
    }
  }

}
