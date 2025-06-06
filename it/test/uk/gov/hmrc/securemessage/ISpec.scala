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

package uk.gov.hmrc.securemessage

import cats.data.NonEmptyList
import org.apache.commons.codec.binary.Base64
import org.bson.types.ObjectId
import org.mongodb.scala.model.Filters
import org.scalatest.concurrent.{ IntegrationPatience, ScalaFutures }
import org.scalatest.{ BeforeAndAfterEach, SuiteMixin }
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.{ Application, Environment, Mode }
import play.api.http.{ ContentTypes, HeaderNames }
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.{ WSClient, WSResponse }
import play.api.Logger.applicationMode
import play.api.test.Helpers.{ await, defaultAwaitTimeout }
import uk.gov.hmrc.securemessage.controllers.model.MessageType
import uk.gov.hmrc.securemessage.models.core.*
import uk.gov.hmrc.securemessage.repository.{ ConversationRepository, MessageRepository }
import org.mongodb.scala.{ ObservableFuture, SingleObservableFuture }
import uk.gov.hmrc.common.message.model.Language
import java.io.File
import java.time.Instant
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Random

trait ISpec
    extends PlaySpec with SuiteMixin with ScalaFutures with IntegrationPatience with BeforeAndAfterEach
    with GuiceOneServerPerSuite with AuthHelper {

  implicit val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]

  override val ggAuthPort: Int = 8585
  override val wsClient: WSClient = app.injector.instanceOf[WSClient]

  protected val conversationRepo: ConversationRepository = app.injector.instanceOf[ConversationRepository]
  protected val messageRepo: MessageRepository = app.injector.instanceOf[MessageRepository]

  override protected def beforeEach(): Unit = {
    await(conversationRepo.collection.deleteMany(Filters.empty()).toFuture())
    await(messageRepo.collection.deleteMany(Filters.empty()).toFuture().map(_ => ()))
  }

  protected def encodeId(id: ObjectId) = base64Encode(s"${MessageType.Conversation.entryName}/$id")

  protected def insertConversation(id: ObjectId) = {
    val conversationId = Random.nextInt(1000).toString
    val messages = NonEmptyList(ConversationMessage(None, 1, Instant.now, "content", None), List.empty)
    val conversation = Conversation(
      id,
      "CDCM",
      conversationId,
      ConversationStatus.Open,
      Some(Map("mrn" -> "DMS7324874993", "notificationType" -> "CDS-EXPORTS")),
      "subject",
      Language.English,
      List(
        Participant(
          1,
          ParticipantType.System,
          Identifier("CDCM", "11111", None),
          Some("CDS Exports Team"),
          None,
          None,
          None
        ),
        Participant(
          2,
          ParticipantType.Customer,
          Identifier("EORINumber", "GB1234567890", Some("HMRC-CUS-ORG")),
          None,
          None,
          None,
          None
        )
      ),
      messages,
      Alert("1", None)
    )
    conversationRepo.insertIfUnique(conversation).futureValue
  }
  protected def createConversation: Future[WSResponse] = {
    val wsClient = app.injector.instanceOf[WSClient]
    wsClient
      .url(resource("/secure-messaging/conversation/cdcm/D-80542-20201120"))
      .withHttpHeaders((HeaderNames.CONTENT_TYPE, ContentTypes.JSON))
      .put(new File("./it/test/resources/cdcm/create-conversation.json"))
  }
  override def fakeApplication(): Application =
    GuiceApplicationBuilder(environment = Environment.simple(mode = applicationMode.getOrElse(Mode.Test)))
      .configure(additionalConfig)
      .build()
  def additionalConfig: Map[String, _] =
    Map(
      "metrics.jvm"                  -> false,
      "play.cache.bindCaches"        -> Seq("controller-cache", "document-cache"),
      "play.cache.createBoundCaches" -> false
    )

  def base64Encode(path: String): String = Base64.encodeBase64String(path.getBytes("UTF-8"))

  def resource(path: String): String = s"http://localhost:$port/${-/(path)}"

  def -/(uri: String): String = if (uri.startsWith("/")) uri.drop(1) else uri

}
