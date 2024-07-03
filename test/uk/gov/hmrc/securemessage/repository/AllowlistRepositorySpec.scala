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

import org.mongodb.scala.model.ReturnDocument.AFTER
import org.mongodb.scala.model.{ Filters, Updates }
import org.mongodb.scala.SingleObservableFuture
import org.mongodb.scala.ObservableFuture
import org.scalatest.concurrent.{ IntegrationPatience, ScalaFutures }
import org.scalatest.{ BeforeAndAfterEach, LoneElement }
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.metrix.MetricOrchestrator
import uk.gov.hmrc.securemessage.models.v4.Allowlist
import uk.gov.hmrc.securemessage.services.utils.MetricOrchestratorStub

class AllowlistRepositorySpec
    extends PlaySpec with BeforeAndAfterEach with GuiceOneAppPerSuite with ScalaFutures with IntegrationPatience
    with LoneElement with MetricOrchestratorStub {

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .overrides(
        bind[MetricOrchestrator].toInstance(mockMetricOrchestrator).eagerly()
      )
      .configure(
        "metrics.enabled" -> "false"
      )
      .build()

  "The AllowlistRepository" must {

    "storeAllowlist()" must {

      "persists a allowlist to the database" in new TestCase {
        repo.collection.deleteMany(Filters.empty()).toFuture().futureValue
        repo.ensureIndexes().futureValue

        val result = repo.store(List("TEST3", "TEST4")).futureValue
        result.get.formIdList mustBe List("TEST3", "TEST4")

        val dbAllowlist = repo.collection.find(Filters.empty()).toFuture().futureValue

        dbAllowlist.flatMap(_.formIdList) mustBe List("TEST3", "TEST4")
        repo.collection.deleteMany(Filters.empty()).toFuture().futureValue
      }
    }

    "retrieveAllowlist()" must {

      "return all formIds from the allowlist" in new TestCase {
        repo.collection.deleteMany(Filters.empty()).toFuture().futureValue
        repo.ensureIndexes().futureValue

        repo.store(List("TEST3", "TEST4")).futureValue

        repo.collection
          .findOneAndUpdate(
            Filters.empty(),
            Updates.set("formIdList", List("TEST3", "TEST4")),
            org.mongodb.scala.model.FindOneAndUpdateOptions().returnDocument(AFTER)
          )
          .toFuture()
          .futureValue

        val result: Option[Allowlist] = repo.retrieve().futureValue

        result.get.formIdList mustBe List("TEST3", "TEST4")
        repo.collection.deleteMany(Filters.empty()).toFuture().futureValue
      }
    }
  }

  trait TestCase {
    val repo: AllowlistRepository = app.injector.instanceOf[AllowlistRepository]
  }
}
