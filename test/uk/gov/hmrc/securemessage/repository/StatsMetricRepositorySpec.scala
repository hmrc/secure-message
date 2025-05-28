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
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{ IntegrationPatience, ScalaFutures }
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject._
import play.api.Application
import org.mongodb.scala.SingleObservableFuture
import org.mongodb.scala.ObservableFuture
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.metrix.MetricOrchestrator
import uk.gov.hmrc.securemessage.services.utils.MetricOrchestratorStub

import scala.concurrent.ExecutionContext.Implicits.global

class StatsMetricRepositorySpec
    extends PlaySpec with ScalaFutures with BeforeAndAfterEach with IntegrationPatience with GuiceOneAppPerSuite
    with MetricOrchestratorStub {
  self =>

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .overrides(
        bind[MetricOrchestrator].toInstance(mockMetricOrchestrator).eagerly()
      )
      .configure(
        "metrics.enabled" -> "false"
      )
      .build()

  val mongoComponent = app.injector.instanceOf[MongoComponent]
  lazy val repo = new StatsMetricRepository(mongoComponent)

  override def beforeEach(): Unit = {
    super.beforeEach()
    repo.collection.deleteMany(Filters.empty()).toFuture().futureValue
    ()
  }

  "Stats Metric repository" must {
    "increment the count and total for read" in {
      repo.incrementReads("sautr", "form1").futureValue
      repo.incrementReads("sautr", "form2").futureValue
      repo.incrementReads("sautr", "form1").futureValue

      val value1 = repo.collection.find().toFuture().futureValue
      value1 must contain.only(
        StatsCount("stats.sautr.form1.read", 2, 2),
        StatsCount("stats.sautr.form2.read", 1, 1)
      )
    }

    "increment the count and total for created" in {
      repo.incrementCreated("sautr", "form1").futureValue
      repo.incrementCreated("sautr", "form2").futureValue
      repo.incrementCreated("sautr", "form1").futureValue

      val value1 = repo.collection.find().toFuture().futureValue
      value1 must contain.only(
        StatsCount("stats.sautr.form1.created", 2, 2),
        StatsCount("stats.sautr.form2.created", 1, 1)
      )
    }

    "reset the count to 0 and retain the total value" in {
      repo.incrementReads("sautr", "form1").futureValue
      repo.incrementReads("sautr", "form2").futureValue
      repo.incrementReads("sautr", "form1").futureValue

      repo.reset().futureValue

      repo.collection.find().toFuture().futureValue must contain.only(
        StatsCount("stats.sautr.form1.read", 2, 0),
        StatsCount("stats.sautr.form2.read", 1, 0)
      )
    }

    "return metrics for form count and total" in {
      repo.incrementReads("sautr", "form1").futureValue
      repo.incrementReads("sautr", "form2").futureValue
      repo.incrementReads("sautr", "form1").futureValue
      repo.incrementCreated("sautr", "form1").futureValue
      repo.incrementCreated("sautr", "form2").futureValue
      repo.incrementCreated("sautr", "form1").futureValue

      val metrics = repo.metrics.futureValue

      metrics("stats.sautr.form1.read.count") mustBe 2
      metrics("stats.sautr.form1.read.total") mustBe 2
      metrics("stats.sautr.form2.read.count") mustBe 1
      metrics("stats.sautr.form2.read.total") mustBe 1
      metrics("stats.sautr.form1.created.count") mustBe 2
      metrics("stats.sautr.form1.created.total") mustBe 2
      metrics("stats.sautr.form2.created.count") mustBe 1
      metrics("stats.sautr.form2.created.total") mustBe 1
    }
  }
}
