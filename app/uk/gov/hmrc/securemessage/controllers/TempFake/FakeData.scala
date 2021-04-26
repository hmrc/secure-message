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

package uk.gov.hmrc.securemessage.controllers.TempFake

import org.joda.time.DateTime
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.securemessage.controllers.model.cdsf.read.{ FirstReaderInformation, Letter, SenderInformation }

object FakeData {

  val message = (id: String) =>
    Letter(
      BSONObjectID.parse(id).get,
      "This is subject of message",
      SenderInformation(None, DateTime.now()),
      FirstReaderInformation(None, DateTime.now()),
      "Message content",
      None
  )

}
