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

import uk.gov.hmrc.auth.core
import uk.gov.hmrc.auth.core.AuthProvider.PrivilegedApplication
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.{ Nino => _, _ }
import uk.gov.hmrc.common.message.model.TaxEntity.{ Epaye, HmceVatdecOrg, HmrcAdOrg, HmrcCusOrg, HmrcIossOrg, HmrcPodsOrg, HmrcPodsPpOrg, HmrcPptOrg }
import uk.gov.hmrc.domain.TaxIds._
import uk.gov.hmrc.domain._
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class AuthIdentifiersConnector @Inject() (
  val authConnector: core.AuthConnector
)(implicit ec: ExecutionContext)
    extends AuthorisedFunctions {

  def getIdentifierValue(enrolment: Enrolment): Option[String] =
    enrolment.identifiers match {
      case Seq(identifier) => Some(identifier.value)
      case Seq(
            EnrolmentIdentifier("TaxOfficeNumber", officeNum),
            EnrolmentIdentifier("TaxOfficeReference", officeRef)
          ) =>
        Some(officeNum + officeRef)
      case _ => None
    }

  // scalastyle:off cyclomatic.complexity
  def collectEnrolments(enrolments: Enrolments): Set[TaxIdWithName] = enrolments.enrolments.flatMap { enrolment =>
    val taxIdValue = getIdentifierValue(enrolment)
    enrolment.key match {
      case "IR-CT"           => taxIdValue.map(CtUtr.apply)
      case "HMRC-NI"         => taxIdValue.map(Nino.apply)
      case "IR-SA"           => taxIdValue.map(SaUtr.apply)
      case "HMRC-OBTDS-ORG"  => taxIdValue.map(HmrcObtdsOrg.apply)
      case "HMRC-MTD-VAT"    => taxIdValue.map(HmrcMtdVat.apply)
      case "VRN"             => taxIdValue.map(Vrn.apply)
      case "IR-PAYE"         => taxIdValue.map(Epaye.apply)
      case "HMCE-VATDEC-ORG" => taxIdValue.map(HmceVatdecOrg.apply)
      case "HMRC-CUS-ORG"    => taxIdValue.map(HmrcCusOrg.apply)
      case "HMRC-PPT-ORG"    => taxIdValue.map(HmrcPptOrg.apply)
      case "HMRC-MTD-IT"     => taxIdValue.map(HmrcMtdItsa.apply)
      case "HMRC-PODS-ORG"   => taxIdValue.map(HmrcPodsOrg.apply)
      case "HMRC-PODSPP-ORG" => taxIdValue.map(HmrcPodsPpOrg.apply)
      case "HMRC-IOSS-ORG"   => taxIdValue.map(HmrcIossOrg.apply)
      case "HMRC-AD-ORG"     => taxIdValue.map(HmrcAdOrg.apply)
      case _                 => None
    }
  }

  private def vrnSet(vrnValue: String): Set[Vrn] = {
    val formatStr = (s: String) => "(\\d{3})(\\d{4})(\\d{2})".r.replaceAllIn(s, "$1 $2 $3")
    vrnValue.filterNot(_.isWhitespace) match {
      case value: String if value.length == 9 => Set(Vrn(value), Vrn(formatStr(value)))
      case _                                  => Set(Vrn(vrnValue))
    }
  }

  def currentTaxIdentifiers(implicit hc: HeaderCarrier): Future[Set[TaxIdWithName]] =
    authorised()
      .retrieve(Retrievals.allEnrolments) { enrolments =>
        Future.successful(collectEnrolments(enrolments))
      }
      .recoverWith { case _: AuthorisationException =>
        Future.successful(Set.empty)
      }

  def currentEffectiveTaxIdentifiers(implicit hc: HeaderCarrier): Future[Set[TaxIdWithName]] =
    currentTaxIdentifiers(hc).map { taxIds =>
      taxIds
        .flatMap { taxId =>
          taxId.name.toUpperCase match {
            case "HMRC-MTD-VAT"    => Set(taxId, HmceVatdecOrg(taxId.value)) ++ vrnSet(taxId.value)
            case "HMCE-VATDEC-ORG" => Set(taxId, HmrcMtdVat(taxId.value))
            case "HMRC-PODS-ORG"   => Set(taxId, HmrcPodsOrg(taxId.value))
            case "HMRC-PODSPP-ORG" => Set(taxId, HmrcPodsPpOrg(taxId.value))
            case "HMRC-IOSS-ORG"   => Set(taxId, HmrcIossOrg(taxId.value))
            case "HMRC-AD-ORG"     => Set(taxId, HmrcAdOrg(taxId.value))
            case "VRN"             => vrnSet(taxId.value)
            case _                 => Set(taxId)
          }
        }
    }

  def isStrideUser(implicit hc: HeaderCarrier): Future[Boolean] =
    authorised(AuthProviders(PrivilegedApplication))(Future.successful(true)).recoverWith {
      case _: AuthorisationException => Future.successful(false)
    }
}
