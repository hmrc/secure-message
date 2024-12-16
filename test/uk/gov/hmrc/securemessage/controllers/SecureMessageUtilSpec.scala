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

import org.apache.commons.codec.binary.Base64
import org.mockito.ArgumentMatchers.{ any, eq => meq }
import org.mockito.Mockito.{ reset, verifyNoInteractions, when }
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.Configuration
import uk.gov.hmrc.common.message.model.TaxpayerName
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.securemessage.connectors.{ EntityResolverConnector, TaxpayerNameConnector }
import uk.gov.hmrc.securemessage.helpers.Resources
import uk.gov.hmrc.securemessage.models.v4.SecureMessage
import uk.gov.hmrc.securemessage.repository.{ ExtraAlertRepository, SecureMessageRepository, StatsMetricRepository }
import uk.gov.hmrc.securemessage.services.MessageBrakeService

import scala.concurrent.{ ExecutionContext, Future }

class SecureMessageUtilSpec extends PlaySpec with ScalaFutures with MockitoSugar with BeforeAndAfterEach {
  val appName: String = "Test"
  val entityResolverConnector: EntityResolverConnector = mock[EntityResolverConnector]
  val taxpayerNameConnector: TaxpayerNameConnector = mock[TaxpayerNameConnector]
  val secureMessageRepository: SecureMessageRepository = mock[SecureMessageRepository]
  val extraAlertRepository: ExtraAlertRepository = mock[ExtraAlertRepository]
  val statsMetricRepository: StatsMetricRepository = mock[StatsMetricRepository]
  val messageBrakeService: MessageBrakeService = mock[MessageBrakeService]
  val auditConnector: AuditConnector = mock[AuditConnector]
  val configuration: Configuration = Configuration.from(
    Map(
      "alertProfile"               -> List(),
      "deprecate.message-renderer" -> true
    )
  )
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  implicit val hc: HeaderCarrier = HeaderCarrier()
  val testUtil = new SecureMessageUtil(
    appName,
    entityResolverConnector,
    taxpayerNameConnector,
    secureMessageRepository,
    extraAlertRepository,
    statsMetricRepository,
    messageBrakeService,
    auditConnector,
    configuration
  )

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

  "deprecateRendererService" must {
    "return the value from config" in {
      testUtil.deprecateRendererService mustBe true
    }
  }

  override def beforeEach(): Unit =
    reset(taxpayerNameConnector)
}
