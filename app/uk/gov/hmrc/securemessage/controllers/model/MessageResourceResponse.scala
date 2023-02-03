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

package uk.gov.hmrc.securemessage.controllers.model

import org.joda.time.{ DateTime, LocalDate }
import play.api.libs.json.{ Writes, _ }
import uk.gov.hmrc.common.message.model.MessageContentParameters
import uk.gov.hmrc.http.controllers.RestFormats
import uk.gov.hmrc.securemessage.models.core.{ Details, Letter, RenderUrl }

final case class ServiceUrl(service: String, url: String)

object ServiceUrl {

  implicit val fmt: Format[ServiceUrl] = Json.format[ServiceUrl]

  def fromRenderUrl(renderUrl: RenderUrl): ServiceUrl = ServiceUrl(renderUrl.service, renderUrl.url)
}

final case class MessageResourceResponse(
  id: String,
  subject: String,
  body: Option[Details],
  validFrom: LocalDate,
  readTime: Either[ServiceUrl, DateTime],
  contentParameters: Option[MessageContentParameters],
  sentInError: Boolean,
  renderUrl: ServiceUrl
) extends ApiMessage

object MessageResourceResponse extends RestFormats {

  import play.api.libs.functional.syntax._

  val readTimeWrites: OWrites[Either[ServiceUrl, DateTime]] = new OWrites[Either[ServiceUrl, DateTime]] {
    def writes(o: Either[ServiceUrl, DateTime]) = o.fold(
      (__ \ "markAsReadUrl").write[ServiceUrl].writes,
      (__ \ "readTime").write[DateTime].writes
    )
  }

  implicit val messageResourceResponseWrites: Writes[MessageResourceResponse] = (
    (__ \ "id").write[String] and
      (__ \ "subject").write[String] and
      (__ \ "body").writeNullable[Details] and
      (__ \ "validFrom").write[LocalDate] and
      readTimeWrites and
      (__ \ "contentParameters").writeNullable[MessageContentParameters] and
      (__ \ "sentInError").write[Boolean] and
      (__ \ "renderUrl").write[ServiceUrl]
  )(m => (m.id, m.subject, m.body, m.validFrom, m.readTime, m.contentParameters, m.sentInError, m.renderUrl))

  def readTimeUrl(letter: Letter, appName: String): ServiceUrl =
    ServiceUrl(appName, s"/messages/${letter._id}/read-time ")

  def from(letter: Letter): MessageResourceResponse =
    MessageResourceResponse(
      letter._id.toString,
      letter.subject,
      letter.body,
      letter.validFrom,
      letter.readTime.toRight(readTimeUrl(letter, "message")),
      letter.contentParameters,
      letter.rescindment.isDefined,
      ServiceUrl.fromRenderUrl(letter.renderUrl)
    )
}
