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

package uk.gov.hmrc.securemessage.services

import uk.gov.hmrc.securemessage.repository.AllowlistRepository
import play.api.cache.AsyncCacheApi
import play.api.libs.json.{ Json, OFormat }
import uk.gov.hmrc.securemessage.models.v4.Allowlist

import javax.inject.Inject
import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }

class MessageBrakeService @Inject()(allowlistRepository: AllowlistRepository, cache: AsyncCacheApi) {

  def getOrInitialiseCachedAllowlist()(implicit ec: ExecutionContext): Future[Option[Allowlist]] =
    cache.getOrElseUpdate[Option[Allowlist]]("brake-gmc-allowlist", 1.minute) {
      allowlistRepository.retrieve() flatMap {
        case allowlist @ Some(_) => Future.successful(allowlist)
        case None                => allowlistRepository.store(MessageBrakeAllowList.default.map(_.toUpperCase))
      }
    }

  def allowlistContains(formId: String)(implicit ec: ExecutionContext): Future[Boolean] =
    getOrInitialiseCachedAllowlist().map {
      case Some(allowlist) => allowlist.formIdList.contains(formId.toUpperCase.stripSuffix("_CY"))
      case None            => false
    }

  def addFormIdToAllowlist(request: AllowlistUpdateRequest)(implicit ec: ExecutionContext): Future[Option[Allowlist]] =
    allowlistRepository.retrieve() map {
      case Some(allowlist) => allowlist.formIdList.concat(List(request.formId)).distinct
      case None            => MessageBrakeAllowList.default.concat(List(request.formId)).distinct
    } flatMap storeAllowlistWithCacheRefresh

  def deleteFormIdFromAllowlist(
    request: AllowlistUpdateRequest
  )(implicit ec: ExecutionContext): Future[Option[Allowlist]] =
    allowlistRepository.retrieve() map {
      case Some(allowlist) => allowlist.formIdList.filterNot(_ == request.formId.toUpperCase)
      case None            => MessageBrakeAllowList.default.filterNot(_ == request.formId.toUpperCase)
    } flatMap storeAllowlistWithCacheRefresh

  private def storeAllowlistWithCacheRefresh(
    updatedAllowlist: List[String]
  )(implicit ec: ExecutionContext): Future[Option[Allowlist]] =
    allowlistRepository.store(updatedAllowlist.map(_.toUpperCase)) flatMap {
      case allowlist @ Some(_) =>
        cache.set("brake-gmc-allowlist", allowlist, 1.minute)
        Future.successful(allowlist)

      case None => Future.successful(None)
    }
}

object MessageBrakeAllowList {
  val default = List(
    "SA300",
    "SS300",
    "SA251",
    "SA359",
    "SA316",
    "SA316 2012",
    "SA316 2013",
    "SA316 2014",
    "SA316 2015",
    "SA316 2016",
    "SA316 2017",
    "SA316 2018",
    "SA326D",
    "SA328D",
    "SA370",
    "SA371",
    "SA372",
    "SA372-30",
    "SA372-60",
    "SA373",
    "SA373-30",
    "SA373-60",
    "R002A",
    "ATSV2",
    "P800 2012",
    "P800 2013",
    "P800 2014",
    "P800 2015",
    "P800 2016",
    "P800 2017",
    "P800 2018",
    "P800 2019",
    "P800 2020",
    "P800 2021",
    "P800 2022",
    "PA302 2012",
    "PA302 2013",
    "PA302 2014",
    "PA302 2015",
    "PA302 2016",
    "PA302 2017",
    "PA302 2018",
    "PA302 2019",
    "PA302 2020",
    "PA302 2021",
    "PA302 2022"
  )
}

case class AllowlistUpdateRequest(formId: String, reasonText: String)

object AllowlistUpdateRequest {
  implicit val format: OFormat[AllowlistUpdateRequest] = Json.format[AllowlistUpdateRequest]
}
