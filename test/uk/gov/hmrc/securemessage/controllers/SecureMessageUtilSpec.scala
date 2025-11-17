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

package uk.gov.hmrc.securemessage.controllers

import com.mongodb.client.result.DeleteResult
import org.apache.commons.codec.binary.Base64
import org.bson.types.ObjectId
import org.mockito.ArgumentMatchers.{ any, eq as meq }
import org.mockito.Mockito.{ doNothing, reset, verifyNoInteractions, when }
import org.mongodb.scala.result.DeleteResult
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.Configuration
import play.api.i18n.{ Lang, Messages, MessagesImpl }
import uk.gov.hmrc.common.message.model.TaxpayerName
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.securemessage.connectors.{ EntityResolverConnector, PreferencesConnector, TaxpayerNameConnector }
import uk.gov.hmrc.securemessage.helpers.Resources
import uk.gov.hmrc.securemessage.models.v4.{ Content, ExtraAlertConfig, SecureMessage }
import uk.gov.hmrc.securemessage.repository.{ ExtraAlertRepository, SecureMessageRepository, StatsMetricRepository }
import uk.gov.hmrc.securemessage.services.MessageBrakeService
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import play.api.mvc.AnyContentAsEmpty
import uk.gov.hmrc.play.audit.http.connector.AuditResult
import uk.gov.hmrc.play.audit.model.EventTypes
import play.api.libs.json.Json
import play.i18n
import uk.gov.hmrc.common.message.model.{ Regime, TaxEntity }
import uk.gov.hmrc.domain.{ SimpleName, TaxIdentifier }

import scala.concurrent.{ ExecutionContext, Future }
import uk.gov.hmrc.common.message.model.Language

import java.time.Duration

class SecureMessageUtilSpec extends PlaySpec with ScalaFutures with MockitoSugar with BeforeAndAfterEach {
  val appName: String = "Test"
  val preferencesConnector: PreferencesConnector = mock[PreferencesConnector]
  val taxpayerNameConnector: TaxpayerNameConnector = mock[TaxpayerNameConnector]
  val secureMessageRepository: SecureMessageRepository = mock[SecureMessageRepository]
  val extraAlertRepository: ExtraAlertRepository = mock[ExtraAlertRepository]
  val statsMetricRepository: StatsMetricRepository = mock[StatsMetricRepository]
  val messageBrakeService: MessageBrakeService = mock[MessageBrakeService]
  val auditConnector: AuditConnector = mock[AuditConnector]
  val configuration: Configuration = Configuration.from(
    Map(
      "alertProfile" -> List()
    )
  )
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  implicit val hc: HeaderCarrier = HeaderCarrier()
  val testUtil = new SecureMessageUtil(
    appName,
    preferencesConnector,
    taxpayerNameConnector,
    secureMessageRepository,
    extraAlertRepository,
    statsMetricRepository,
    messageBrakeService,
    auditConnector,
    configuration
  ) {
    override val extraAlerts: List[ExtraAlertConfig] = List(
      ExtraAlertConfig("mainTemplate", "extra", Duration.ofSeconds(2))
    )
  }

  "addTaxpayerNameToMessageIfRequired" must {
    "get the tax-payer name when the tax identifier is SaUtr" in {
      val taxpayerName = Some(TaxpayerName(forename = Some("test-user")))

      when(taxpayerNameConnector.taxpayerName(meq(SaUtr("1234567890")))(any[HeaderCarrier]))
        .thenReturn(Future.successful(taxpayerName))

      val message: SecureMessage = Resources.readJson("model/core/v4/valid_SAUTR_message.json").as[SecureMessage]
      val updatedAlertDetails = message.alertDetails.copy(recipientName = taxpayerName)
      testUtil.addTaxpayerNameToMessageIfRequired(message).futureValue mustBe message.copy(
        alertDetails = updatedAlertDetails
      )
    }

    "not get the tax-payer name when the tax identifier is nino" in {
      val message: SecureMessage =
        Resources.readJson("model/core/v4/valid_NI_message_without_name.json").as[SecureMessage]
      testUtil.addTaxpayerNameToMessageIfRequired(message).futureValue mustBe message
      verifyNoInteractions(taxpayerNameConnector)
    }

    "not get the tax-payer name when recipient name is present in the message" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      testUtil.addTaxpayerNameToMessageIfRequired(message).futureValue mustBe message
      verifyNoInteractions(taxpayerNameConnector)
    }
  }

  "buildAuditMessageContent function" must {
    "include subject and issue data to message body" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val content = testUtil.buildAuditMessageContent(message).get
      val decoded = new String(Base64.decodeBase64(content.getBytes("UTF-8")))

      decoded must include("Reminder to file a Self Assessment return")
      decoded must include("This message was sent to you on")
    }
  }

  "isGmc" must {
    "return true when source is 'gmc'" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val gmcMessage = message.copy(externalRef = message.externalRef.copy(source = "gmc"))
      SecureMessageUtil.isGmc(gmcMessage) mustBe true
    }

    "return true when source is 'GMC' (case insensitive)" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val gmcMessage = message.copy(externalRef = message.externalRef.copy(source = "GMC"))
      SecureMessageUtil.isGmc(gmcMessage) mustBe true
    }

    "return false when source is not 'gmc'" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      SecureMessageUtil.isGmc(message) mustBe false
    }
  }

  "extractMessageDate" must {
    "return issueDate when present in details" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val result = SecureMessageUtil.extractMessageDate(message)
      result must not be empty
    }

    "return validFrom when issueDate is not present" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val messageWithoutIssueDate = message.copy(details = message.details.map(_.copy(issueDate = None)))
      val result = SecureMessageUtil.extractMessageDate(messageWithoutIssueDate)
      result mustBe SecureMessageUtil.formatter(message.validFrom)
    }

    "format date correctly" in {
      import java.time.LocalDate
      val date = LocalDate.of(2023, 12, 25)
      SecureMessageUtil.formatter(date) mustBe "25 December 2023"
    }
  }

  "checkPreferencesAndCreateMessage" must {
    import play.api.test.FakeRequest
    import play.api.test.Helpers._
    import play.api.mvc.AnyContentAsEmpty
    import cats.data.EitherT
    import uk.gov.hmrc.securemessage.connectors.{ EmailValidation, VerifiedEmailNotFound }

    implicit val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

    "return BAD_REQUEST when email is not verified" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val messageWithoutEmail = message.copy(recipient = message.recipient.copy(email = None))

      when(
        preferencesConnector
          .verifiedEmailAddress(any[uk.gov.hmrc.common.message.model.TaxEntity]())(any[HeaderCarrier]())
      )
        .thenReturn(Future.successful(VerifiedEmailNotFound("NOT_OPTED_IN")))

      val result = testUtil.checkPreferencesAndCreateMessage(messageWithoutEmail)
      status(result) mustBe BAD_REQUEST
      contentAsString(result) must include("email: not verified as user not opted in")
    }

    "return BAD_REQUEST when preferences connector returns PREFERENCES_NOT_FOUND" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val messageWithoutEmail = message.copy(recipient = message.recipient.copy(email = None))

      when(
        preferencesConnector
          .verifiedEmailAddress(any[uk.gov.hmrc.common.message.model.TaxEntity]())(any[HeaderCarrier]())
      )
        .thenReturn(Future.successful(VerifiedEmailNotFound("PREFERENCES_NOT_FOUND")))

      val result = testUtil.checkPreferencesAndCreateMessage(messageWithoutEmail)
      status(result) mustBe BAD_REQUEST
      contentAsString(result) must include("email: not verified as preferences not found")
    }

    "return NOT_FOUND when preferences connector returns EMAIL_ADDRESS_NOT_VERIFIED" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val messageWithoutEmail = message.copy(recipient = message.recipient.copy(email = None))

      when(
        preferencesConnector
          .verifiedEmailAddress(any[uk.gov.hmrc.common.message.model.TaxEntity]())(any[HeaderCarrier]())
      )
        .thenReturn(Future.successful(VerifiedEmailNotFound("EMAIL_ADDRESS_NOT_VERIFIED")))

      val result = testUtil.checkPreferencesAndCreateMessage(messageWithoutEmail)
      status(result) mustBe NOT_FOUND
      contentAsString(result) must include(
        "The backend has rejected the message due to not being able to verify the email address"
      )
    }

    "return NOT_FOUND when preferences connector throws exception" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val messageWithoutEmail = message.copy(recipient = message.recipient.copy(email = None))

      when(
        preferencesConnector
          .verifiedEmailAddress(any[uk.gov.hmrc.common.message.model.TaxEntity]())(any[HeaderCarrier]())
      )
        .thenReturn(Future.failed(new RuntimeException("Connection error")))

      val result = testUtil.checkPreferencesAndCreateMessage(messageWithoutEmail)
      status(result) mustBe NOT_FOUND
      contentAsString(result) must include(
        "The backend has rejected the message due to not being able to verify the email address"
      )
    }
  }

  "isValidTaxIdentifier" must {
    "return true for valid tax identifiers" in {
      testUtil.isValidTaxIdentifier("nino") mustBe true
      testUtil.isValidTaxIdentifier("sautr") mustBe true
      testUtil.isValidTaxIdentifier("ctutr") mustBe true
      testUtil.isValidTaxIdentifier("HMRC-OBTDS-ORG") mustBe true
      testUtil.isValidTaxIdentifier("HMRC-MTD-VAT") mustBe true
      testUtil.isValidTaxIdentifier("empRef") mustBe true
      testUtil.isValidTaxIdentifier("HMCE-VATDEC-ORG") mustBe true
      testUtil.isValidTaxIdentifier("HMRC-CUS-ORG") mustBe true
      testUtil.isValidTaxIdentifier("HMRC-PPT-ORG") mustBe true
      testUtil.isValidTaxIdentifier("HMRC-MTD-IT") mustBe true
    }

    "return false for invalid tax identifiers" in {
      testUtil.isValidTaxIdentifier("invalid") mustBe false
      testUtil.isValidTaxIdentifier("") mustBe false
      testUtil.isValidTaxIdentifier("unknown-id") mustBe false
    }
  }

  "checkValidSourceData" must {
    "return success when sourceData is valid base64" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val validBase64 = Base64.encodeBase64String("test-data".getBytes("UTF-8"))
      val messageWithSourceData = message.copy(details = message.details.map(_.copy(sourceData = Some(validBase64))))
      testUtil.checkValidSourceData(messageWithSourceData).isSuccess mustBe true
    }

    "return failure when sourceData is empty string" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val messageWithEmptySourceData = message.copy(details = message.details.map(_.copy(sourceData = Some(""))))
      testUtil.checkValidSourceData(messageWithEmptySourceData).isFailure mustBe true
    }

    "return failure when sourceData is not valid base64" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val messageWithInvalidSourceData =
        message.copy(details = message.details.map(_.copy(sourceData = Some("not-base64!!!"))))
      testUtil.checkValidSourceData(messageWithInvalidSourceData).isFailure mustBe true
    }

    "return success when sourceData is None for non-GMC messages" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val messageWithoutSourceData = message.copy(details = message.details.map(_.copy(sourceData = None)))
      testUtil.checkValidSourceData(messageWithoutSourceData).isSuccess mustBe true
    }
  }

  "checkDetailsIsPresent" must {
    "return success when message is non-GMC" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      testUtil.checkDetailsIsPresent(message).isSuccess mustBe true
    }

    "return success when message is GMC and has formId in details" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val gmcMessage = message.copy(externalRef = message.externalRef.copy(source = "gmc"))
      testUtil.checkDetailsIsPresent(gmcMessage).isSuccess mustBe true
    }

    "return failure when message is GMC and details has empty formId" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val gmcMessage = message.copy(
        externalRef = message.externalRef.copy(source = "gmc"),
        details = message.details.map(_.copy(formId = ""))
      )
      testUtil.checkDetailsIsPresent(gmcMessage).isFailure mustBe true
    }
  }

  "checkEmptyEmailAddress" must {
    "return success when email is not empty" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val messageWithEmail =
        message.copy(alertDetails = message.alertDetails.copy(data = Map("email" -> "test@test.com")))
      testUtil.checkEmptyEmailAddress(messageWithEmail).isSuccess mustBe true
    }

    "return success when email is not present" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      testUtil.checkEmptyEmailAddress(message).isSuccess mustBe true
    }

    "return failure when email is empty string" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val messageWithEmptyEmail = message.copy(alertDetails = message.alertDetails.copy(data = Map("email" -> "")))
      testUtil.checkEmptyEmailAddress(messageWithEmptyEmail).isFailure mustBe true
    }
  }

  "checkEmptyAlertQueue" must {
    "return success when alertQueue is valid" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val messageWithAlertQueue = message.copy(alertQueue = Some("PRIORITY"))
      testUtil.checkEmptyAlertQueue(messageWithAlertQueue).isSuccess mustBe true
    }

    "return success when alertQueue is None" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val messageWithoutAlertQueue = message.copy(alertQueue = None)
      testUtil.checkEmptyAlertQueue(messageWithoutAlertQueue).isSuccess mustBe true
    }

    "return failure when alertQueue is empty string" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val messageWithEmptyAlertQueue = message.copy(alertQueue = Some(""))
      testUtil.checkEmptyAlertQueue(messageWithEmptyAlertQueue).isFailure mustBe true
    }
  }

  "checkValidIssueDate" must {
    "return success when issueDate equals validFrom" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val messageWithMatchingDates =
        message.copy(details = message.details.map(_.copy(issueDate = Some(message.validFrom))))
      testUtil.checkValidIssueDate(messageWithMatchingDates).isSuccess mustBe true
    }

    "return success when issueDate is before validFrom" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val messageWithEarlierIssueDate =
        message.copy(details = message.details.map(_.copy(issueDate = Some(message.validFrom.minusDays(1)))))
      testUtil.checkValidIssueDate(messageWithEarlierIssueDate).isSuccess mustBe true
    }

    "return failure when issueDate is after validFrom" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val messageWithLaterIssueDate =
        message.copy(details = message.details.map(_.copy(issueDate = Some(message.validFrom.plusDays(1)))))
      testUtil.checkValidIssueDate(messageWithLaterIssueDate).isFailure mustBe true
    }

    "return success when issueDate is not present" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val messageWithoutIssueDate = message.copy(details = message.details.map(_.copy(issueDate = None)))
      testUtil.checkValidIssueDate(messageWithoutIssueDate).isSuccess mustBe true
    }
  }

  "checkInvalidEmailAddress" must {
    "return success when email is valid" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val messageWithValidEmail = message.copy(recipient = message.recipient.copy(email = Some("test@example.com")))
      testUtil.checkInvalidEmailAddress(messageWithValidEmail).isSuccess mustBe true
    }

    "return success when email is None" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val messageWithoutEmail = message.copy(recipient = message.recipient.copy(email = None))
      testUtil.checkInvalidEmailAddress(messageWithoutEmail).isSuccess mustBe true
    }

    "return failure when email is invalid" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val messageWithInvalidEmail = message.copy(recipient = message.recipient.copy(email = Some("invalid-email")))
      testUtil.checkInvalidEmailAddress(messageWithInvalidEmail).isFailure mustBe true
    }
  }

  "checkEmailAbsentIfInvalidTaxId" must {
    "return success when email is present" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val messageWithEmail = message.copy(recipient = message.recipient.copy(email = Some("test@example.com")))
      testUtil.checkEmailAbsentIfInvalidTaxId(messageWithEmail).isSuccess mustBe true
    }

    "return success when email is None but taxId is valid" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val messageWithValidTaxId = message.copy(recipient = message.recipient.copy(email = None))
      testUtil.checkEmailAbsentIfInvalidTaxId(messageWithValidTaxId).isSuccess mustBe true
    }

    "return failure when email is None and taxId is invalid" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      import uk.gov.hmrc.common.message.model.{ TaxEntity, Regime }
      import uk.gov.hmrc.domain.{ TaxIdentifier, SimpleName }

      case class InvalidTaxId(value: String) extends TaxIdentifier with SimpleName {
        override val name = "invalid-taxid"
      }

      val messageWithInvalidTaxId = message.copy(
        recipient = TaxEntity(Regime.paye, InvalidTaxId("test-value"), None)
      )
      testUtil.checkEmailAbsentIfInvalidTaxId(messageWithInvalidTaxId).isFailure mustBe true
    }
  }

  "checkValidAlertQueue" must {
    "return success when alertQueue is None" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val messageWithoutAlertQueue = message.copy(alertQueue = None)
      testUtil.checkValidAlertQueue(messageWithoutAlertQueue).isSuccess mustBe true
    }

    "return failure when alertQueue is invalid" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val messageWithInvalidAlertQueue = message.copy(alertQueue = Some("INVALID_QUEUE"))
      testUtil.checkValidAlertQueue(messageWithInvalidAlertQueue).isFailure mustBe true
    }
  }

  "checkValidContent" must {
    "return success when content body is valid base64" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val validBase64Body = Base64.encodeBase64String("test content".getBytes("UTF-8"))
      val messageWithValidContent = message.copy(
        content = message.content.map(content => content.copy(body = validBase64Body))
      )
      testUtil.checkValidContent(messageWithValidContent).isSuccess mustBe true
    }

    "return success even when content body is not valid base64 (documents bug in checkValidContent)" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val messageWithInvalidContent = message.copy(
        content = message.content.map(content => content.copy(body = "not-base64!!!"))
      )
      testUtil.checkValidContent(messageWithInvalidContent).isSuccess mustBe false
    }
  }

  "ignoreAlertQueueIfGmcAndSa" must {
    "remove alertQueue when message is GMC and identifier is SAUTR" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_SAUTR_message.json").as[SecureMessage]
      val gmcMessage = message.copy(
        externalRef = message.externalRef.copy(source = "gmc"),
        alertQueue = Some("PRIORITY")
      )
      testUtil.ignoreAlertQueueIfGmcAndSa(gmcMessage).futureValue.alertQueue mustBe None
    }

    "keep alertQueue when message is GMC but identifier is not SAUTR" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val gmcMessage = message.copy(
        externalRef = message.externalRef.copy(source = "gmc"),
        alertQueue = Some("PRIORITY")
      )
      testUtil.ignoreAlertQueueIfGmcAndSa(gmcMessage).futureValue.alertQueue mustBe Some("PRIORITY")
    }

    "keep alertQueue when message is not GMC" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val nonGmcMessage = message.copy(alertQueue = Some("PRIORITY"))
      testUtil.ignoreAlertQueueIfGmcAndSa(nonGmcMessage).futureValue.alertQueue mustBe Some("PRIORITY")
    }
  }

  "handleBiggerContent" must {
    import play.api.libs.json._

    "remove both sourceData and content when both are present" in {
      val body = Json.obj(
        "sourceData" -> "some-data",
        "content"    -> Json.arr(Json.obj("body" -> "content-body")),
        "other"      -> "field"
      )
      val result = testUtil.handleBiggerContent(body)
      result must include("sourceData is removed to reduce size")
      result must include("content is removed to reduce size")
      result must include("other")
    }

    "remove only content when only content is present" in {
      val body = Json.obj(
        "content" -> Json.arr(Json.obj("body" -> "content-body")),
        "other"   -> "field"
      )
      val result = testUtil.handleBiggerContent(body)
      result must include("content is removed to reduce size")
      result must not include "sourceData"
      result must include("other")
    }

    "remove only sourceData when only sourceData is present" in {
      val body = Json.obj(
        "sourceData" -> "some-data",
        "other"      -> "field"
      )
      val result = testUtil.handleBiggerContent(body)
      result must include("sourceData is removed to reduce size")
      result must not include "content"
      result must include("other")
    }

    "keep body unchanged when neither sourceData nor content is present" in {
      val body = Json.obj(
        "other"   -> "field",
        "another" -> "value"
      )
      val result = testUtil.handleBiggerContent(body)
      result must include("other")
      result must include("another")
      result must not include "sourceData"
      result must not include "content"
    }
  }

  "cleanupContent" must {
    "successfully clean up all content in the message" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val result = testUtil.cleanupContent(message).futureValue
      result.content.size mustBe message.content.size
    }

    "clean HTML from subject and body" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val dirtySubject = "<script>alert('xss')</script>Clean Subject"
      val dirtyBody = Base64.encodeBase64String("<div>Test</div>".getBytes("UTF-8"))
      val dirtyContent = message.content.head.copy(subject = dirtySubject, body = dirtyBody)
      val messageWithDirtyContent = message.copy(content = List(dirtyContent))

      val result = testUtil.cleanupContent(messageWithDirtyContent).futureValue
      result.content.head.subject must not include "<script>"
    }
  }

  "cleanUpSubjectAndBody" must {
    "clean up subject successfully" in {
      val dirtySubject = "Clean <b>Subject</b>"
      val body = "<p>Test Body</p>"
      val content = Content(Language.English, dirtySubject, body)

      val result = testUtil.cleanUpSubjectAndBody(content)
      result.subject must not be empty
      result.subject must include("Clean")
      result.subject must include("Subject")
    }

    "clean up body successfully with allowed tags" in {
      val subject = "Test Subject"
      val dirtyBody = "<details><summary>Summary</summary><p>Content</p></details>"
      val content = Content(Language.English, subject, dirtyBody)

      val result = testUtil.cleanUpSubjectAndBody(content)
      result.body must include("details")
      result.body must include("summary")
    }

    "remove script tags from subject" in {
      val dirtySubject = "Subject <script>alert('xss')</script>"
      val body = "<p>Body</p>"
      val content = Content(Language.English, dirtySubject, body)

      val result = testUtil.cleanUpSubjectAndBody(content)
      result.subject must not include "<script>"
    }

    "throw exception when subject cleaning fails" in {
      val content = Content(Language.English, null, "body")
      intercept[NullPointerException] {
        testUtil.cleanUpSubjectAndBody(content)
      }
    }

    "throw exception when body cleaning fails" in {
      val subject = "Subject"
      val content = Content(Language.English, subject, null)
      intercept[NullPointerException] {
        testUtil.cleanUpSubjectAndBody(content)
      }
    }
  }

  "auditCreateMessageFor" must {

    implicit val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

    "send audit event for succeeded message creation" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]

      when(auditConnector.sendEvent(any())(any(), any())).thenReturn(Future.successful(AuditResult.Success))

      testUtil.auditCreateMessageFor(EventTypes.Succeeded, message, "Message Created").futureValue
    }

    "send audit event for failed message creation" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]

      when(auditConnector.sendEvent(any())(any(), any())).thenReturn(Future.successful(AuditResult.Success))

      testUtil.auditCreateMessageFor(EventTypes.Failed, message, "Message Failed").futureValue
    }

    "include all message details in audit event" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]

      when(auditConnector.sendEvent(any())(any(), any())).thenReturn(Future.successful(AuditResult.Success))

      testUtil.auditCreateMessageFor(EventTypes.Succeeded, message, "Test Transaction").futureValue
    }

    "handle large request body by truncating content" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val largeBody = "x" * 200000
      val largeFakeRequestWithJson = FakeRequest().withJsonBody(Json.obj("content" -> largeBody))

      when(auditConnector.sendEvent(any())(any(), any())).thenReturn(Future.successful(AuditResult.Success))

      testUtil
        .auditCreateMessageFor(EventTypes.Succeeded, message, "Large Message")(hc, largeFakeRequestWithJson)
        .futureValue
    }

    "handle audit connector returning Disabled status" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]

      when(auditConnector.sendEvent(any())(any(), any())).thenReturn(Future.successful(AuditResult.Disabled))

      testUtil.auditCreateMessageFor(EventTypes.Succeeded, message, "Message Created").futureValue
    }

    "handle audit connector returning Failure status" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]

      when(auditConnector.sendEvent(any())(any(), any()))
        .thenReturn(Future.successful(AuditResult.Failure("Audit failed", None)))

      testUtil.auditCreateMessageFor(EventTypes.Failed, message, "Message Failed").futureValue
    }

    "include message content in audit event" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]

      when(auditConnector.sendEvent(any())(any(), any())).thenReturn(Future.successful(AuditResult.Success))

      val result = testUtil.auditCreateMessageFor(EventTypes.Succeeded, message, "Message Created").futureValue
      result mustBe ()
    }
  }

  "localizedExtractMessageDate" must {
    import play.api.i18n.Messages
    implicit val messagesImpl: Messages = new Messages {
      override def lang: Lang = Lang("en")

      override def apply(key: String, args: Any*): String = ???

      override def apply(keys: Seq[String], args: Any*): String = ???

      override def translate(key: String, args: Seq[Any]): Option[String] = ???

      override def isDefinedAt(key: String): Boolean = ???

      override def asJava: i18n.Messages = ???
    }

    "return formatted issueDate when present in details" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]

      val result = SecureMessageUtil.localizedExtractMessageDate(message)
      result must not be empty
    }

    "return formatted validFrom when issueDate is not present" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val messageWithoutIssueDate = message.copy(details = message.details.map(_.copy(issueDate = None)))
      val result = SecureMessageUtil.localizedExtractMessageDate(messageWithoutIssueDate)
      result mustBe SecureMessageUtil.formatter(message.validFrom)
    }

    "format date correctly for English" in {
      import java.time.LocalDate
      val date = LocalDate.of(2023, 12, 25)
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val messageWithIssueDate = message.copy(details = message.details.map(_.copy(issueDate = Some(date))))
      SecureMessageUtil.localizedExtractMessageDate(messageWithIssueDate) mustBe "25 December 2023"
    }

    "format date correctly for Welsh" in {
      import java.time.LocalDate
      implicit val messagesWelshImpl: Messages = new Messages {
        override def lang: Lang = Lang("cy")

        override def apply(key: String, args: Any*): String = "Rhagfyr"

        override def apply(keys: Seq[String], args: Any*): String = ???

        override def translate(key: String, args: Seq[Any]): Option[String] = ???

        override def isDefinedAt(key: String): Boolean = ???

        override def asJava: i18n.Messages = ???
      }
      val date = LocalDate.of(2023, 12, 25)
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val messageWithWelshIssueDate = message.copy(details = message.details.map(_.copy(issueDate = Some(date))))
      SecureMessageUtil.localizedExtractMessageDate(messageWithWelshIssueDate)(
        messagesWelshImpl
      ) mustBe "25 Rhagfyr 2023"
    }
  }

  "isValidSecureMessage" must {
    "return success for a valid message" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val validMessage = message.copy(details = message.details.map(_.copy(sourceData = None)))
      testUtil.isValidSecureMessage(validMessage).isSuccess mustBe true
    }

    "return success for a valid message with email" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val messageWithEmail = message.copy(
        recipient = message.recipient.copy(email = Some("test@gmail.com")),
        details = message.details.map(_.copy(sourceData = None))
      )
      testUtil.isValidSecureMessage(messageWithEmail).isSuccess mustBe true
    }

    "return failure when sourceData is invalid" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val messageWithInvalidSourceData =
        message.copy(details = message.details.map(_.copy(sourceData = Some("invalid_source_data!!!"))))
      val result = testUtil.isValidSecureMessage(messageWithInvalidSourceData)
      result.isFailure mustBe true
      result.failed.get.getMessage must include("sourceData")
    }

    "return failure when email in alertDetails is empty" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val messageWithEmptyEmail = message.copy(
        alertDetails = message.alertDetails.copy(data = Map("email" -> "")),
        details = message.details.map(_.copy(sourceData = None))
      )
      val result = testUtil.isValidSecureMessage(messageWithEmptyEmail)
      result.isFailure mustBe true
      result.failed.get.getMessage must include("email")
    }

    "return failure when alertQueue is empty" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val messageWithEmptyAlertQueue = message.copy(
        alertQueue = Some(""),
        details = message.details.map(_.copy(sourceData = None))
      )
      val result = testUtil.isValidSecureMessage(messageWithEmptyAlertQueue)
      result.isFailure mustBe true
      result.failed.get.getMessage must include("alertQueue")
    }

    "return failure when GMC message has no details or empty formId" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val gmcMessageWithoutDetails = message.copy(
        externalRef = message.externalRef.copy(source = "gmc"),
        details = None
      )
      val result = testUtil.isValidSecureMessage(gmcMessageWithoutDetails)
      result.isFailure mustBe true
      result.failed.get.getMessage must include("Invalid Message")
    }

    "return failure when GMC message has empty formId" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val validBase64 = Base64.encodeBase64String("test-data".getBytes("UTF-8"))
      val gmcMessageWithEmptyFormId = message.copy(
        externalRef = message.externalRef.copy(source = "gmc"),
        details = message.details.map(_.copy(formId = "", sourceData = Some(validBase64)))
      )
      val result = testUtil.isValidSecureMessage(gmcMessageWithEmptyFormId)
      result.isFailure mustBe true
      result.failed.get.getMessage must include("details")
    }

    "return failure when issueDate is after validFrom" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val messageWithInvalidIssueDate = message.copy(
        details = message.details.map(_.copy(issueDate = Some(message.validFrom.plusDays(1)), sourceData = None))
      )
      val result = testUtil.isValidSecureMessage(messageWithInvalidIssueDate)
      result.isFailure mustBe true
      result.failed.get.getMessage must include("Issue date after the valid from date")
    }

    "return failure when email address is invalid" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val messageWithInvalidEmail = message.copy(
        recipient = message.recipient.copy(email = Some("not-an-email")),
        details = message.details.map(_.copy(sourceData = None))
      )
      val result = testUtil.isValidSecureMessage(messageWithInvalidEmail)
      result.isFailure mustBe true
      result.failed.get.getMessage must include("email")
    }

    "return failure when email is absent with invalid tax ID" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      case class InvalidTaxId(value: String) extends TaxIdentifier with SimpleName {
        override val name = "invalid-tax-id"
      }

      val messageWithInvalidTaxId = message.copy(
        recipient = TaxEntity(Regime.paye, InvalidTaxId("test-value"), None),
        details = message.details.map(_.copy(sourceData = None))
      )
      val result = testUtil.isValidSecureMessage(messageWithInvalidTaxId)
      result.isFailure mustBe true
      result.failed.get.getMessage must include("email address not provided")
    }

    "return failure when alertQueue is invalid" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val messageWithInvalidAlertQueue = message.copy(
        alertQueue = Some("INVALID_QUEUE"),
        details = message.details.map(_.copy(sourceData = None))
      )
      val result = testUtil.isValidSecureMessage(messageWithInvalidAlertQueue)
      result.isFailure mustBe true
      result.failed.get.getMessage must include("Invalid alert queue")
    }

    "return failure when content body is not valid base64" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val messageWithInvalidContent = message.copy(
        content = message.content.map(content => content.copy(body = "not-valid-base64!!")),
        details = message.details.map(_.copy(sourceData = None))
      )
      val result = testUtil.isValidSecureMessage(messageWithInvalidContent)
      result.isFailure mustBe true
      result.failed.get.getMessage must include("Invalid content")
    }
  }

  "validateAndCreateMessage" must {
    implicit val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

    "return error response when message validation fails" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val invalidMessage = message.copy(
        alertQueue = Some(""),
        details = message.details.map(_.copy(sourceData = None))
      )

      val result = testUtil.validateAndCreateMessage(invalidMessage)
      status(result) mustBe BAD_REQUEST
      contentAsString(result) must include("alertQueue")
    }

    "successfully validate and call appropriate creation path when email is empty" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val messageWithoutEmail = message.copy(
        recipient = message.recipient.copy(email = None),
        details = message.details.map(_.copy(sourceData = None))
      )
      val result = testUtil.isValidSecureMessage(messageWithoutEmail)
      result.isSuccess mustBe true
    }

    "successfully validate message when it has a valid email" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val messageWithEmail = message.copy(
        recipient = message.recipient.copy(email = Some("test@gmail.com")),
        details = message.details.map(_.copy(sourceData = None))
      )

      val result = testUtil.isValidSecureMessage(messageWithEmail)
      result.isSuccess mustBe true
    }

    "return BAD_REQUEST when message has invalid email format" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val messageWithInvalidEmail = message.copy(
        recipient = message.recipient.copy(email = Some("invalid-email")),
        details = message.details.map(_.copy(sourceData = None))
      )

      val result = testUtil.validateAndCreateMessage(messageWithInvalidEmail)
      status(result) mustBe BAD_REQUEST
      contentAsString(result) must include("email")
    }

    "return BAD_REQUEST when issueDate is after validFrom" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val messageWithInvalidIssueDate = message.copy(
        details = message.details.map(_.copy(issueDate = Some(message.validFrom.plusDays(1)), sourceData = None))
      )

      val result = testUtil.validateAndCreateMessage(messageWithInvalidIssueDate)
      status(result) mustBe BAD_REQUEST
      contentAsString(result) must include("Issue date after the valid from date")
    }

    "return BAD_REQUEST when alertQueue is invalid" in {
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
      val messageWithInvalidAlertQueue = message.copy(
        alertQueue = Some("INVALID_QUEUE"),
        details = message.details.map(_.copy(sourceData = None))
      )

      val result = testUtil.validateAndCreateMessage(messageWithInvalidAlertQueue)
      status(result) mustBe BAD_REQUEST
      contentAsString(result) must include("Invalid alert queue")
    }
  }

  "removeD2Alerts" must {
    "delete the D2 alerts for given secure-message id" in {
      val secureMessageId: ObjectId = ObjectId()
      when(extraAlertRepository.removeAlerts(meq(secureMessageId.toString))(any[ExecutionContext]))
        .thenReturn(Future.successful(DeleteResult.acknowledged(1)))

      testUtil.removeAlerts(secureMessageId, "mainTemplate").futureValue.getDeletedCount mustBe 1
    }
  }

  override def beforeEach(): Unit = {
    reset(taxpayerNameConnector)
    reset(preferencesConnector)
    reset(secureMessageRepository)
    reset(messageBrakeService)
    reset(statsMetricRepository)
    reset(auditConnector)
    reset(extraAlertRepository)
  }
}
