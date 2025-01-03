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

package uk.gov.hmrc.securemessage.models

sealed trait JourneyStep extends Product with Serializable

final case class ShowLinkJourneyStep(returnUrl: String) extends JourneyStep
final case class ReplyFormJourneyStep(returnUrl: String) extends JourneyStep
case object AckJourneyStep extends JourneyStep

object SecureMessageUrlStep {

  def toJourneyStep(
    step: String,
    returnUrl: Option[String]
  ): Option[Either[String, JourneyStep]] =
    Step.values
      .find(_.toString == step)
      .map { stepValue =>
        (stepValue, returnUrl) match {
          case (Step.link, Some(r)) => Right(ShowLinkJourneyStep(r))
          case (Step.form, Some(r)) => Right(ReplyFormJourneyStep(r))
          case (Step.ack, None)     => Right(AckJourneyStep)
          case _ =>
            Left(s"Unexpected Journey parameter combination: $step and $returnUrl")
        }
      }
      .orElse(Option(Left(s"Unknown step value provided: $step")))
}

object Step extends Enumeration {
  val link, form, ack = Value
}
