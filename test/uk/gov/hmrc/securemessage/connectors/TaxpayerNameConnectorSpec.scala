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

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.{ Level, Logger => LogbackLogger }
import ch.qos.logback.core.read.ListAppender
import com.google.inject.AbstractModule
import net.codingwell.scalaguice.ScalaModule
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.concurrent.{ Eventually, IntegrationPatience, ScalaFutures }
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import org.slf4j.LoggerFactory
import play.api.{ Application, Logger }
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json._
import uk.gov.hmrc.common.message.model.TaxpayerName
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.http.{ HeaderCarrier, HttpClient, HttpReads, NotFoundException }
import uk.gov.hmrc.mongo.metrix.MetricOrchestrator
import uk.gov.hmrc.securemessage.services.utils.MetricOrchestratorStub

import scala.concurrent.{ ExecutionContext, Future }

class TaxpayerNameConnectorSpec
    extends PlaySpec with ScalaFutures with LogCapturing with Eventually with MockitoSugar with GuiceOneAppPerSuite
    with MetricOrchestratorStub with IntegrationPatience {

  val fullTaxpayerName = TaxpayerName(
    title = Some("Mr"),
    forename = Some("Erbert"),
    secondForename = Some("Donaldson"),
    surname = Some("Ducking"),
    honours = Some("KCBE")
  )

  val utr = SaUtr("12345678990")

  "Parsing from JSON to a" must {

    implicit val headerCarrier: HeaderCarrier = new HeaderCarrier()
    "work for more complete Taxpayer data JSON" in {
      val json = Some(Json.parse("""{
                                   |    "name" : {
                                   |        "title": "Mr",
                                   |        "forename": "Erbert",
                                   |        "secondForename": "Donaldson",
                                   |        "surname": "Ducking",
                                   |        "honours": "KCBE"
                                   |    },
                                   |    "address": {
                                   |        "addressLine1": "42 Somewhere's Street",
                                   |        "addressLine2": "London",
                                   |        "addressLine3": "Greater London",
                                   |        "addressLine4": "",
                                   |        "addressLine5": "",
                                   |        "postcode": "WO9H 8AA",
                                   |        "foreignCountry": null,
                                   |        "returnedLetter": true,
                                   |        "additionalDeliveryInformation": "Leave by door"
                                   |    },
                                   |    "contact": {
                                   |        "telephone": {
                                   |            "daytime": "02654321#1235",
                                   |            "evening": "027123456",
                                   |            "mobile": "07676767",
                                   |            "fax": "0209798969"
                                   |        },
                                   |        "email": {
                                   |            "primary": "erbert@notthere.co.uk"
                                   |        },
                                   |        "other": {}
                                   |    }
                                   |}
                                   | """.stripMargin))

      val result = connectorWithResponse(json).taxpayerName(utr)

      result.futureValue must be(Some(fullTaxpayerName))
    }

    "work for Taxpayer JSON which holds no name field" in {
      val json = Some(Json.parse("""{
                                   |    "address": {
                                   |        "addressLine1": "42 Somewhere's Street",
                                   |        "addressLine2": "London",
                                   |        "addressLine3": "Greater London",
                                   |        "postcode": "WO9H 8AA",
                                   |        "foreignCountry": null,
                                   |        "returnedLetter": true,
                                   |        "additionalDeliveryInformation": "Leave by door"
                                   |    },
                                   |    "contact": {
                                   |        "telephone": {
                                   |            "mobile": "07676767"
                                   |        },
                                   |        "email": {
                                   |            "primary": "erbert@notthere.co.uk"
                                   |        },
                                   |        "other": {}
                                   |    }
                                   |}
                                   | """.stripMargin))

      val result = connectorWithResponse(json).taxpayerName(utr)

      result.futureValue must be(None)
    }

    "work for empty Taxpayer JSON" in {
      val result = connectorWithResponse(Some(Json.parse("{}"))).taxpayerName(utr)
      result.futureValue must be(None)
    }

    "work for JSON which only holds the name data" in {
      val json = Some(Json.parse("""{
                                   |"name" : {
                                   |        "title": "Mr",
                                   |        "forename": "Erbert",
                                   |        "secondForename": "Donaldson",
                                   |        "surname": "Ducking",
                                   |        "honours": "KCBE"
                                   |    }
                                   |}""".stripMargin))

      val result = connectorWithResponse(json).taxpayerName(utr)

      result.futureValue must be(Some(fullTaxpayerName))
    }
  }

  "Taxpayer connector" must {
    implicit val hc = HeaderCarrier()
    "log an error and return empty TaxpayerName on 5** or non 404 4** error" in {
      val logger = play.api.Logger(connector.getClass()).underlyingLogger.asInstanceOf[LogbackLogger]
      withCaptureOfLoggingFrom(logger) { logEvents =>
        connectorWithResponse(None, 500).taxpayerName(utr).futureValue must be(None)

        connectorWithResponse(None, 501).taxpayerName(utr).futureValue must be(None)

        connectorWithResponse(None, 401).taxpayerName(utr).futureValue must be(None)

        logEvents.count(_.getLevel == Level.ERROR) must be(3)
      }
    }

    "log an warn level message and return empty TaxpayerName on 404 error" in {
      val logger = play.api.Logger(connector.getClass()).underlyingLogger.asInstanceOf[LogbackLogger]
      withCaptureOfLoggingFrom(logger) { logEvents =>
        connectorWithResponse(None, 404).taxpayerName(utr).futureValue must be(None)
        logEvents.head.getLevel must be(Level.WARN)
        logEvents.head.getMessage must include(utr.value)
      }
    }
  }

  val mockHttp = mock[HttpClient]

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .overrides(
        bind[MetricOrchestrator].toInstance(mockMetricOrchestrator).eagerly(),
//        bind[HttpClient].toInstance(mockHttp)
      )
      .overrides(new AbstractModule with ScalaModule {
        override def configure(): Unit =
          bind[HttpClient].toInstance(mockHttp)
      })
      .configure(
        "metrics.enabled" -> "false"
      )
      .build()

  val connector = app.injector.instanceOf[TaxpayerNameConnector]

  def connectorWithResponse(json: Option[JsValue], status: Int = 200): TaxpayerNameConnector = {
    val nameResponse = json
      .map(js => Future.successful(js.as[NameFromHods]))
      .getOrElse(
        Future.failed(status match {
          case 404 => new NotFoundException("test")
          case _   => new RuntimeException("some other error")
        })
      )

    when(
      mockHttp.GET[NameFromHods](any[String], any[Seq[(String, String)]], any[Seq[(String, String)]])(
        any[HttpReads[NameFromHods]],
        any[HeaderCarrier],
        any[ExecutionContext]))
      .thenReturn(nameResponse)

    connector
  }
}

trait LogCapturing {

  import scala.jdk.CollectionConverters._
  import scala.reflect._

  def withCaptureOfLoggingFrom[T: ClassTag](body: (=> List[ILoggingEvent]) => Any): Any = {
    val logger = LoggerFactory.getLogger(classTag[T].runtimeClass).asInstanceOf[LogbackLogger]
    withCaptureOfLoggingFrom(logger)(body)
  }

  def withCaptureOfLoggingFrom(logger: LogbackLogger)(body: (=> List[ILoggingEvent]) => Any): Any = {
    val appender = new ListAppender[ILoggingEvent]()
    appender.setContext(logger.getLoggerContext)
    appender.start()
    logger.addAppender(appender)
    logger.setLevel(Level.ALL)
    logger.setAdditive(true)
    body(appender.list.asScala.toList)
  }
}
