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

class GetMessagesISpec extends ISpec {

  " GET request to /secure-messaging/messages/id" must {
    "return correct message" in {
      val response =
        wsClient
          .url(resource("/secure-messaging/messages/607d5f924a0000a569a9c328"))
          .get()
          .futureValue
      response.status mustBe (200)
      val responseBody = response.body
      responseBody must include("This is subject of message")
      responseBody must include("Message content")
    }
  }
}
