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

package uk.gov.hmrc.securemessage.services.utils

import org.joda.time.{ DateTime, DateTimeZone, LocalDate }
import org.mongodb.scala.bson.ObjectId
import play.api.libs.json._
import uk.gov.hmrc.common.message.model.{ ExternalRef, Regime, _ }
import uk.gov.hmrc.domain.TaxIds._
import uk.gov.hmrc.domain._
import uk.gov.hmrc.mongo.workitem.ProcessingStatus
import uk.gov.hmrc.mongo.workitem.ProcessingStatus.ToDo

import java.util.UUID

object MessageFixtures {

  val utr = "1234567890"
  val form = "SA300"

  def testMessageWithContent(
    id: ObjectId,
    recipientId: TaxIdWithName = SaUtr("5554444333"),
    uuid: UUID,
    form: String = "SA300",
    validFrom: LocalDate = new LocalDate(2013, 12, 1),
    rescindment: Option[Rescindment] = None,
    alertFrom: Option[LocalDate] = Some(new LocalDate(2013, 12, 1)),
    subject: String = "Blah blah blah",
    readTime: Option[DateTime] = None,
    archiveTime: Option[DateTime] = None,
    alertDetails: AlertDetails = AlertDetails("templateId", None, Map()),
    status: ProcessingStatus = ToDo,
    testTime: Option[DateTime] = Some(SystemTimeSource.now),
    contentParameters: Option[MessageContentParameters] = None,
    statutory: Boolean = false,
    renderUrl: RenderUrl = RenderUrl("service", "relUrl"),
    content: String,
    sourceData: Option[String] = None,
    emailAlertEventUrl: Option[String] = None
  ) =
    Message(
      id = id,
      recipient = MessageFixtures.createTaxEntity(recipientId),
      subject = subject,
      body = Some(Details(Some(form), Some("print-suppression-notification"), None, None)),
      validFrom = validFrom,
      alertFrom = alertFrom,
      alertDetails = alertDetails,
      alerts = Some(EmailAlert(emailAddress = Some(s"$uuid@test.com"), testTime.get, true, None)),
      readTime = readTime,
      archiveTime = archiveTime,
      status = status,
      lastUpdated = testTime,
      contentParameters = contentParameters,
      rescindment = rescindment,
      hash = uuid.toString,
      statutory = statutory,
      renderUrl = renderUrl,
      content = Some(content),
      sourceData = sourceData,
      emailAlertEventUrl = emailAlertEventUrl
    )

  def testMessageWithoutContent(
    recipientId: TaxIdWithName = SaUtr("5554444333"),
    form: String = "SA300",
    suppressedAt: String = "2013-01-02",
    validFrom: LocalDate = new LocalDate(2013, 12, 1),
    rescindment: Option[Rescindment] = None,
    alertFrom: Option[LocalDate] = Some(new LocalDate(2013, 12, 1)),
    detailsId: String = "C0123456781234568",
    subject: String = "Blah blah blah",
    readTime: Option[DateTime] = None,
    archiveTime: Option[DateTime] = None,
    alerts: Option[EmailAlert] = Some(
      EmailAlert(
        emailAddress = Some(s"${UUID.randomUUID}@test.com"),
        DateTime.now.withZone(DateTimeZone.UTC),
        true,
        None)
    ),
    alertDetails: AlertDetails = AlertDetails("templateId", None, Map()),
    status: ProcessingStatus = ToDo,
    lastUpdated: Option[DateTime] = Some(SystemTimeSource.now),
    contentParameters: Option[MessageContentParameters] = None,
    hash: String = UUID.randomUUID.toString,
    statutory: Boolean = false,
    renderUrl: RenderUrl = RenderUrl("service", "relUrl"),
    messageType: Option[String] = Some("print-suppression-notification"),
    externalRef: Option[ExternalRef] = None,
    sourceData: Option[String] = None,
    emailAlertEventUrl: Option[String] = None,
    threadId: Option[String] = None,
    enquiryType: Option[String] = None,
    adviser: Option[Adviser] = None,
    replyTo: Option[String] = None,
    batchId: Option[String] = None,
    verificationBrake: Option[Boolean] = None,
    issueDate: Option[LocalDate] = None,
    id: ObjectId = new ObjectId,
    tags: Option[Map[String, String]] = None
  ): Message = {

    val details = issueDate.fold(
      Details(
        form = Some(form),
        `type` = messageType,
        suppressedAt = Some(suppressedAt),
        detailsId = Some(detailsId),
        threadId = threadId,
        enquiryType = enquiryType,
        adviser = adviser,
        replyTo = replyTo,
        batchId = batchId
      )
    )(
      _ =>
        Details(
          form = Some(form),
          `type` = messageType,
          suppressedAt = Some(suppressedAt),
          detailsId = Some(detailsId),
          threadId = threadId,
          enquiryType = enquiryType,
          adviser = adviser,
          replyTo = replyTo,
          batchId = batchId,
          issueDate = issueDate
      ))

    Message(
      id = id,
      recipient = MessageFixtures.createTaxEntity(recipientId),
      subject = subject,
      body = Some(details),
      validFrom = validFrom,
      alertFrom = alertFrom,
      alertDetails = alertDetails,
      alerts = alerts,
      readTime = readTime,
      archiveTime = archiveTime,
      status = status,
      lastUpdated = lastUpdated,
      contentParameters = contentParameters,
      rescindment = rescindment,
      hash = hash,
      statutory = statutory,
      renderUrl = renderUrl,
      externalRef = externalRef,
      sourceData = sourceData,
      verificationBrake = verificationBrake,
      emailAlertEventUrl = emailAlertEventUrl,
      tags = tags
    )
  }

  def testBrakeMessage(
    recipientId: TaxIdWithName = SaUtr("5554444333"),
    form: Option[String] = Some("SA300"),
    suppressedAt: String = "2013-01-02",
    validFrom: LocalDate = new LocalDate(2013, 12, 1),
    rescindment: Option[Rescindment] = None,
    alertFrom: Option[LocalDate] = Some(new LocalDate(2013, 12, 1)),
    detailsId: String = "C0123456781234568",
    subject: String = "Blah blah blah",
    readTime: Option[DateTime] = None,
    archiveTime: Option[DateTime] = None,
    alerts: Option[EmailAlert] = Some(
      EmailAlert(
        emailAddress = Some(s"${UUID.randomUUID}@test.com"),
        DateTime.now.withZone(DateTimeZone.UTC),
        true,
        None)
    ),
    alertDetails: AlertDetails = AlertDetails("templateId", None, Map()),
    status: ProcessingStatus = ToDo,
    lastUpdated: Option[DateTime] = Some(SystemTimeSource.now),
    contentParameters: Option[MessageContentParameters] = None,
    hash: String = UUID.randomUUID.toString,
    statutory: Boolean = false,
    renderUrl: RenderUrl = RenderUrl("service", "relUrl"),
    messageType: Option[String] = Some("print-suppression-notification"),
    externalRef: Option[ExternalRef] = None,
    sourceData: Option[String] = None,
    emailAlertEventUrl: Option[String] = None,
    threadId: Option[String] = None,
    enquiryType: Option[String] = None,
    adviser: Option[Adviser] = None,
    replyTo: Option[String] = None,
    batchId: Option[String] = None,
    verificationBrake: Option[Boolean] = None,
    issueDate: Option[LocalDate] = None
  ): Message = {

    val details = Details(
      form = form,
      `type` = messageType,
      suppressedAt = Some(suppressedAt),
      detailsId = Some(detailsId),
      threadId = threadId,
      enquiryType = enquiryType,
      adviser = adviser,
      replyTo = replyTo,
      batchId = batchId,
      issueDate = issueDate
    )

    Message(
      id = new ObjectId,
      recipient = MessageFixtures.createTaxEntity(recipientId),
      subject = subject,
      body = Some(details),
      validFrom = validFrom,
      alertFrom = alertFrom,
      alertDetails = alertDetails,
      alerts = alerts,
      readTime = readTime,
      archiveTime = archiveTime,
      status = status,
      lastUpdated = lastUpdated,
      contentParameters = contentParameters,
      rescindment = rescindment,
      hash = hash,
      statutory = statutory,
      renderUrl = renderUrl,
      externalRef = externalRef,
      sourceData = sourceData,
      verificationBrake = verificationBrake,
      emailAlertEventUrl = emailAlertEventUrl
    )
  }

  def atsTestMessage(
    recipientId: TaxIdWithName = SaUtr("5554444333"),
    validFrom: LocalDate = new LocalDate(2013, 12, 1),
    rescindment: Option[Rescindment] = None,
    alertFrom: Option[LocalDate] = Some(new LocalDate(2013, 12, 1)),
    subject: String = "Blah blah blah",
    readTime: Option[DateTime] = None,
    archiveTime: Option[DateTime] = None,
    alerts: Option[EmailAlert] = Some(
      EmailAlert(
        emailAddress = Some(s"${UUID.randomUUID}@test.com"),
        DateTime.now.withZone(DateTimeZone.UTC),
        true,
        None)
    ),
    status: ProcessingStatus = ToDo,
    lastUpdated: Option[DateTime] = Some(SystemTimeSource.now),
    contentParameters: Option[MessageContentParameters] = None,
    hash: String = "someHashValue",
    statutory: Boolean = false,
    renderUrl: RenderUrl = RenderUrl("service", "relUrl"),
    alertDetails: AlertDetails = AlertDetails("templateId", None, Map()),
    emailAlertEventUrl: Option[String] = None
  ) =
    Message(
      id = new ObjectId,
      recipient = MessageFixtures.createTaxEntity(recipientId),
      subject = subject,
      body = Some(Details(None, Some("tax-summary-notification"), None, None)),
      validFrom = validFrom,
      alertFrom = alertFrom,
      alertDetails = alertDetails,
      alerts = alerts,
      readTime = readTime,
      archiveTime = archiveTime,
      status = status,
      lastUpdated = lastUpdated,
      contentParameters = contentParameters,
      rescindment = rescindment,
      hash = hash,
      statutory = statutory,
      renderUrl = renderUrl,
      sourceData = None,
      emailAlertEventUrl = emailAlertEventUrl
    )

  def messageForSAWithAlertQueue(
    utr: String = utr,
    validFrom: LocalDate = testDate(0),
    form: String = "SA300",
    contentParameters: Option[MessageContentParameters] = None,
    detailsId: String = "C0123456781234568",
    hash: String = "someHashValue",
    statutory: Boolean = false,
    alertTemplateId: String = "templateId",
    renderUrl: RenderUrl = RenderUrl("service", "relUrl"),
    recipientName: Option[TaxpayerName] = None,
    alertQueue: Option[String] = None,
    emailAlertEventUrl: Option[String] = None
  ) =
    Message(
      id = new ObjectId,
      recipient = MessageFixtures.createTaxEntity(SaUtr(utr)),
      subject = "Your Tax Return",
      body = Some(
        Details(
          Some(form),
          Some("print-suppression-notification"),
          Some(validFrom.minusDays(1).toString()),
          Some(detailsId)
        )
      ),
      contentParameters = contentParameters,
      validFrom = validFrom,
      alertFrom = Some(validFrom),
      alertDetails = AlertDetails(alertTemplateId, recipientName, Map()),
      alerts = None,
      lastUpdated = Some(SystemTimeSource.now),
      hash = hash,
      statutory = statutory,
      renderUrl = renderUrl,
      sourceData = None,
      alertQueue = alertQueue,
      emailAlertEventUrl = emailAlertEventUrl
    )

  def messageForSA(
    utr: String = utr,
    validFrom: LocalDate = testDate(0),
    form: String = "SA300",
    contentParameters: Option[MessageContentParameters] = None,
    detailsId: String = "C0123456781234568",
    hash: String = "someHashValue",
    statutory: Boolean = false,
    alertTemplateId: String = "templateId",
    renderUrl: RenderUrl = RenderUrl("service", "relUrl"),
    recipientName: Option[TaxpayerName] = None,
    emailAlertEventUrl: Option[String] = None
  ) =
    Message(
      id = new ObjectId,
      recipient = MessageFixtures.createTaxEntity(SaUtr(utr)),
      subject = "Your Tax Return",
      body = Some(
        Details(
          Some(form),
          Some("print-suppression-notification"),
          Some(validFrom.minusDays(1).toString()),
          Some(detailsId)
        )
      ),
      contentParameters = contentParameters,
      validFrom = validFrom,
      alertFrom = Some(validFrom),
      alertDetails = AlertDetails(alertTemplateId, recipientName, Map()),
      alerts = None,
      lastUpdated = Some(SystemTimeSource.now),
      hash = hash,
      statutory = statutory,
      renderUrl = renderUrl,
      sourceData = None,
      emailAlertEventUrl = emailAlertEventUrl
    )

  def messageForNino(
    utr: String = utr,
    validFrom: LocalDate = testDate(0),
    form: String = "SA300",
    contentParameters: Option[MessageContentParameters] = None,
    detailsId: String = "C0123456781234568",
    hash: String = "someHashValue",
    statutory: Boolean = false,
    alertTemplateId: String = "templateId",
    renderUrl: RenderUrl = RenderUrl("service", "relUrl"),
    recipientName: Option[TaxpayerName] = None,
    emailAlertEventUrl: Option[String] = None
  ) =
    Message(
      id = new ObjectId,
      recipient = MessageFixtures.createTaxEntity(Nino("CS700100A")),
      subject = "Your Tax Return",
      body = Some(
        Details(
          Some(form),
          Some("print-suppression-notification"),
          Some(validFrom.minusDays(1).toString()),
          Some(detailsId)
        )
      ),
      contentParameters = contentParameters,
      validFrom = validFrom,
      alertFrom = Some(validFrom),
      alertDetails = AlertDetails(alertTemplateId, recipientName, Map()),
      alerts = None,
      lastUpdated = Some(SystemTimeSource.now),
      hash = hash,
      statutory = statutory,
      renderUrl = renderUrl,
      sourceData = None,
      emailAlertEventUrl = emailAlertEventUrl
    )

  def messageFor2WSM(
    id: ObjectId = new ObjectId,
    utr: String = utr,
    validFrom: LocalDate = testDate(0),
    form: String = "SA300",
    contentParameters: Option[MessageContentParameters] = None,
    detailsId: String = "C0123456781234568",
    hash: String = "someHashValue",
    statutory: Boolean = false,
    alertTemplateId: String = "templateId",
    renderUrl: RenderUrl = RenderUrl("service", "relUrl"),
    recipientName: Option[TaxpayerName] = None,
    topic: Option[String] = Some("some-topic"),
    emailAlertEventUrl: Option[String] = None,
    lifecycle: Option[Lifecycle] = None
  ) =
    Message(
      id,
      recipient = MessageFixtures.createTaxEntity(SaUtr(utr), Some("test@test.com")),
      subject = "Your Tax Return",
      body = Some(
        Details(
          Some(form),
          Some("print-suppression-notification"),
          Some(validFrom.minusDays(1).toString()),
          Some(detailsId),
          topic = topic
        )
      ),
      contentParameters = contentParameters,
      validFrom = validFrom,
      alertFrom = Some(validFrom),
      alertDetails = AlertDetails(alertTemplateId, recipientName, Map()),
      alerts = None,
      lastUpdated = Some(SystemTimeSource.now),
      hash = hash,
      statutory = statutory,
      renderUrl = renderUrl,
      sourceData = None,
      emailAlertEventUrl = emailAlertEventUrl,
      lifecycle = lifecycle
    )

  def gmcMessage(
    utr: String = utr,
    validFrom: LocalDate = testDate(0),
    form: String = "SA300",
    contentParameters: Option[MessageContentParameters] = None,
    detailsId: String = "C0123456781234568",
    hash: String = "someHashValue",
    statutory: Boolean = false,
    alertTemplateId: String = "templateId",
    renderUrl: RenderUrl = RenderUrl("service", "relUrl"),
    recipientName: Option[TaxpayerName] = None,
    externalRef: ExternalRef = ExternalRef("2342342341", "gmc"),
    sourceData: String = "someHashedSourceData",
    alerts: Option[EmailAlert] = None
  ) =
    Message(
      id = new ObjectId,
      externalRef = Some(externalRef),
      recipient = MessageFixtures.createTaxEntity(SaUtr(utr)),
      subject = "Your Tax Return",
      body = Some(
        Details(
          Some(form),
          Some("print-suppression-notification"),
          Some(validFrom.minusDays(1).toString()),
          Some(detailsId)
        )
      ),
      contentParameters = contentParameters,
      validFrom = validFrom,
      alertFrom = Some(validFrom),
      alertDetails = AlertDetails(alertTemplateId, recipientName, Map()),
      alerts = alerts,
      lastUpdated = Some(SystemTimeSource.now),
      hash = hash,
      statutory = statutory,
      renderUrl = renderUrl,
      sourceData = Some(sourceData),
      emailAlertEventUrl = None
    )

  def testDate(plusDays: Int) = new LocalDate(2013, 6, 1).plusDays(plusDays)

  def testTime(plusDays: Int, plusHours: Int) =
    testDate(plusDays).toDateTimeAtStartOfDay(DateTimeZone.UTC).plusHours(plusHours)

  def createTaxEntity(identifier: TaxIdWithName, email: Option[String] = None): TaxEntity = identifier match {
    case x: Nino         => TaxEntity(Regime.paye, x, email)
    case x: SaUtr        => TaxEntity(Regime.sa, x, email)
    case x: CtUtr        => TaxEntity(Regime.ct, x, email)
    case x: HmrcObtdsOrg => TaxEntity(Regime.fhdds, x)
    case x: HmrcMtdVat   => TaxEntity(Regime.vat, x)
    case x               => throw new RuntimeException(s"unsupported identifier $x")
  }

  def createRecipient(identifier: TaxIdWithName, email: Option[String] = None): Recipient = identifier match {
    case x: Nino         => Recipient(x, None, email, Some(Regime.paye))
    case x: SaUtr        => Recipient(x, None, email, Some(Regime.sa))
    case x: CtUtr        => Recipient(x, None, email, Some(Regime.ct))
    case x: HmrcObtdsOrg => Recipient(x, None, email, Some(Regime.fhdds))
    case x: HmrcMtdVat   => Recipient(x, None, email, Some(Regime.vat))
    case x               => throw new RuntimeException(s"unsupported identifier $x")
  }

  def testAlert(dateTime: DateTime) =
    Some(EmailAlert(emailAddress = Some(s"${UUID.randomUUID}@test.com"), dateTime, true, None))

  def testExtRef(source: String) = Some(ExternalRef(s"${UUID.randomUUID}", source))

  def updateIssueDate(jsMessage: JsValue, issueDate: LocalDate = LocalDate.now): JsValue =
    jsMessage
      .transform(
        (__ \ 'body \ 'issueDate).json.update(Reads.of[JsString].map { case _: JsString => JsString(s"$issueDate") }))
      .get

}
