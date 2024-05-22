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

import com.github.tomakehurst.wiremock.client.WireMock._
import org.apache.commons.codec.binary.Base64
import org.scalatest.concurrent.{ IntegrationPatience, ScalaFutures }
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.{ HeaderNames, Status }
import play.api.inject._
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.common.message.model.TaxEntity.{ HmceVatdecOrg, HmrcCusOrg, HmrcIossOrg, HmrcPodsOrg, HmrcPodsPpOrg, HmrcPptOrg }
import uk.gov.hmrc.domain._
import uk.gov.hmrc.http.{ Authorization, HeaderCarrier, UpstreamErrorResponse }
import uk.gov.hmrc.mongo.metrix.MetricOrchestrator
import uk.gov.hmrc.securemessage.services.utils.{ MetricOrchestratorStub, WithWireMock }

class AuthIdentifiersConnectorSpec
    extends PlaySpec with GuiceOneAppPerSuite with ScalaFutures with WithWireMock with MockitoSugar
    with MetricOrchestratorStub with IntegrationPatience {

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .overrides(
        bind[MetricOrchestrator].toInstance(mockMetricOrchestrator).eagerly()
      )
      .configure(
        "metrics.enabled"                 -> "false",
        "microservice.services.auth.port" -> "8501"
      )
      .build()

  override def dependenciesPort: Int =
    app.configuration
      .getOptional[Int]("microservice.services.auth.port")
      .getOrElse(throw new Exception("Port missing for Auth"))

  "AuthIdentifiers connector" must {

    "test" in {
      val string =
        """
          |
          |<p class="govuk-body">This is because you missed the payment deadline for the tax year ended 5 April 2023.</p>
          |
          |<h3 class="govuk-heading-m heading-medium">What to do next</h3>
          |
          |<ul class="govuk-list govuk-list--bullet  list list-bullet"> <ul><li>Pay all the tax you owe for this tax year to avoid further penalties for late payment, and interest.</li></ul> </ul>
          |
          |<p class="govuk-body govuk-!-font-weight-bold bold" >
          |How your penalty is calculated
          |</p>
          |
          |<div class="penalty-details">
          |    <table class="govuk-table">
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |			<tr class="govuk-table__row">
          |                <th class="govuk-table__header bold ">Late payment</th>
          |                <th class="govuk-table__header bold ">Amount</th>
          |            </tr>
          |
          |
          |            <tr class="govuk-table__row" >
          |                <td class="govuk-table__cell">30 days late - a penalty of 5&#37; of &#163;1161.80 (the total tax unpaid at 01 March 2024). <br>Paragraph 3(2) of Schedule 56 to the Finance Act 2009.</td>
          |                <td class="govuk-table__cell">&#163;58</td>
          |            </tr>
          |
          |
          |
          |
          |
          |
          |
          |
          |
          |        <tr class="govuk-table__row">
          |            <td class="govuk-table__header bold" >Total penalties</td>
          |            <td class="govuk-table__header bold">&#163;58</td>
          |        </tr>
          |        </tbody>
          |    </table>
          |</div>
          |
          |<p class="govuk-body">Previous penalties (and interest) aren&#39;t included here.</p>
          |
          |
          |<h2 class="govuk-heading-m heading-medium">You have time to appeal to us or ask us for a review</h2>
          |
          |<p class="govuk-body">You have 30 days to appeal or ask for a review</p>
          |
          |<p class="govuk-body">Your deadline depends on which of the following your penalty notice states:</p>
          |
          |<ul class="govuk-list govuk-list--bullet  list list-bullet">
          |    <li><span>pay within 30 days</span></li>
          |    <li><span>pay by a specific date</span></li>
          |
          |</ul>
          |
          |<h2 class="govuk-heading-m heading-medium">Reasons you can appeal</h2>
          |
          |<p class="govuk-body">You can appeal if one of the following applies:</p>
          |
          |<ul class="govuk-list govuk-list--bullet list list-bullet">
          |
          |    <li>you do not think your penalty is due, for example because you have already submitted your return</li>
          |    <li>you <a id="reasonable-excuses-href" href="https://www.gov.uk/tax-appeals/reasonable-excuses" target="_self" data-sso="false">have a reasonable excuse</a> for missing the deadline</li>
          |
          |</ul>
          |
          |<h2 class="govuk-heading-m heading-medium">If you cannot pay your tax</h2>
          |
          |<p class="govuk-body">We can agree &#39;time to pay&#39; arrangements with you. We agree these on a case-by-case basis to meet your circumstances.</p>
          |<p class="govuk-body">To talk about your options, call the dedicated helpline on 0300 200 3822.</p>
          |
          |<h2 class="govuk-heading-m heading-medium">If you need extra support</h2>
          |
          |<p class="govuk-body">We can help if you have health issues or personal circumstances that make it difficult for you to deal with us.</p>
          |<p class="govuk-body"><a id="dealing-hmrc-additional-needs-href" href="https://www.gov.uk/dealing-hmrc-additional-needs" target="_self" data-sso="false">Get help from HMRC if you need extra support</a></p>
          |
          |<h2 class="govuk-heading-m heading-medium">More information</h2>
          |
          |<ul class="govuk-list govuk-list list list">
          |    <li><a id="deadline-and-penalties-href" href="https://www.gov.uk/self-assessment-tax-returns/deadlines" target="_blank" data-sso="false" rel="external noopener noreferrer">Deadlines and penalties<span class="visuallyhidden">link opens in a new window</span></a></li>
          |    <li><a id="sa-payment-href" href="https://www.tax.service.gov.uk/pay-online/self-assessment/make-a-payment" target="_self" data-sso="client">Pay your penalty and pay your tax bill</a></li>
          |    <li><a id="difficulties-paying-href" href="https://www.gov.uk/difficulties-paying-hmrc" target="_blank" data-sso="false" rel="external noopener noreferrer">Problems paying your tax bill<span class="visuallyhidden">link opens in a new window</span></a></li>
          |    <li><a id="appeal-a-penalty-href" href="https://www.gov.uk/tax-appeals/penalty" target="_blank" data-sso="false" rel="external noopener noreferrer">Appeal a penalty<span class="visuallyhidden">link opens in a new window</span></a></li>
          |    <li><a id="where-to-send-tax-return-href" href="https://www.tax.service.gov.uk/self-assessment/ind/1689178887/taxreturn" target="_self" data-sso="client">Send your tax return</a></li>
          |    <li><a id="how-to-send-tax-return-href" href="https://www.gov.uk/self-assessment-tax-returns/sending-return" target="_blank" data-sso="false" rel="external noopener noreferrer">How to send a tax return<span class="visuallyhidden">link opens in a new window</span></a></li>
          |    <li><a id="contact-hmrc-href" href="https://www.gov.uk/government/organisations/hm-revenue-customs/contact/self-assessment" target="_blank" data-sso="false" rel="external noopener noreferrer">Contact HMRC<span class="visuallyhidden">link opens in a new window</span></a></li>
          |</ul>
          |%jx˛-w!z'!v,u֫Sa"w0pj^vnr<div class="govuk-grid-row grid-row">
          |<div class="govuk-grid-column-two-thirds column-two-thirds">
          |<section lang="cy">
          |<p class="govuk-body">Rhaid i chi anfon eich diweddariad incwm a threuliau ar gyfer eich incwm o eiddo tramor ar gyfer y chwarter yn diweddu 05 Gorffennaf 2020 atom.</p>
          |<p class="govuk-body">Mae angen i chi anfon y diweddariadau atom ar neu cyn 05 Awst 2020.</p>
          |<p class="govuk-body">Mae’n bosibl y byddwn yn rhoi cosb i chi os bydd eich diweddariadau chwarterol yn hwyr.</p>
          |<p class="govuk-body">Eich cyfrifoldeb chi yw sicrhau eich bod yn anfon yr holl gyflwyniadau angenrheidiol atom. Os oes gennych asiant, efallai y byddwch am drafod y nodyn atgoffa hwn gyda nhw.</p>
          |<p class="message_time faded-text--small govuk-body-s">Cyfeirnod y neges&#58; ITSAQU1 05/22 1.0</p>
          |</section>
          |</div>
          |</div>""".stripMargin
      val test = Base64.encodeBase64String(string.getBytes("UTF-8"))

      val result = new String(Base64.decodeBase64(test.getBytes("UTF-8")))
      result mustBe ""
    }

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
                           |          "value": "123 4567 89"
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
        Set(Vrn("123456789"), Vrn("123 4567 89"))
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

    "get tax ids for HMRC-OBTDS-ORG enrolment " in new TestCase {

      val responseBody = """
                           |{
                           |  "allEnrolments": [
                           |    {
                           |      "key": "HMRC-OBTDS-ORG",
                           |      "identifiers": [
                           |        {
                           |           "key":"HMRC-OBTDS-ORG",
                           |           "value":"XZFH00000100024"
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
        Set(HmrcObtdsOrg("XZFH00000100024"))
      )
    }

    "return a set of CtUtr and nino" in new TestCase {

      val responseBody = """
                           |{
                           |  "allEnrolments": [
                           |    {
                           |      "key": "IR-CT",
                           |      "identifiers": [
                           |        {
                           |          "key": "UTR",
                           |          "value": "1872796160"
                           |        }
                           |      ],
                           |      "state": "Activated"
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

      authConnector.currentEffectiveTaxIdentifiers.futureValue must be(Set(CtUtr("1872796160")))
    }

    "get all val tax ids for only HMRC-PPT-ORG enrolment " in new TestCase {

      val responseBody = """
                           |{
                           |  "allEnrolments": [
                           |    {
                           |      "key": "HMRC-PPT-ORG",
                           |      "identifiers": [
                           |        {
                           |          "key": "EtmpRegistrationNumber",
                           |          "value": "example Etmp Registration Number"
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
        Set(HmrcPptOrg("example Etmp Registration Number"))
      )
    }

    "get all val tax ids for only HMRC-PODS-ORG enrolment " in new TestCase {

      val responseBody = """
                           |{
                           |  "allEnrolments": [
                           |    {
                           |      "key": "HMRC-PODS-ORG",
                           |      "identifiers": [
                           |        {
                           |          "key": "HMRC-PODS-ORG",
                           |          "value": "example HMRC-PODS-ORG"
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
        Set(HmrcPodsOrg("example HMRC-PODS-ORG"))
      )
    }

    "get all val tax ids for only HMRC-PODSPP-ORG enrolment " in new TestCase {

      val responseBody = """
                           |{
                           |  "allEnrolments": [
                           |    {
                           |      "key": "HMRC-PODSPP-ORG",
                           |      "identifiers": [
                           |        {
                           |          "key": "HMRC-PODSPP-ORG",
                           |          "value": "example HMRC-PODSPP-ORG"
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
        Set(HmrcPodsPpOrg("example HMRC-PODSPP-ORG"))
      )
    }

    "get all val tax ids for only HMRC-IOSS-ORG enrolment " in new TestCase {

      val responseBody = """
                           |{
                           |  "allEnrolments": [
                           |    {
                           |      "key": "HMRC-IOSS-ORG",
                           |      "identifiers": [
                           |        {
                           |          "key": "IOSSNumber",
                           |          "value": "example HMRC-IOSS-ORG"
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
        Set(HmrcIossOrg("example HMRC-IOSS-ORG"))
      )
    }

    "isStrideUser return Future when the call to authorise succeed" in new TestCase {
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

      authConnector.isStrideUser.futureValue mustBe true
    }

    "isStrideUser return Future when the call to authorise fails" in new TestCase {

      givenThat(
        post(urlEqualTo("/auth/authorise"))
          .withHeader(HeaderNames.AUTHORIZATION, equalTo(authToken))
          .willReturn(
            aResponse()
              .withStatus(Status.UNAUTHORIZED)
          )
      )

      authConnector.isStrideUser.futureValue mustBe false
    }
  }
  trait TestCase {

    val authToken = "authToken23432"
    implicit val hc: HeaderCarrier = HeaderCarrier(authorization = Some(Authorization(authToken)))

    lazy val authConnector: AuthIdentifiersConnector =
      app.injector.instanceOf[AuthIdentifiersConnector]
  }

}
