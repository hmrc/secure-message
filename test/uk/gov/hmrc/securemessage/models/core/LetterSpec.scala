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

package uk.gov.hmrc.securemessage.models.core

import com.fasterxml.jackson.core.JsonParseException

import java.time.Instant
import org.mongodb.scala.bson.ObjectId
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{ JsError, JsObject, JsSuccess, JsValue, Json }
import uk.gov.hmrc.securemessage.TestData.*
import uk.gov.hmrc.securemessage.controllers.model.cdsf.read.{ ApiLetter, FirstReaderInformation, SenderInformation }
import uk.gov.hmrc.securemessage.helpers.Resources
import uk.gov.hmrc.securemessage.models.core.Letter.*

class LetterSpec extends PlaySpec {

  "Letter" must {
    "be parsed from json object" in {
      val objectID = new ObjectId()
      val letterJson = Resources.readJson("model/core/letter.json").as[JsObject] +
        ("_id"         -> Json.toJson(objectID)) +
        ("lastUpdated" -> Json.toJson(Instant.now())) +
        ("readTime"    -> Json.toJson(Instant.now()))

      val letter = letterJson.validate[Letter].get
      val apiLetter = ApiLetter.fromCore(letter)

      apiLetter.subject mustBe (letter.subject)
      apiLetter.content mustBe (letter.content.getOrElse(""))
      apiLetter.senderInformation mustBe SenderInformation("HMRC", letter.validFrom)
      apiLetter.firstReaderInformation.get mustBe (FirstReaderInformation(None, letter.readTime.get))
    }

    "be parsed from json object with missing fields" in {
      val objectID = new ObjectId()
      val letterJson = Resources.readJson("model/core/letter-missing-fields.json").as[JsObject] +
        ("_id"         -> Json.toJson(objectID)) +
        ("lastUpdated" -> Json.toJson(Instant.now())) +
        ("readTime"    -> Json.toJson(Instant.now()))

      letterJson.validate[Letter] match {
        case success: JsSuccess[Letter] =>
          val letter = success.get
          val apiLetter = ApiLetter.fromCore(letter)
          apiLetter.subject mustBe (letter.subject)
          apiLetter.content mustBe (letter.content.getOrElse(""))
          apiLetter.senderInformation mustBe SenderInformation("HMRC", letter.validFrom)
          apiLetter.firstReaderInformation.get mustBe (FirstReaderInformation(None, letter.readTime.get))
        case JsError(errors) => fail(s"Failed with errors $errors")
      }

    }

    "be parsed without readTime from json object" in {
      val objectID = new ObjectId()
      val letterJson = Resources.readJson("model/core/letter.json").as[JsObject] +
        ("_id"         -> Json.toJson(objectID)) +
        ("lastUpdated" -> Json.toJson(Instant.now()))

      val letter = letterJson.validate[Letter].get
      val apiLetter = ApiLetter.fromCore(letter)

      apiLetter.subject mustBe (letter.subject)
      apiLetter.content mustBe (letter.content.getOrElse(""))
      apiLetter.senderInformation mustBe SenderInformation("HMRC", letter.validFrom)
      apiLetter.firstReaderInformation mustBe None
    }
  }

  "Details.format" should {
    import Details.format

    "read the json correctly" in new Setup {
      Json.parse(detailsJsonString).as[Details] mustBe details
    }

    "throw exception for the invalid json" in new Setup {
      intercept[JsonParseException] {
        Json.parse(detailsInvalidJsonString).as[Details]
      }
    }

    "write the object correctly" in new Setup {
      Json.toJson(details) mustBe Json.parse(detailsJsonString)
    }
  }

  "Details.toMap" should {
    "return correct map of available values" in new Setup {
      details.toMap.size must be(12)
    }
  }

  trait Setup {
    val details: Details = Details(
      form = Some(TEST_FORM),
      `type` = Some(TEST_TYPE),
      suppressedAt = Some(TEST_DATE_STRING),
      detailsId = Some(TEST_ID),
      paperSent = Some(true),
      batchId = Some(TEST_BATCH_ID),
      issueDate = Some(TEST_DATE),
      replyTo = Some(TEST_EMAIL_ADDRESS_VALUE),
      threadId = Some(TEST_THREAD_ID),
      enquiryType = Some(TEST_ENQUIRY_TYPE),
      adviser = Some(TEST_ADVISER),
      waitTime = Some(TEST_WAIT_TIME),
      topic = Some(TEST_TOPIC),
      envelopId = Some(TEST_ENVELOPE_ID)
    )

    val detailsJsonString: String =
      """{
        |"form":"test_form",
        |"type":"test_type",
        |"suppressedAt":"2025-12-20",
        |"detailsId":"test_id",
        |"paperSent":true,
        |"batchId":"87912345",
        |"issueDate":"2025-12-20",
        |"replyTo":"test@test.com",
        |"threadId":"adfg#1456hjftwer==+gj123",
        |"enquiryType":"test_enquiry",
        |"adviser":{"pidId":"1234567"},
        |"waitTime":"200",
        |"topic":"test_topic",
        |"envelopId":"adfg#1456hjftwer=="
        |}""".stripMargin

    val detailsInvalidJsonString: String =
      """{
        |"form":"test_form",
        |"suppressedAt":"2025-12-20",
        |"detailsId":"test_id",
        |"paperSent":"test",
        |"batchId":"87912345",
        |"issueDate":"2025-12-20",
        |"replyTo":"test@test.com",
        |"threadId":adfg#1456hjftwer==+gj123,
        |"enquiryType":"test_enquiry",
        |"adviser":{"pidId":"1234567"},
        |"waitTime":"200",
        |"topic":"test_topic",
        |"envelopId":"adfg#1456hjftwer=="
        |}""".stripMargin
  }
}
