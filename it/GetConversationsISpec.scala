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

import java.io.File

import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import play.api.http.{ ContentTypes, HeaderNames }
import play.api.libs.ws.{ WSClient, WSResponse }
import play.api.test.Helpers._
import uk.gov.hmrc.integration.ServiceSpec
import uk.gov.hmrc.securemessage.repository.ConversationRepository
import utils.AuthHelper

import scala.concurrent.{ ExecutionContext, Future }

@SuppressWarnings(Array("org.wartremover.warts.All"))
class GetConversationsISpec extends PlaySpec with ServiceSpec with BeforeAndAfterEach with AuthHelper {

  override def externalServices: Seq[String] = Seq("auth-login-api")
  override val ggAuthPort: Int = externalServicePorts("auth-login-api")
  override val wsClient: WSClient = app.injector.instanceOf[WSClient]

  val repository: ConversationRepository = app.injector.instanceOf[ConversationRepository]
  val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]

  override protected def beforeEach(): Unit = {
    val _ = await(repository.removeAll()(ec))
  }

  "A GET request to /secure-messaging/conversations" should {

    "return a JSON body of conversation metadata" in {
      createConversation
      val response =
        wsClient
          .url(resource("/secure-messaging/conversations/hmrc-cus-org/eorinumber"))
          .withHttpHeaders(buildEoriToken(VALID_EORI))
          .get()
          .futureValue
      response.body must include("""senderName":"CDS Exports Team""")
    }

    "return a JSON body of [No EORI enrolment found] when there's an auth session, but no EORI enrolment" in {
      val response =
        wsClient
          .url(resource("/secure-messaging/conversations/hmrc-cus-org/eorinumber"))
          .withHttpHeaders(buildNonEoriToken)
          .get()
          .futureValue
      response.body mustBe "\"No EORI enrolment found\""
    }
  }

  def createConversation: Future[WSResponse] = {
    val wsClient = app.injector.instanceOf[WSClient]
    wsClient
      .url(resource("/secure-messaging/conversation/cdcm/D-80542-20201120"))
      .withHttpHeaders((HeaderNames.CONTENT_TYPE, ContentTypes.JSON))
      .put(new File("./it/resources/create-conversation-full.json"))
  }

}
