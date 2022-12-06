/*
 * Copyright 2022 HM Revenue & Customs
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

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.concurrent.{ IntegrationPatience, ScalaFutures }
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.{ HeaderNames, Status }
import uk.gov.hmrc.common.message.model.TaxEntity.{ HmceVatdecOrg, HmrcCusOrg }
import uk.gov.hmrc.domain._
import uk.gov.hmrc.http.{ Authorization, HeaderCarrier, UpstreamErrorResponse }
import uk.gov.hmrc.securemessage.services.utils.WithWireMock

class AuthIdentifiersConnectorSpec
    extends PlaySpec with GuiceOneAppPerSuite with ScalaFutures with WithWireMock with MockitoSugar
    with IntegrationPatience {

  override def dependenciesPort: Int =
    app.configuration
      .getOptional[Int]("microservice.services.auth.port")
      .getOrElse(throw new Exception("Port missing for Auth"))

  "AuthIdentifiers connector" must {

    "return an empty set if the auth call is not authorised" in new TestCase {
      givenThat(
        post(urlEqualTo("/auth/authorise"))
          .withHeader(HeaderNames.AUTHORIZATION, equalTo(authToken))
          .willReturn(
            aResponse()
              .withStatus(Status.UNAUTHORIZED)
          )
      )

      authConnector.currentEffectiveTaxIdentifiers.futureValue must be(Set())
    }

    "throw an exception if the auth call fails " in new TestCase {
      givenThat(
        post(urlEqualTo("/auth/authorise"))
          .withHeader(HeaderNames.AUTHORIZATION, equalTo(authToken))
          .willReturn(
            aResponse()
              .withStatus(Status.INTERNAL_SERVER_ERROR)
          )
      )

      authConnector.currentEffectiveTaxIdentifiers.failed.futureValue mustBe an[UpstreamErrorResponse]
    }

    "return an empty set if auth returns no identifiers" in new TestCase {

      val responseBody = """
                           |{
                           |  "allEnrolments": []
                           |}
                         """.stripMargin

      givenThat(
        post(urlEqualTo("/auth/authorise"))
          .withHeader(HeaderNames.AUTHORIZATION, equalTo(authToken))
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
              .withBody(responseBody)
          )
      )

      authConnector.currentEffectiveTaxIdentifiers.futureValue must be(Set())
    }

    "return a set of utr and nino" in new TestCase {

      val responseBody = """
                           |{
                           |  "allEnrolments": [
                           |    {
                           |      "key": "IR-SA",
                           |      "identifiers": [
                           |        {
                           |          "key": "UTR",
                           |          "value": "1872796160"
                           |        }
                           |      ],
                           |      "state": "Activated"
                           |    },
                           |    {
                           |      "key": "HMRC-NI",
                           |      "identifiers": [
                           |        {
                           |          "key": "NINO",
                           |          "value": "CE123456D"
                           |        }
                           |      ],
                           |      "state": "Activated",
                           |      "confidenceLevel": 200
                           |    }
                           |  ]
                           |}
                         """.stripMargin

      givenThat(
        post(urlEqualTo("/auth/authorise"))
          .withHeader(HeaderNames.AUTHORIZATION, equalTo(authToken))
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
              .withBody(responseBody)
          )
      )

      authConnector.currentEffectiveTaxIdentifiers.futureValue must be(Set(SaUtr("1872796160"), Nino("CE123456D")))
    }

    "get all val tax ids for if only HMRC-MTD-VAT enrolment " in new TestCase {

      val responseBody = """
                           |{
                           |  "allEnrolments": [
                           |    {
                           |      "key": "HMRC-MTD-VAT",
                           |      "identifiers": [
                           |        {
                           |          "key": "VRN",
                           |          "value": "example vrn"
                           |        }
                           |      ],
                           |      "state": "Activated",
                           |      "confidenceLevel": 200
                           |    }
                           |  ]
                           |}
                         """.stripMargin

      givenThat(
        post(urlEqualTo("/auth/authorise"))
          .withHeader(HeaderNames.AUTHORIZATION, equalTo(authToken))
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
              .withBody(responseBody)
          )
      )

      authConnector.currentEffectiveTaxIdentifiers.futureValue must be(
        Set(HmrcMtdVat("example vrn"), HmceVatdecOrg("example vrn"), Vrn("example vrn"))
      )
    }

    "get all val tax ids for if only VRN enrolment " in new TestCase {

      val responseBody = """
                           |{
                           |  "allEnrolments": [
                           |    {
                           |      "key": "VRN",
                           |      "identifiers": [
                           |        {
                           |          "key": "VRN",
                           |          "value": "example vrn"
                           |        }
                           |      ],
                           |      "state": "Activated",
                           |      "confidenceLevel": 200
                           |    }
                           |  ]
                           |}
                         """.stripMargin

      givenThat(
        post(urlEqualTo("/auth/authorise"))
          .withHeader(HeaderNames.AUTHORIZATION, equalTo(authToken))
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
              .withBody(responseBody)
          )
      )

      authConnector.currentEffectiveTaxIdentifiers.futureValue must be(
        Set(Vrn("example vrn"))
      )
    }

    "get all val tax ids for if only HMCE-VATDEC-ORG enrolment " in new TestCase {

      val responseBody = """
                           |{
                           |  "allEnrolments": [
                           |    {
                           |      "key": "HMCE-VATDEC-ORG",
                           |      "identifiers": [
                           |        {
                           |          "key": "VATRegNo",
                           |          "value": "example vrn"
                           |        }
                           |      ],
                           |      "state": "Activated",
                           |      "confidenceLevel": 200
                           |    },
                           |    {
                           |      "key": "HMRC-MTD-VAT",
                           |      "identifiers": [
                           |        {
                           |          "key": "VRN",
                           |          "value": "example vrn"
                           |        }
                           |      ],
                           |      "state": "Activated",
                           |      "confidenceLevel": 200
                           |    },
                           |    {
                           |      "key": "VRN",
                           |      "identifiers": [
                           |        {
                           |          "key": "VRN",
                           |          "value": "example vrn"
                           |        }
                           |      ],
                           |      "state": "Activated",
                           |      "confidenceLevel": 200
                           |    }
                           |  ]
                           |}
                         """.stripMargin

      givenThat(
        post(urlEqualTo("/auth/authorise"))
          .withHeader(HeaderNames.AUTHORIZATION, equalTo(authToken))
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
              .withBody(responseBody)
          )
      )

      authConnector.currentEffectiveTaxIdentifiers.futureValue must be(
        Set(HmrcMtdVat("example vrn"), HmceVatdecOrg("example vrn"), Vrn("example vrn"))
      )
    }

    "get all val tax ids for if both HMCE-VATDEC-ORG and HMRC-VATDEC-ORG enrolments " in new TestCase {

      val responseBody = """
                           |{
                           |  "allEnrolments": [
                           |    {
                           |      "key": "HMCE-VATDEC-ORG",
                           |      "identifiers": [
                           |        {
                           |          "key": "VATRegNo",
                           |          "value": "example vrn"
                           |        }
                           |      ],
                           |      "state": "Activated",
                           |      "confidenceLevel": 200
                           |    }
                           |  ]
                           |}
                         """.stripMargin

      givenThat(
        post(urlEqualTo("/auth/authorise"))
          .withHeader(HeaderNames.AUTHORIZATION, equalTo(authToken))
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
              .withBody(responseBody)
          )
      )

      authConnector.currentEffectiveTaxIdentifiers.futureValue must be(
        Set(HmrcMtdVat("example vrn"), HmceVatdecOrg("example vrn"))
      )
    }

    "get all val tax ids for only HMRC-CUS-ORG enrolment " in new TestCase {

      val responseBody = """
                           |{
                           |  "allEnrolments": [
                           |    {
                           |      "key": "HMRC-CUS-ORG",
                           |      "identifiers": [
                           |        {
                           |          "key": "EORINumber",
                           |          "value": "example eori"
                           |        }
                           |      ],
                           |      "state": "Activated",
                           |      "confidenceLevel": 200
                           |    }
                           |  ]
                           |}
                         """.stripMargin

      givenThat(
        post(urlEqualTo("/auth/authorise"))
          .withHeader(HeaderNames.AUTHORIZATION, equalTo(authToken))
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
              .withBody(responseBody)
          )
      )

      authConnector.currentEffectiveTaxIdentifiers.futureValue must be(
        Set(HmrcCusOrg("example eori"))
      )
    }

    "get all val tax ids for only HMRC-MTD-IT enrolment " in new TestCase {

      val responseBody = """
                           |{
                           |  "allEnrolments": [
                           |    {
                           |      "key": "HMRC-MTD-IT",
                           |      "identifiers": [
                           |        {
                           |          "key": "MTDITID",
                           |          "value": "X99999999999"
                           |        }
                           |      ],
                           |      "state": "Activated",
                           |      "confidenceLevel": 200
                           |    }
                           |  ]
                           |}
                         """.stripMargin

      givenThat(
        post(urlEqualTo("/auth/authorise"))
          .withHeader(HeaderNames.AUTHORIZATION, equalTo(authToken))
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
              .withBody(responseBody)
          )
      )

      authConnector.currentEffectiveTaxIdentifiers.futureValue must be(
        Set(HmrcMtdItsa("X99999999999"))
      )
    }

    "get tax id for empRef identifier" in new TestCase {

      val responseBody = """
                           |{
                           |  "allEnrolments": [
                           |    {
                           |      "key": "IR-PAYE",
                           |      "identifiers": [
                           |        {
                           |          "key": "TaxOfficeNumber",
                           |          "value": "840"
                           |        },
                           |        {
                           |          "key": "TaxOfficeReference",
                           |          "value": "Pd00123456"
                           |        }
                           |      ],
                           |      "state": "Activated",
                           |      "confidenceLevel": 200
                           |    }
                           |  ]
                           |}
                         """.stripMargin

      givenThat(
        post(urlEqualTo("/auth/authorise"))
          .withHeader(HeaderNames.AUTHORIZATION, equalTo(authToken))
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
              .withBody(responseBody)
          )
      )

      val identifier = authConnector.currentEffectiveTaxIdentifiers.futureValue.head
      identifier.name must be("EMPREF")
      identifier.value must be("840Pd00123456")
    }

    "return empty set when given an enrolment with multiple non empRef identifiers" in new TestCase {

      val responseBody = """
                           |{
                           |  "allEnrolments": [
                           |    {
                           |      "key": "IR-PAYE",
                           |      "identifiers": [
                           |        {
                           |          "key": "fakeKey1",
                           |          "value": "666"
                           |        },
                           |        {
                           |          "key": "fakeKey2",
                           |          "value": "something"
                           |        }
                           |      ],
                           |      "state": "Activated",
                           |      "confidenceLevel": 200
                           |    }
                           |  ]
                           |}
                         """.stripMargin

      givenThat(
        post(urlEqualTo("/auth/authorise"))
          .withHeader(HeaderNames.AUTHORIZATION, equalTo(authToken))
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
              .withBody(responseBody)
          )
      )

      authConnector.currentEffectiveTaxIdentifiers.futureValue must be(Set.empty)
    }
  }
  trait TestCase {

    val authToken = "authToken23432"
    implicit val hc: HeaderCarrier = HeaderCarrier(authorization = Some(Authorization(authToken)))

    lazy val authConnector: AuthIdentifiersConnector =
      app.injector.instanceOf[AuthIdentifiersConnector]
  }

}
