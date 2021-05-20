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

import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import play.api.http.{ ContentTypes, HeaderNames }
import play.api.libs.ws.{ WSClient, WSResponse }
import play.api.test.Helpers._
import uk.gov.hmrc.integration.ServiceSpec
import uk.gov.hmrc.securemessage.repository.{ ConversationRepository, MessageRepository }
import java.io.File
import scala.concurrent.{ ExecutionContext, Future }

trait ISpec extends PlaySpec with ServiceSpec with BeforeAndAfterEach with AuthHelper {

  implicit val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]
//  implicit val timeout: Timeout = Timeout(1, TimeUnit.MINUTES)

  override def externalServices: Seq[String] = Seq.empty
  override val ggAuthPort: Int = 8585
  override val wsClient: WSClient = app.injector.instanceOf[WSClient]

  protected val conversationRepo: ConversationRepository = app.injector.instanceOf[ConversationRepository]
  protected val messageRepo: MessageRepository = app.injector.instanceOf[MessageRepository]

  override protected def beforeEach(): Unit = {
    await(conversationRepo.removeAll())
    await(messageRepo.removeAll())
    ()
  }

  protected def createConversation: Future[WSResponse] = {
    val wsClient = app.injector.instanceOf[WSClient]
    wsClient
      .url(resource("/secure-messaging/conversation/cdcm/D-80542-20201120"))
      .withHttpHeaders((HeaderNames.CONTENT_TYPE, ContentTypes.JSON))
      .put(new File("./it/resources/cdcm/create-conversation.json"))
  }

  override def additionalConfig: Map[String, _] = Map("metrics.jvm" -> false)

}
