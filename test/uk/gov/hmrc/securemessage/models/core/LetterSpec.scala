/*
 * Copyright 2026 HM Revenue & Customs
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
import play.api.libs.json.{ JsError, JsObject, JsResultException, JsSuccess, JsValue, Json }
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

      intercept[JsonParseException] {
        Json.parse(detailsInvalidJsonString1).as[Details]
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

  "AlertDetails.format" should {
    import AlertDetails.format

    "read the json correctly" in new Setup {
      Json.parse(alertDetailsJsonString).as[AlertDetails] mustBe alertDetails
    }

    "throw exception for the invalid json" in new Setup {
      intercept[JsResultException] {
        Json.parse(alertDetailsInvalidJsonString).as[AlertDetails]
      }
    }

    "write the object correctly" in new Setup {
      Json.toJson(alertDetails) mustBe Json.parse(alertDetailsJsonString)
    }
  }

  "ExternalReference.format" should {
    import ExternalReference.externalReferenceFormat

    "read the json correctly" in new Setup {
      Json.parse(externalReferenceJsonString).as[ExternalReference] mustBe externalReference
    }

    "throw exception for the invalid json" in new Setup {
      intercept[JsResultException] {
        Json.parse(externalReferenceInvalidJsonString).as[ExternalReference]
      }
    }

    "write the object correctly" in new Setup {
      Json.toJson(externalReference) mustBe Json.parse(externalReferenceJsonString)
    }
  }

  "EmailAlert.format" should {
    import EmailAlert.emailAlertFormat

    "read the json correctly" in new Setup {
      Json.parse(emailAlertJsonString).as[EmailAlert] mustBe emailAlert
    }

    "throw exception for the invalid json" in new Setup {
      intercept[JsResultException] {
        Json.parse(emailAlertInvalidJsonString).as[EmailAlert]
      }
    }

    "write the object correctly" in new Setup {
      Json.toJson(emailAlert) mustBe Json.parse(emailAlertJsonString)
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

    val alertDetails: AlertDetails = AlertDetails(
      templateId = TEST_TEMPLATE_ID,
      recipientName = Some(
        RecipientName(
          title = Some(TEST_TITLE),
          forename = Some(TEST_NAME),
          secondForename = None,
          surname = None,
          honours = None,
          line1 = None
        )
      )
    )

    val externalReference: ExternalReference = ExternalReference(TEST_ID, TEST_SOURCE)
    val emailAlert: EmailAlert =
      EmailAlert(
        emailAddress = Some(TEST_EMAIL_ADDRESS_VALUE),
        success = true,
        failureReason = Some("connection_error")
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

    val detailsInvalidJsonString1: String =
      """{
        |"issueDate":true,
        |}""".stripMargin

    val alertDetailsJsonString: String =
      """{"templateId":"test_template_id","recipientName":{"title":"test_title","forename":"test_name"}}""".stripMargin

    val alertDetailsInvalidJsonString: String =
      """{"recipientName":{"title":"test_title","forename":"test_name"}}""".stripMargin

    val externalReferenceJsonString: String = """{"id":"test_id","source":"gmc"}""".stripMargin
    val externalReferenceInvalidJsonString: String = """{"source":"gmc"}""".stripMargin

    val emailAlertJsonString: String =
      """{"emailAddress":"test@test.com","success":true,"failureReason":"connection_error"}""".stripMargin

    val emailAlertInvalidJsonString: String =
      """{"emailAddress":"test@test.com","failureReason":"connection_error"}""".stripMargin
  }
}
