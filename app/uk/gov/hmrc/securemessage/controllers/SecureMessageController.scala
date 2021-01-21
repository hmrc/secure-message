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

import javax.inject.Inject
import play.api.Logger
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.{ Action, AnyContent, ControllerComponents, Result }
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.{ AuthConnector, AuthorisationException, AuthorisedFunctions, InsufficientEnrolments, NoActiveSession }
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.securemessage.controllers.models.generic.ConversationRequest

import scala.concurrent.{ ExecutionContext, Future }

class SecureMessageController @Inject()(val cc: ControllerComponents, val authConnector: AuthConnector)(
  implicit ec: ExecutionContext)
    extends BackendController(cc) with AuthorisedFunctions {

  def createConversation(client: String, conversationId: String): Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      withJsonBody[ConversationRequest] { _ =>
        Logger.logger.info(client)
        Logger.logger.info(conversationId)
        Future.successful(Created("It works!"))
      }
  }

  def getListOfConversations(enrolment: String): Action[AnyContent] = Action.async { implicit request =>
    Logger.logger.info(enrolment)
    Logger.logger.info(request.toString)
    authorised().retrieve(Retrievals.allEnrolments) {
      case enrolments =>
        enrolments.getEnrolment("HMRC-CUS-ORG") match {
          case Some(en) =>
            en.getIdentifier("EORINumber") match {
              case Some(eori) => Future.successful(Ok(eori.value))
              case None       => Future.successful(Unauthorized(Json.toJson("No EORINumber found")))
            }
          case None => Future.successful(Unauthorized(Json.toJson("EORI enrolment not found")))
        }
    } recover handleError
  }

  def handleError(): PartialFunction[Throwable, Result] = {
    case _: InsufficientEnrolments =>
      Logger.logger.debug("Request user did not have the correct enrolment")
      Unauthorized(Json.toJson("InsufficientEnrolment"))
    case _: NoActiveSession =>
      Logger.logger.debug("Request did not have an Active Session, returning Unauthorised - Unauthenticated Error")
      Unauthorized(Json.toJson("Not authenticated"))
    case _: AuthorisationException =>
      Logger.logger.debug(
        "Request has an active session but was not authorised, returning Forbidden - Not Authorised Error")
      Forbidden(Json.toJson("Not authorised"))
    case e: Exception =>
      Logger.logger.debug(s"Unknown error: ${e.toString}")
      InternalServerError
  }
}
