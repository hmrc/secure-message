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

import akka.Done
import com.google.inject.AbstractModule
import net.codingwell.scalaguice.ScalaModule
import net.sf.ehcache.Element
import org.mockito.ArgumentMatchers.{ eq => eqTo }
import org.mockito.Mockito._
import org.scalatest.concurrent.{ IntegrationPatience, ScalaFutures }
import org.scalatest.{ BeforeAndAfterEach, LoneElement }
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.cache.AsyncCacheApi
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.metrix.MetricOrchestrator
import uk.gov.hmrc.securemessage.models.v4.Allowlist
import uk.gov.hmrc.securemessage.repository.AllowlistRepository
import uk.gov.hmrc.securemessage.services.utils.MetricOrchestratorStub

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.reflect.ClassTag

class MessageBrakeServiceSpec
    extends PlaySpec with BeforeAndAfterEach with GuiceOneAppPerSuite with ScalaFutures with IntegrationPatience
    with MetricOrchestratorStub with LoneElement with MockitoSugar {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val mockAllowlistRepository: AllowlistRepository = mock[AllowlistRepository]
  val cache: AsyncCacheApi = new InMemoryCache()

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .overrides(
        bind[MetricOrchestrator].toInstance(mockMetricOrchestrator).eagerly(),
        bind[AsyncCacheApi].toInstance(cache)
      )
      .overrides(new AbstractModule() with ScalaModule {
        override def configure(): Unit =
          bind[AllowlistRepository].toInstance(mockAllowlistRepository)
      })
      .configure(
        "metrics.enabled" -> "false"
      )
      .build()

  val defaultAllowlist: List[String] = MessageBrakeAllowList.default
  val service: MessageBrakeService = app.injector.instanceOf[MessageBrakeService]

  "The MessageBrakeService" must {

    "getOrInitialiseCachedAllowlist()" must {

      "initialise a new allowlist when collection or document is missing" in new TestCase {
        when(mockAllowlistRepository.retrieve())
          .thenReturn(Future.successful(None))
        when(mockAllowlistRepository.store(eqTo(defaultAllowlist)))
          .thenReturn(Future.successful(Some(Allowlist(defaultAllowlist))))

        val result = service.getOrInitialiseCachedAllowlist().futureValue
        result.get.formIdList mustBe defaultAllowlist
        cache.get[Allowlist]("brake-gmc-allowlist").futureValue.get mustBe Some(Allowlist(defaultAllowlist))
      }

      "not initialise the collection's document if one is already present with existing formIds in the list" in new TestCase {
        when(mockAllowlistRepository.retrieve())
          .thenReturn(Future.successful(Some(Allowlist(List("TEST1", "TEST2")))))

        val result = service.getOrInitialiseCachedAllowlist().futureValue
        result.get.formIdList mustBe List("TEST1", "TEST2")

        cache.get[Allowlist]("brake-gmc-allowlist").futureValue.get mustBe Some(Allowlist(List("TEST1", "TEST2")))
      }

      "fetch the allowlist from the cache instead of the database if the cache currently holds a allow list" in new TestCase {
        cache
          .set("brake-gmc-allowlist", Some(Allowlist(List("TEST8", "TEST9"))), 1.minute)
          .futureValue

        when(mockAllowlistRepository.retrieve())
          .thenReturn(Future.successful(Some(Allowlist(List("TEST8", "TEST9")))))

        val result = service.getOrInitialiseCachedAllowlist().futureValue.get.formIdList
        result mustBe List("TEST8", "TEST9")
        cache.get[Allowlist]("brake-gmc-allowlist").futureValue.get mustBe Some(Allowlist(List("TEST8", "TEST9")))
      }
    }

    "allowlistContains" must {

      "return true if formId is in the lists" in new TestCase {
        when(mockAllowlistRepository.retrieve())
          .thenReturn(Future.successful(Some(Allowlist(List("TEST8")))))

        val result = service.allowlistContains("TEST8").futureValue

        result mustBe true
      }

      "return false if formId is not in the lists" in new TestCase {
        when(mockAllowlistRepository.retrieve())
          .thenReturn(Future.successful(Some(Allowlist(List("TEST8", "TEST9")))))

        val result = service.allowlistContains("TEST10").futureValue

        result mustBe false
      }

      "match the welsh(_cy) formId with the corresponding one in the lists" in new TestCase {
        when(mockAllowlistRepository.retrieve())
          .thenReturn(Future.successful(Some(Allowlist(List("TEST8", "TEST9")))))

        val result = service.allowlistContains("TEST8_CY").futureValue

        result mustBe true
      }
    }

    "addFormIdToAllowlist" must {

      "add a form id to a allowlist must update the cache and the database with an uppercased version" in new TestCase {

        when(mockAllowlistRepository.retrieve())
          .thenReturn(Future.successful(Some(Allowlist(List("TEST10", "TEST11")))))
        when(mockAllowlistRepository.store(eqTo(List("TEST10", "TEST11", "TEST12"))))
          .thenReturn(Future.successful(Some(Allowlist(List("TEST10", "TEST11", "TEST12")))))

        val allowlistUpdateRequest = AllowlistUpdateRequest("teSt12", "a reason to add this form id")
        val result = service.addFormIdToAllowlist(allowlistUpdateRequest).futureValue
        result.get.formIdList mustBe List("TEST10", "TEST11", "TEST12")

        cache.get[Allowlist]("brake-gmc-allowlist").futureValue.get mustBe Some(
          Allowlist(List("TEST10", "TEST11", "TEST12")))
      }

      "add a form id to a non-existing collection must update the cache and the database with an uppercased default version" in new TestCase {

        val newAllowlist = defaultAllowlist.concat(List("TEST12"))

        when(mockAllowlistRepository.retrieve()).thenReturn(Future.successful(None))
        when(mockAllowlistRepository.store(eqTo(newAllowlist)))
          .thenReturn(Future.successful(Some(Allowlist(newAllowlist))))

        val allowlistUpdateRequest = AllowlistUpdateRequest("teSt12", "a reason to add this form id")
        val result = service.addFormIdToAllowlist(allowlistUpdateRequest).futureValue
        result.get.formIdList mustBe newAllowlist

        cache.get[Allowlist]("brake-gmc-allowlist").futureValue.get mustBe Some(Allowlist(newAllowlist))
      }
    }

    "deleteFormIdFromAllowlist" must {

      "remove a form id from a allowlist must update the cache and the database with an uppercased version" in new TestCase {

        when(mockAllowlistRepository.retrieve())
          .thenReturn(Future.successful(Some(Allowlist(List("TEST10", "TEST11")))))
        when(mockAllowlistRepository.store(eqTo(List("TEST10", "TEST11", "TEST12"))))
          .thenReturn(Future.successful(Some(Allowlist(List("TEST10", "TEST11", "TEST12")))))

        val allowlistUpdateRequest = AllowlistUpdateRequest("teSt12", "a reason to add this form id")
        val result = service.addFormIdToAllowlist(allowlistUpdateRequest).futureValue
        result.get.formIdList mustBe List("TEST10", "TEST11", "TEST12")

        cache.get[Allowlist]("brake-gmc-allowlist").futureValue.get mustBe Some(
          Allowlist(List("TEST10", "TEST11", "TEST12")))
      }

      "remove a form id from a non-existing collection must update the cache and the database with the default version" in new TestCase {

        val allowlistWithoutSA359 = defaultAllowlist.filterNot(_ == "SA359")

        when(mockAllowlistRepository.retrieve()).thenReturn(Future.successful(None))
        when(mockAllowlistRepository.store(eqTo(allowlistWithoutSA359)))
          .thenReturn(Future.successful(Some(Allowlist(allowlistWithoutSA359))))

        val allowlistUpdateRequest = AllowlistUpdateRequest("sa359", "a reason to add this form id")
        val result = service.deleteFormIdFromAllowlist(allowlistUpdateRequest).futureValue
        result.get.formIdList mustBe allowlistWithoutSA359

        cache.get[Allowlist]("brake-gmc-allowlist").futureValue.get mustBe Some(Allowlist(allowlistWithoutSA359))
      }
    }
  }

  trait TestCase {
    reset(mockAllowlistRepository)
    cache.removeAll()
  }

  class InMemoryCache() extends AsyncCacheApi {

    val cache = scala.collection.mutable.Map[String, Element]()

    def remove(key: String): Future[Done] = Future {
      cache -= key
      Done
    }

    def getOrElseUpdate[A: ClassTag](key: String, expiration: Duration)(orElse: => Future[A]): Future[A] =
      get[A](key).flatMap {
        case Some(value) => Future.successful(value)
        case None        => orElse.flatMap(value => set(key, value, expiration).map(_ => value))
      }

    def set(key: String, value: Any, expiration: Duration): Future[Done] = Future {
      val element = new Element(key, value)

      if (expiration.isFinite) {
        element.setTimeToLive(expiration.toSeconds.toInt)
      } else {
        element.setEternal(true)
      }

      cache.put(key, element)
      Done
    }

    def get[T: ClassTag](key: String): Future[Option[T]] = Future {
      cache.get(key).map(_.getObjectValue).asInstanceOf[Option[T]]
    }

    def removeAll(): Future[Done] = Future {
      cache.clear()
      Done
    }

  }
}
