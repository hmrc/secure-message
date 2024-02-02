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

package uk.gov.hmrc.securemessage.repository

import org.mongodb.scala.model.Filters
import org.scalatest.LoneElement
import org.scalatest.concurrent.{ IntegrationPatience, ScalaFutures }
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.common.message.model.AlertDetails
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.metrix.MetricOrchestrator
import uk.gov.hmrc.mongo.workitem.ProcessingStatus.Succeeded
import uk.gov.hmrc.securemessage.services.utils.{ MessageFixtures, MetricOrchestratorStub }

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global

class ExtraAlertRepositorySpec
    extends PlaySpec with GuiceOneAppPerSuite with ScalaFutures with IntegrationPatience with MetricOrchestratorStub
    with LoneElement {

  implicit val hc = HeaderCarrier()

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .overrides(
        bind[MetricOrchestrator].toInstance(mockMetricOrchestrator).eagerly()
      )
      .configure(
        "metrics.enabled" -> "false"
      )
      .build()

  "The ExtraAlertRepository" must {
    "allow an alert to be pushed, pulled and marked as done." in new TestCase {
      repo.collection.deleteMany(Filters.empty()).toFuture().futureValue
      repo.ensureIndexes().futureValue

      repo.pushNew(item = alert).futureValue

      val alertable = repo.pullMessageToAlert().futureValue.get

      repo.alertCompleted(alertable.id, Succeeded).futureValue must be(true)
      alertable.alertTemplateName must be(alert.emailTemplateId)

      repo.collection.find(Filters.empty()).toFuture().futureValue.loneElement.status must be(Succeeded)
    }

    "add a duplicate alert to ensure it is not added" in new TestCase {
      repo.collection.deleteMany(Filters.empty()).toFuture().futureValue
      repo.ensureIndexes().futureValue

      repo.pushNew(item = alert).futureValue

      //repo.pushNew(item = alert).failed.futureValue
    }

    "allow pushed alert, to be deleted." in new TestCase {

      repo.collection.deleteMany(Filters.empty()).toFuture().futureValue
      repo.ensureIndexes().futureValue

      repo.pushNew(item = alert).futureValue
      repo
        .pushNew(
          item = alert.copy(reference = "foo", extraReference = Some("template1")),
          Instant.now().plusMillis(60000))
        .futureValue
      repo
        .pushNew(
          item = alert.copy(reference = "foo", extraReference = Some("template2")),
          Instant.now().plusMillis(120000))
        .futureValue

      val alertable = repo.pullMessageToAlert().futureValue.get

      repo.alertCompleted(alertable.id, Succeeded).futureValue must be(true)
      repo.removeAlerts("foo").futureValue
      alertable.alertTemplateName must be(alert.emailTemplateId)

      repo.collection.find(Filters.empty()).toFuture().futureValue.loneElement.status must be(Succeeded)
    }

  }

  "The ExtraAlertRepository " must {
    "pull newMessageAlert_P800_D2" in new TestCase {
      repo.collection.deleteMany(Filters.empty()).toFuture().futureValue
      repo.ensureIndexes().futureValue

      val alert1 = ExtraAlert.build(
        MessageFixtures.createTaxEntity(SaUtr("10000001")),
        "ref",
        "newMessageAlert_P800_D2",
        AlertDetails("newMessageAlert_P800_D2", None, Map())
      )

      repo.pushNew(item = alert1, Instant.now()).futureValue

      repo.pullMessageToAlert().futureValue.get.alertTemplateName must be("newMessageAlert_P800_D2")
    }

    "pull newMessageAlert_PA302_D2" in new TestCase {
      repo.collection.deleteMany(Filters.empty()).toFuture().futureValue
      repo.ensureIndexes().futureValue

      val alert1 = ExtraAlert.build(
        MessageFixtures.createTaxEntity(SaUtr("10000001")),
        "ref",
        "newMessageAlert_PA302_D2",
        AlertDetails("newMessageAlert_PA302_D2", None, Map())
      )

      repo.pushNew(item = alert1, Instant.now()).futureValue

      repo.pullMessageToAlert().futureValue.get.alertTemplateName must be("newMessageAlert_PA302_D2")
    }
  }

  trait TestCase {
    val repo = app.injector.instanceOf[ExtraAlertRepository]

    val alert: ExtraAlert = ExtraAlert.build(
      MessageFixtures.createTaxEntity(SaUtr("10000001")),
      "ref",
      "emailTemplateId",
      AlertDetails("template-id", None, Map())
    )
  }
}
