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

package uk.gov.hmrc.securemessage.connectors

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.concurrent.{ IntegrationPatience, ScalaFutures }
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{ JsObject, JsString }
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.http.{ HeaderCarrier, HttpClient, HttpResponse }
import uk.gov.hmrc.mongo.metrix.MetricOrchestrator
import uk.gov.hmrc.securemessage.services.utils.{ GenerateRandom, MessageFixtures, MetricOrchestratorStub }

import scala.concurrent.{ ExecutionContext, Future }

class EntityResolverConnectorSpec
    extends PlaySpec with ScalaFutures with MockitoSugar with MetricOrchestratorStub with IntegrationPatience {

  implicit val hc: HeaderCarrier = HeaderCarrier()
  lazy implicit val ec: ExecutionContext = mock[ExecutionContext]

  lazy val mockHttp: HttpClient = mock[HttpClient]

  private val injector = new GuiceApplicationBuilder()
    .overrides(bind[MetricOrchestrator].to(mockMetricOrchestrator))
    .overrides(bind[HttpClient].to(mockHttp))
    .configure("metrics.enabled" -> false)
    .injector()

  lazy val connector: EntityResolverConnector =
    injector.instanceOf[EntityResolverConnector]

  "verifiedEmailAddress in connector" must {
    "return a valid email when a preference is found for sautr" in {
      when(mockHttp.doGet(any[String], any[Seq[(String, String)]])(any[ExecutionContext]))
        .thenReturn(
          Future.successful(HttpResponse(200, "{\"email\" :  \"an@email.com\"}", Map.empty[String, Seq[String]])))

      connector
        .verifiedEmailAddress(MessageFixtures.createTaxEntity(SaUtr("someUtr")))
        .futureValue mustBe EmailValidation("an@email.com")
    }

    "return a valid email when a preference is found for nino" in {
      val nino = GenerateRandom.nino()
      when(mockHttp.doGet(any[String], any[Seq[(String, String)]])(any[ExecutionContext]))
        .thenReturn(Future.successful(
          HttpResponse(200, JsObject(Seq("email" -> JsString("an@email.com"))), Map.empty[String, Seq[String]])))
      connector.verifiedEmailAddress(MessageFixtures.createTaxEntity(nino)).futureValue mustBe EmailValidation(
        "an@email.com"
      )
    }

    "return VerifiedEmailNotFoundException when a preference is not found" in {
      when(mockHttp.doGet(any[String], any[Seq[(String, String)]])(any[ExecutionContext]))
        .thenReturn(Future.successful(
          HttpResponse(404, JsObject(Seq("reason" -> JsString("not found"))), Map.empty[String, Seq[String]])))

      connector
        .verifiedEmailAddress(MessageFixtures.createTaxEntity(SaUtr("someUtr")))
        .futureValue mustBe VerifiedEmailNotFound("not found")
    }

    "return a OtherException when status is 5xx" in {
      when(mockHttp.doGet(any[String], any[Seq[(String, String)]])(any[ExecutionContext]))
        .thenReturn(Future.successful(HttpResponse(504, "", Map.empty[String, Seq[String]])))

      val e = connector.verifiedEmailAddress(MessageFixtures.createTaxEntity(SaUtr("someUtr"))).failed.futureValue
      e mustBe an[OtherException]
      e.getMessage mustBe "OTHER_EXCEPTION_504"
    }

    "return a OtherException when status is 4xx and is not 404" in {
      when(mockHttp.doGet(any[String], any[Seq[(String, String)]])(any[ExecutionContext]))
        .thenReturn(Future.successful(HttpResponse(403, "", Map.empty[String, Seq[String]])))

      val e = connector.verifiedEmailAddress(MessageFixtures.createTaxEntity(SaUtr("someUtr"))).failed.futureValue
      e mustBe an[OtherException]
      e.getMessage mustBe "OTHER_EXCEPTION_403"
    }
  }

  "verifiedEmailNotFoundException getMessage" must {

    "return 'The backend has rejected the message due to not being able to verify the email address.' when reason code is 'EMAIL_ADDRESS_NOT_VERIFIED'" in {
      val result = VerifiedEmailNotFound("EMAIL_ADDRESS_NOT_VERIFIED").getMessage
      result mustBe "The backend has rejected the message due to not being able to verify the email address."
    }

    "return 'email not verified as user not opted in' when reason code is 'NOT_OPTED_IN'" in {
      val result = VerifiedEmailNotFound("NOT_OPTED_IN").getMessage
      result mustBe "email: not verified as user not opted in"
    }

    "return 'email not verified as preferences not found' when reason code is 'PREFERENCES_NOT_FOUND'" in {
      val result = VerifiedEmailNotFound("PREFERENCES_NOT_FOUND").getMessage
      result mustBe "email: not verified as preferences not found"
    }

    "return 'email not verified for unknown reason' otherwise" in {
      val result = VerifiedEmailNotFound("XXX").getMessage
      result mustBe "email: not verified for unknown reason"
    }
  }

  "VerifiedEmailAddressResponse value" must {
    "return Left for VerifiedEmailNotFound" in {
      val result = VerifiedEmailNotFound("EMAIL_ADDRESS_NOT_VERIFIED").value

      result.left.get mustBe VerifiedEmailNotFound("EMAIL_ADDRESS_NOT_VERIFIED")
    }
  }
}
