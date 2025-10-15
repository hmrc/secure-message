/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.securemessage.services

import org.apache.pekko.actor.ActorSystem
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{ times, verify, when }
import org.mongodb.scala.bson.ObjectId
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.Configuration
import uk.gov.hmrc.common.message.model.{ AlertDetails, Alertable, TaxEntity }
import uk.gov.hmrc.domain.{ Nino, SaUtr }
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.workitem.ProcessingStatus.{ PermanentlyFailed, Succeeded }
import uk.gov.hmrc.mongo.workitem.ResultStatus
import uk.gov.hmrc.play.audit.http.connector.{ AuditConnector, AuditResult }
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.securemessage.connectors.{ EmailConnector, EmailValidation, MobilePushNotificationsConnector, PreferencesConnector, VerifiedEmailNotFound }
import uk.gov.hmrc.securemessage.models.EmailRequest
import uk.gov.hmrc.securemessage.models.v4.MobileNotification
import uk.gov.hmrc.securemessage.repository.ExtraAlertRepository
import uk.gov.hmrc.securemessage.services.utils.MessageFixtures

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ ExecutionContext, Future }

class ExtraAlerterSpec extends PlaySpec with MockitoSugar with ScalaFutures {

  "ExtraAlerter" should {

    "successfully send alerts when email address is provided in alertParams" in new TestCase {
      given ActorSystem = ActorSystem()

      val alertable = createAlertable(
        params = Map("email" -> "test@example.com"),
        templateName = "test-template"
      )

      when(repo.pullMessageToAlert())
        .thenReturn(Future.successful(Some(alertable)))
        .thenReturn(Future.successful(None))

      when(emailConnector.send(any[EmailRequest])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Right(())))

      when(repo.alertCompleted(any[ObjectId], any[ResultStatus]))
        .thenReturn(Future.successful(true))

      when(auditConnector.sendEvent(any[DataEvent])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(AuditResult.Success))

      when(
        mobilePushNotificationsConnector.sendNotification(any[MobileNotification])(
          any[HeaderCarrier],
          any[ExecutionContext]
        )
      ).thenReturn(Future.successful(()))

      val result = extraAlerter.sendAlerts().futureValue

      result mustBe EmailResults(sent = 1, requeued = 0)
      verify(emailConnector, times(1)).send(any[EmailRequest])(any[HeaderCarrier])
      verify(repo, times(1)).alertCompleted(alertable.id, Succeeded)
      verify(auditConnector, times(1)).sendEvent(any[DataEvent])(any[HeaderCarrier], any[ExecutionContext])
    }

    "successfully send alerts when email address is retrieved from preferences" in new TestCase {
      given ActorSystem = ActorSystem()

      val alertable = createAlertable(
        params = Map.empty,
        templateName = "test-template"
      )

      when(repo.pullMessageToAlert())
        .thenReturn(Future.successful(Some(alertable)))
        .thenReturn(Future.successful(None))

      when(preferencesConnector.verifiedEmailAddress(any[TaxEntity])(any[HeaderCarrier]))
        .thenReturn(Future.successful(EmailValidation("preferences@example.com")))

      when(emailConnector.send(any[EmailRequest])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Right(())))

      when(repo.alertCompleted(any[ObjectId], any[ResultStatus]))
        .thenReturn(Future.successful(true))

      when(auditConnector.sendEvent(any[DataEvent])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(AuditResult.Success))

      when(
        mobilePushNotificationsConnector.sendNotification(any[MobileNotification])(
          any[HeaderCarrier],
          any[ExecutionContext]
        )
      ).thenReturn(Future.successful(()))

      val result = extraAlerter.sendAlerts().futureValue

      result mustBe EmailResults(sent = 1, requeued = 0)
      verify(preferencesConnector, times(1)).verifiedEmailAddress(any[TaxEntity])(any[HeaderCarrier])
      verify(emailConnector, times(1)).send(any[EmailRequest])(any[HeaderCarrier])
      verify(repo, times(1)).alertCompleted(alertable.id, Succeeded)
    }

    "mark alert as permanently failed when verified email is not found" in new TestCase {
      given ActorSystem = ActorSystem()

      val alertable = createAlertable(
        params = Map.empty,
        templateName = "test-template"
      )

      when(repo.pullMessageToAlert())
        .thenReturn(Future.successful(Some(alertable)))
        .thenReturn(Future.successful(None))

      when(preferencesConnector.verifiedEmailAddress(any[TaxEntity])(any[HeaderCarrier]))
        .thenReturn(Future.successful(VerifiedEmailNotFound("Email not found")))

      when(repo.alertCompleted(any[ObjectId], any[ResultStatus]))
        .thenReturn(Future.successful(true))

      when(auditConnector.sendEvent(any[DataEvent])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(AuditResult.Success))

      val result = extraAlerter.sendAlerts().futureValue

      result mustBe EmailResults(sent = 0, requeued = 0, permanentlyFailed = 1)
      verify(repo, times(1)).alertCompleted(alertable.id, PermanentlyFailed)
      verify(auditConnector, times(1)).sendEvent(any[DataEvent])(any[HeaderCarrier], any[ExecutionContext])
    }

    "handle exceptions and mark alert as permanently failed" in new TestCase {
      given ActorSystem = ActorSystem()

      val alertable = createAlertable(
        params = Map("email" -> "test@example.com"),
        templateName = "test-template"
      )

      when(repo.pullMessageToAlert())
        .thenReturn(Future.successful(Some(alertable)))
        .thenReturn(Future.successful(None))

      when(emailConnector.send(any[EmailRequest])(any[HeaderCarrier]))
        .thenReturn(Future.failed(new RuntimeException("Email service failure")))

      when(repo.alertCompleted(any[ObjectId], any[ResultStatus]))
        .thenReturn(Future.successful(true))

      when(auditConnector.sendEvent(any[DataEvent])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(AuditResult.Success))

      val result = extraAlerter.sendAlerts().futureValue

      result.requeued mustBe 1
      verify(repo, times(1)).alertCompleted(alertable.id, PermanentlyFailed)
      verify(auditConnector, times(1)).sendEvent(any[DataEvent])(any[HeaderCarrier], any[ExecutionContext])
    }

    "send mobile notification for NINO when template is not in invalid list" in new TestCase {
      given ActorSystem = ActorSystem()

      val nino = Nino("SK123456A")
      val alertable = createAlertable(
        taxEntity = MessageFixtures.createTaxEntity(nino),
        params = Map("email" -> "test@example.com"),
        templateName = "valid-template"
      )

      when(repo.pullMessageToAlert())
        .thenReturn(Future.successful(Some(alertable)))
        .thenReturn(Future.successful(None))

      when(emailConnector.send(any[EmailRequest])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Right(())))

      when(repo.alertCompleted(any[ObjectId], any[ResultStatus]))
        .thenReturn(Future.successful(true))

      when(auditConnector.sendEvent(any[DataEvent])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(AuditResult.Success))

      when(
        mobilePushNotificationsConnector.sendNotification(any[MobileNotification])(
          any[HeaderCarrier],
          any[ExecutionContext]
        )
      ).thenReturn(Future.successful(()))

      val result = extraAlerter.sendAlerts().futureValue

      result.sent mustBe 1
      verify(mobilePushNotificationsConnector, times(1)).sendNotification(any[MobileNotification])(
        any[HeaderCarrier],
        any[ExecutionContext]
      )
    }

    "send mobile notification for SAUTR when template is not in invalid list" in new TestCase {
      given ActorSystem = ActorSystem()

      val saUtr = SaUtr("1234567890")
      val alertable = createAlertable(
        taxEntity = MessageFixtures.createTaxEntity(saUtr),
        params = Map("email" -> "test@example.com"),
        templateName = "valid-template"
      )

      when(repo.pullMessageToAlert())
        .thenReturn(Future.successful(Some(alertable)))
        .thenReturn(Future.successful(None))

      when(emailConnector.send(any[EmailRequest])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Right(())))

      when(repo.alertCompleted(any[ObjectId], any[ResultStatus]))
        .thenReturn(Future.successful(true))

      when(auditConnector.sendEvent(any[DataEvent])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(AuditResult.Success))

      when(
        mobilePushNotificationsConnector.sendNotification(any[MobileNotification])(
          any[HeaderCarrier],
          any[ExecutionContext]
        )
      ).thenReturn(Future.successful(()))

      val result = extraAlerter.sendAlerts().futureValue

      result.sent mustBe 1
      verify(mobilePushNotificationsConnector, times(1)).sendNotification(any[MobileNotification])(
        any[HeaderCarrier],
        any[ExecutionContext]
      )
    }

    "not send mobile notification when template is in invalid list" in new TestCase {
      given ActorSystem = ActorSystem()

      val nino = Nino("SK123456A")
      val invalidTemplate = "invalid-template"
      val alertable = createAlertable(
        taxEntity = MessageFixtures.createTaxEntity(nino),
        params = Map("email" -> "test@example.com"),
        templateName = invalidTemplate
      )

      val extraAlerterWithInvalidTemplates = new ExtraAlerter(
        repo = repo,
        email = emailConnector,
        preferencesConnector = preferencesConnector,
        auditConnector = auditConnector,
        configuration = configuration,
        mobilePushNotificationsConnector = mobilePushNotificationsConnector,
        invalidTemplateIdsForPushNotifications = List(invalidTemplate)
      )

      when(repo.pullMessageToAlert())
        .thenReturn(Future.successful(Some(alertable)))
        .thenReturn(Future.successful(None))

      when(emailConnector.send(any[EmailRequest])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Right(())))

      when(repo.alertCompleted(any[ObjectId], any[ResultStatus]))
        .thenReturn(Future.successful(true))

      when(auditConnector.sendEvent(any[DataEvent])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(AuditResult.Success))

      val result = extraAlerterWithInvalidTemplates.sendAlerts().futureValue

      result.sent mustBe 1
      verify(mobilePushNotificationsConnector, times(0)).sendNotification(any[MobileNotification])(
        any[HeaderCarrier],
        any[ExecutionContext]
      )
    }

    "process multiple alerts successfully" in new TestCase {
      given ActorSystem = ActorSystem()

      val alertable1 = createAlertable(
        params = Map("email" -> "test1@example.com"),
        templateName = "template1"
      )
      val alertable2 = createAlertable(
        params = Map("email" -> "test2@example.com"),
        templateName = "template2"
      )
      val alertable3 = createAlertable(
        params = Map("email" -> "test3@example.com"),
        templateName = "template3"
      )

      when(repo.pullMessageToAlert())
        .thenReturn(Future.successful(Some(alertable1)))
        .thenReturn(Future.successful(Some(alertable2)))
        .thenReturn(Future.successful(Some(alertable3)))
        .thenReturn(Future.successful(None))

      when(emailConnector.send(any[EmailRequest])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Right(())))

      when(repo.alertCompleted(any[ObjectId], any[ResultStatus]))
        .thenReturn(Future.successful(true))

      when(auditConnector.sendEvent(any[DataEvent])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(AuditResult.Success))

      when(
        mobilePushNotificationsConnector.sendNotification(any[MobileNotification])(
          any[HeaderCarrier],
          any[ExecutionContext]
        )
      ).thenReturn(Future.successful(()))

      val result = extraAlerter.sendAlerts().futureValue

      result mustBe EmailResults(sent = 3, requeued = 0)
      verify(emailConnector, times(3)).send(any[EmailRequest])(any[HeaderCarrier])
      verify(repo, times(3)).alertCompleted(any[ObjectId], any[ResultStatus])
    }

    "return callbacks as None from sendAlertCallback" in new TestCase {
      val objectId = new ObjectId()
      extraAlerter.sendAlertCallback(objectId) mustBe None
    }

    "return callbacks as None from processEventCallback" in new TestCase {
      val objectId = new ObjectId()
      extraAlerter.processEventCallback(objectId) mustBe None
    }
  }

  trait TestCase {
    val repo: ExtraAlertRepository = mock[ExtraAlertRepository]
    val emailConnector: EmailConnector = mock[EmailConnector]
    val preferencesConnector: PreferencesConnector = mock[PreferencesConnector]
    val auditConnector: AuditConnector = mock[AuditConnector]
    val configuration: Configuration = Configuration()
    val mobilePushNotificationsConnector: MobilePushNotificationsConnector =
      mock[MobilePushNotificationsConnector]

    val extraAlerter = new ExtraAlerter(
      repo = repo,
      email = emailConnector,
      preferencesConnector = preferencesConnector,
      auditConnector = auditConnector,
      configuration = configuration,
      mobilePushNotificationsConnector = mobilePushNotificationsConnector,
      invalidTemplateIdsForPushNotifications = Nil
    )

    def createAlertable(
      taxEntity: TaxEntity = MessageFixtures.createTaxEntity(SaUtr("1234567890")),
      params: Map[String, String] = Map.empty,
      templateName: String = "default-template"
    ): Alertable = {
      val objectId = new ObjectId()
      new Alertable {
        override def id: ObjectId = objectId
        override def recipient: TaxEntity = taxEntity
        override def alertTemplateName: String = templateName
        override def alertParams: Map[String, String] = params
        override def taxPayerName: Option[uk.gov.hmrc.common.message.model.TaxpayerName] = None
        override def alertQueue: Option[String] = Some("test-queue")
        override def auditData: Map[String, String] = Map("alertId" -> objectId.toString)
        override def externalRef: Option[uk.gov.hmrc.common.message.model.ExternalRef] = None
        override def hardCopyAuditData: Map[String, String] = Map.empty
        override def source: Option[String] = None
        override def statutory: Boolean = false
        override def validFrom: java.time.LocalDate = java.time.LocalDate.now()
      }
    }
  }
}
