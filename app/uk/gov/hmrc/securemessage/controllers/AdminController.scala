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

package uk.gov.hmrc.securemessage.controllers

import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.{ Action, AnyContent, ControllerComponents }
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.securemessage.models.v4.{ BrakeBatch, BrakeBatchApproval }
import uk.gov.hmrc.securemessage.repository.Instances

import javax.inject.{ Inject, Singleton }
import scala.concurrent.ExecutionContext
import uk.gov.hmrc.securemessage.models.AllowlistUpdateRequest

@Singleton
class AdminController @Inject() (
  instances: Instances,
  cc: ControllerComponents
)(implicit ec: ExecutionContext)
    extends BackendController(cc) {
  def getGMCBrakeBatches(): Action[AnyContent] = Action.async {
    instances.messageRepository.pullBrakeBatchDetails().map(bs => Ok(Json.toJson(bs)))
  }

  def acceptBrakeBatch(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[BrakeBatchApproval] { brakeBatchApproval =>
      instances.messageRepository.brakeBatchAccepted(brakeBatchApproval).map {
        case true =>
          instances.extraAlertRepository
            .brakeBatchAccepted(brakeBatchApproval)
          Ok
        case false => NotFound(s"No messages found for update in the batch: $brakeBatchApproval.")
      }
    }
  }

  def rejectBrakeBatch(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[BrakeBatchApproval] { brakeBatchApproval =>
      instances.messageRepository.brakeBatchRejected(brakeBatchApproval).map {
        case true =>
          instances.extraAlertRepository
            .brakeBatchRejected(brakeBatchApproval)
          Ok
        case false => NotFound(s"No messages found for update in the batch: $brakeBatchApproval.")
      }
    }
  }

  def randomBrakeBatchMessage(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[BrakeBatch] { request =>
      instances.messageRepository.brakeBatchMessageRandom(request).map {
        case Some(m) => Ok(Json.toJson(m))
        case None    => NotFound
      }
    }
  }

  def getGmcAllowlist(): Action[AnyContent] = Action.async {
    instances.messageBrakeService.getOrInitialiseCachedAllowlist().map {
      case Some(allowlist) => Ok(Json.toJson(allowlist))
      case None            => InternalServerError(Json.obj("error" -> "No allowlist present"))
    }
  }

  def addFormIdToGmcAllowlist(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[AllowlistUpdateRequest] { allowlistUpdateRequest =>
      instances.messageBrakeService
        .addFormIdToAllowlist(allowlistUpdateRequest)
        .map {
          case Some(allowlist) => Created(Json.toJson(allowlist))
          case None            => InternalServerError(Json.obj("error" -> "No allowlist present"))
        }
    }
  }

  def deleteFormIdFromGmcAllowlist(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[AllowlistUpdateRequest] { allowlistUpdateRequest =>
      instances.messageBrakeService
        .deleteFormIdFromAllowlist(allowlistUpdateRequest)
        .map {
          case Some(allowlist) => Ok(Json.toJson(allowlist))
          case None            => InternalServerError(Json.obj("error" -> "No allowlist present"))
        }
    }
  }
}
