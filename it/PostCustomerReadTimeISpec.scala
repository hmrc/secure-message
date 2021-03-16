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

import org.scalatest.DoNotDiscover
import play.api.http.Status.{ BAD_REQUEST, CREATED }
import play.api.http.{ ContentTypes, HeaderNames }

@DoNotDiscover
@SuppressWarnings(Array("org.wartremover.warts.All"))
class PostCustomerReadTimeISpec extends ISpec {

  "A POST request to /secure-messaging/conversation/{client}/{conversationId}/read-time" should {

    "return CREATED when read time added to DB" in {
      createConversation map { _ =>
        val response =
          wsClient
            .url(resource("/secure-messaging/conversation/cdcm/D-80542-20201120/read-time"))
            .withHttpHeaders(buildEoriToken(VALID_EORI), (HeaderNames.CONTENT_TYPE, ContentTypes.JSON))
            .post(new File("./it/resources/read-time.json"))
            .futureValue
        response.status mustBe CREATED
      }
    }

    "return BAD REQUEST when read time not added to DB" in {
      createConversation map { _ =>
        val response =
          wsClient
            .url(resource("/secure-messaging/conversation/cdcm/D-80542-20201120/read-time"))
            .withHttpHeaders(buildNonEoriToken, (HeaderNames.CONTENT_TYPE, ContentTypes.JSON))
            .post(new File("./it/resources/read-time.json"))
            .futureValue
        response.status mustBe BAD_REQUEST
      }
    }
  }

}
