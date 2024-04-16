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

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{ times, verify, when }
import org.mongodb.scala.bson.ObjectId
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.common.message.model.{ EmailAlert, TaxEntity }
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.workitem.ProcessingStatus
import uk.gov.hmrc.play.audit.http.connector.{ AuditConnector, AuditResult }
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.securemessage.connectors.{ EmailConnector, EntityResolverConnector, MobilePushNotificationsConnector }
import uk.gov.hmrc.securemessage.models.{ EmailRequest, TaxId }
import uk.gov.hmrc.securemessage.models.v4.MobileNotification
import uk.gov.hmrc.securemessage.repository.SecureMessageRepository
import uk.gov.hmrc.securemessage.services.utils.SecureMessageFixtures.messageForSA

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ ExecutionContext, Future }

class EmailAlertServiceSpec extends PlaySpec with MockitoSugar with ScalaFutures {

  "Send email alerts" should {

    "run successfully" in new TestCase {
      implicit private val actorSystem = ActorSystem(Behaviors.empty, "test-actor-system")

      when(secureMessageRepository.pullMessageToAlert())
        .thenReturn(Future.successful(Some(messageForSA(utr = "3254567990"))))
        .thenReturn(Future.successful(Some(messageForSA(utr = "1264567981"))))
        .thenReturn(Future.successful(Some(messageForSA(utr = "7764560597"))))
        .thenReturn(Future.successful(None))

      when(entityResolverConnector.getTaxId(any[TaxEntity])(any[HeaderCarrier])).thenReturn(Future.successful(None))

      private val result = emailAlertService.sendEmailAlerts().futureValue

      // noinspection RedundantDefaultArgument
      result mustBe EmailResults(sent = 3, requeued = 0)

      verify(emailConnector, times(3)).send(any[EmailRequest])(any[HeaderCarrier])
      verify(auditConnector, times(3)).sendEvent(any[DataEvent])(any[HeaderCarrier], any[ExecutionContext])
      verify(secureMessageRepository, times(3)).alertCompleted(any[ObjectId], any[ProcessingStatus], any[EmailAlert])
    }

    "email request should include tax identifier" in new TestCase {
      val message = messageForSA(utr = "3254567990")

      val emailRequest =
        emailAlertService.createEmailRequest(message, Some(TaxId("", Some("3254567990"), Some("SK12345678A"))))

      emailRequest.parameters.get("sautr") mustBe Some("3254567990")
      emailRequest.parameters.get("nino") mustBe Some("SK12345678A")
    }

    "email request should include tax identifier along with Nino and SaUtr" in new TestCase {
      val message = messageForSA(utr = "3254567990")

      val emailRequest = emailAlertService.createEmailRequest(message)

      emailRequest.parameters.get("sautr") mustBe Some("3254567990")
    }

  }

  trait TestCase {
    val secureMessageRepository: SecureMessageRepository = mock[SecureMessageRepository]
    val emailConnector: EmailConnector = mock[EmailConnector]
    val entityResolverConnector: EntityResolverConnector = mock[EntityResolverConnector]
    val auditConnector: AuditConnector = mock[AuditConnector]
    val servicesConfig: ServicesConfig = mock[ServicesConfig]
    val mobilePushNotificationsConnector: MobilePushNotificationsConnector = mock[MobilePushNotificationsConnector]

    when(emailConnector.send(any[EmailRequest])(any[HeaderCarrier]))
      .thenReturn(Future.successful(Right(())))
    when(auditConnector.sendEvent(any[DataEvent])(any[HeaderCarrier], any[ExecutionContext]))
      .thenReturn(Future.successful(AuditResult.Success))
    when(secureMessageRepository.alertCompleted(any[ObjectId], any[ProcessingStatus], any[EmailAlert]))
      .thenReturn(Future.successful(true))
    when(
      mobilePushNotificationsConnector
        .sendNotification(any[MobileNotification])(any[HeaderCarrier], any[ExecutionContext])
    )
      .thenReturn(Future.successful(()))

    val emailAlertService = new EmailAlertService(
      secureMessageRepository,
      emailConnector,
      entityResolverConnector,
      auditConnector,
      servicesConfig,
      mobilePushNotificationsConnector,
      invalidTemplateIdsForPushNotifications = Nil
    )
  }

}
