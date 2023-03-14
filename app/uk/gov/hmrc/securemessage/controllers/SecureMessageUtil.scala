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

package uk.gov.hmrc.securemessage.controllers

import org.apache.commons.codec.binary.Base64
import org.bson.types.ObjectId
import org.joda.time.{ DateTime, LocalDate }
import org.joda.time.format.DateTimeFormat
import org.jsoup.Jsoup
import org.jsoup.nodes.Document.OutputSettings
import org.jsoup.safety.{ Safelist => JsouptAllowList }
import play.api.http.Status.{ BAD_REQUEST, CONFLICT, NOT_FOUND }
import play.api.i18n.Messages
import play.api.libs.json.{ JsArray, JsObject, JsValue, Json }
import play.api.mvc.Results.Created
import play.api.mvc.{ AnyContent, Request, Result }
import play.api.{ Configuration, Logging }
import uk.gov.hmrc.common.message.emailaddress.EmailAddress
import uk.gov.hmrc.common.message.failuremodule.FailureResponseService.errorResponseResult
import uk.gov.hmrc.common.message.model.{ AlertDetails, AlertQueueTypes, MessagesCount }
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.domain.TaxIds.TaxIdWithName
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.workitem.ProcessingStatus.Deferred
import uk.gov.hmrc.mongo.workitem.WorkItem
import uk.gov.hmrc.play.audit.http.connector.{ AuditConnector, AuditResult }
import uk.gov.hmrc.play.audit.model.{ DataEvent, EventTypes }
import uk.gov.hmrc.securemessage.SecureMessageError
import uk.gov.hmrc.securemessage.connectors.{ EmailValidation, EntityResolverConnector, TaxpayerNameConnector }
import uk.gov.hmrc.securemessage.models.core.{ Count, FilterTag, Identifier, MessageFilter }
import uk.gov.hmrc.securemessage.models.v4.{ Content, ExtraAlertConfig, SecureMessage }
import uk.gov.hmrc.securemessage.repository.{ ExtraAlert, ExtraAlertRepository, SecureMessageRepository, StatsMetricRepository }
import uk.gov.hmrc.securemessage.services.MessageBrakeService

import javax.inject.{ Inject, Named }
import scala.collection.JavaConverters._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

object SecureMessageUtil {

  def isGmc(message: SecureMessage): Boolean = "gmc".equalsIgnoreCase(message.externalRef.source)

  def extractMessageDate(message: SecureMessage): String =
    message.details.flatMap(_.issueDate) match {
      case Some(issueDate) => formatter(issueDate)
      case None            => formatter(message.validFrom)
    }

  def localizedExtractMessageDate(message: SecureMessage)(implicit messages: Messages): String =
    message.details.flatMap(_.issueDate) match {
      case Some(issueDate) => localizedFormatter(issueDate)
      case None            => localizedFormatter(message.validFrom)
    }

  private val dateFormatter = DateTimeFormat.forPattern("dd MMMM yyyy")
  def formatter(date: LocalDate): String = date.toString(dateFormatter)

  private def localizedFormatter(date: LocalDate)(implicit messages: Messages): String = {
    val formatter =
      if (messages.lang.language == "cy") {
        DateTimeFormat.forPattern(s"d '${messages(s"month.${date.getMonthOfYear}")}' yyyy")
      } else {
        dateFormatter
      }
    date.toString(formatter)
  }

  private def errorResponseWithErrorId(errorMessage: String, responseCode: Int = BAD_REQUEST) =
    errorResponseResult(errorMessage, responseCode, showErrorID = true)

  private val NotificationType = "notificationType"
}

class SecureMessageUtil @Inject()(
  @Named("app-name") appName: String,
  entityResolverConnector: EntityResolverConnector,
  taxpayerNameConnector: TaxpayerNameConnector,
  secureMessageRepository: SecureMessageRepository,
  extraAlertRepository: ExtraAlertRepository,
  statsMetricRepository: StatsMetricRepository,
  messageBrakeService: MessageBrakeService,
  auditConnector: AuditConnector,
  configuration: Configuration)(implicit ec: ExecutionContext)
    extends Logging {
  import SecureMessageUtil._

  lazy val defaultAuditEventMaxSize = 128000
  lazy val disableMessageBrake: Boolean = configuration.getOptional[Boolean]("disableMessageBrake").getOrElse(false)
  lazy val auditEventMaxSize: Int =
    configuration.getOptional[Int]("auditEventMaxSize").getOrElse(defaultAuditEventMaxSize)

  val extraAlerts: List[ExtraAlertConfig] = configuration.underlying
    .getObjectList("alertProfile")
    .asScala
    .map(_.unwrapped().asScala.toMap)
    .map(ExtraAlertConfig(_))
    .toList

  def validateAndCreateMessage(
    message: SecureMessage)(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Result] =
    isValidSecureMessage(message) match {
      case Success(_) if message.recipient.email.isEmpty => checkPreferencesAndCreateMessage(message)
      case Success(_)                                    => cleanUpAndCreateMessage(message)
      case Failure(exception)                            => Future.successful(errorResponseWithErrorId(exception.getMessage))
    }

  def isValidSecureMessage(message: SecureMessage): Try[SecureMessage] =
    for {
      _ <- checkValidSourceData(message)
      _ <- checkEmptyEmailAddress(message)
      _ <- checkEmptyAlertQueue(message)
      _ <- checkDetailsIsPresent(message)
      _ <- checkValidIssueDate(message)
      _ <- checkInvalidEmailAddress(message)
      _ <- checkEmailAbsentIfInvalidTaxId(message)
      _ <- checkValidAlertQueue(message)
      _ <- checkValidContent(message)
    } yield message

  def checkValidContent(message: SecureMessage): Try[SecureMessage] = {
    for (content <- message.content) {
      if (!Base64.isBase64(content.body)) {
        Failure(MessageValidationException("Content Body: Invalid content"))
      }
    }
    Success(message)
  }

  def checkValidSourceData(message: SecureMessage): Try[SecureMessage] = message.details.flatMap(_.sourceData) match {
    case Some(data) if data.trim.isEmpty || !Base64.isBase64(data) =>
      Failure(new IllegalArgumentException("sourceData: invalid source data provided"))
    case Some(data) if data.trim.nonEmpty || Base64.isBase64(data) => Success(message)
    case None if !isGmc(message)                                   => Success(message)
    case _                                                         => Failure(MessageValidationException("Invalid Message"))
  }

  def checkDetailsIsPresent(message: SecureMessage): Try[SecureMessage] = message match {
    case m if m.details.exists(_.formId.nonEmpty) => Success(m)
    case _                                        => Failure(MessageValidationException("details: details not provided where it is required"))
  }

  def checkEmptyEmailAddress(message: SecureMessage): Try[SecureMessage] =
    message.alertDetails.data.get("email") match {
      case Some(email) if email.trim.isEmpty =>
        Failure(MessageValidationException("email: invalid email address provided"))
      case _ => Success(message)
    }

  def checkEmptyAlertQueue(message: SecureMessage): Try[SecureMessage] = message.alertQueue match {
    case Some(queue) if queue.trim.isEmpty =>
      Failure(MessageValidationException("alertQueue: invalid alert queue provided"))
    case _ => Success(message)
  }

  def checkValidIssueDate(message: SecureMessage): Try[SecureMessage] = {
    val validFrom = message.validFrom
    val issueDate = message.details.flatMap(_.issueDate).getOrElse(validFrom)
    if (validFrom == issueDate || validFrom.isAfter(issueDate)) {
      Success(message)
    } else {
      Failure(MessageValidationException("Issue date after the valid from date"))
    }
  }

  def checkInvalidEmailAddress(message: SecureMessage): Try[SecureMessage] = message.recipient.email match {
    case Some(email) if EmailAddress.isValid(email) => Success(message)
    case Some(_)                                    => Failure(MessageValidationException("email: invalid email address provided"))
    case None                                       => Success(message)
  }

  def checkEmailAbsentIfInvalidTaxId(message: SecureMessage): Try[SecureMessage] = message.recipient.email match {
    case None if !isValidTaxIdentifier(message.recipient.identifier.name) =>
      Failure(MessageValidationException("email: email address not provided"))
    case _ => Success(message)
  }

  def isValidTaxIdentifier(taxId: String): Boolean = {
    // Refer "domain" library for valid Tax Identifier names
    val taxIdentifiers =
      List(
        "nino",
        "sautr",
        "ctutr",
        "HMRC-OBTDS-ORG",
        "HMRC-MTD-VAT",
        "empRef",
        "HMCE-VATDEC-ORG",
        "HMRC-CUS-ORG",
        "HMRC-PPT-ORG",
        "HMRC-MTD-IT")
    taxIdentifiers.contains(taxId)
  }

  def checkValidAlertQueue(message: SecureMessage): Try[SecureMessage] = message.alertQueue match {
    case Some(alertQueue) if AlertQueueTypes.alertQueueTypes.contains(alertQueue) => Success(message)
    case Some(_)                                                                  => Failure(MessageValidationException("Invalid alert queue submitted"))
    case _                                                                        => Success(message)
  }

  def checkPreferencesAndCreateMessage(
    message: SecureMessage)(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Result] = {

    val TAXPAYER_NOTFOUND = errorResponseWithErrorId(
      "The backend has rejected the message due to not being able to verify the email address.",
      NOT_FOUND)
    entityResolverConnector
      .verifiedEmailAddress(message.recipient)
      .flatMap { resp =>
        resp.value match {
          case Left(failure) if failure.getMessage.startsWith("email: not verified") =>
            Future.successful(errorResponseWithErrorId(failure.getMessage, BAD_REQUEST))

          case Left(_) => Future.successful(TAXPAYER_NOTFOUND)
          case Right(EmailValidation(email)) =>
            val updatedAlertDetails: AlertDetails =
              message.alertDetails.copy(data = message.alertDetails.data ++ Map("email" -> email))
            cleanUpAndCreateMessage(message.copy(emailAddress = email, alertDetails = updatedAlertDetails))
        }
      }
      .recover {
        case _ => TAXPAYER_NOTFOUND
      }
  }

  def cleanUpAndCreateMessage(
    message: SecureMessage)(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Result] =
    cleanupContent(message).flatMap(m => createMessage(m)).recoverWith {
      case e: Throwable => Future.successful(errorResponseWithErrorId(e.getMessage))
    }

  def cleanupContent(message: SecureMessage): Future[SecureMessage] = {
    val updateContent = for {
      content <- message.content
    } yield {
      cleanUpSubjectAndBody(content)
    }
    Try(updateContent) match {
      case Success(contentList) => Future.successful(message.copy(content = contentList))
      case Failure(exception)   => Future.failed(exception)
    }
  }

  def cleanUpSubjectAndBody(content: Content): Content =
    cleanHtml(content.subject) match {
      case Success(cleanSubject) =>
        cleanHtml(
          content.body,
          List(
            AllowedTagAndAttributes("details"),
            AllowedTagAndAttributes("summary"),
            AllowedTagAndAttributes("section", List("lang")))) match {
          case Success(cleanContent) =>
            content.copy(subject = cleanSubject, body = cleanContent)
          case Failure(e) => throw e
        }

      case Failure(exception) => throw exception
    }

  private def cleanHtml(dirtyHtml: String, extraAllowedTags: List[AllowedTagAndAttributes] = List()): Try[String] = {
    val settings = new OutputSettings().prettyPrint(false).syntax(OutputSettings.Syntax.xml)
    Try(Jsoup.clean(dirtyHtml, "", relaxedAllowlistWithClassAttributes(extraAllowedTags), settings))
  }

  //scalastyle:off
  private def relaxedAllowlistWithClassAttributes(extraTags: List[AllowedTagAndAttributes]): JsouptAllowList = {
    // We want to allow "class" for all allowed tags.  Unfortunately there is no way to do
    // getTags() on a Allowlist, so I have copied the list of tags from the Allowlist.relaxed()
    // implementation here.  Obviously this is not ideal, but there isn't a way around it.
    val allTags = List(
      "a",
      "b",
      "blockquote",
      "br",
      "caption",
      "cite",
      "code",
      "col",
      "colgroup",
      "dd",
      "div",
      "dl",
      "dt",
      "em",
      "h1",
      "h2",
      "h3",
      "h4",
      "h5",
      "h6",
      "i",
      "img",
      "li",
      "ol",
      "p",
      "pre",
      "q",
      "small",
      "span",
      "strike",
      "strong",
      "sub",
      "sup",
      "table",
      "tbody",
      "td",
      "tfoot",
      "th",
      "thead",
      "tr",
      "u",
      "ul"
    )
    //scalastyle:on

    val allTagsAndAttributes = allTags.map(t => AllowedTagAndAttributes(t))

    (allTagsAndAttributes ++ extraTags)
      .foldLeft(JsouptAllowList.relaxed().addAttributes("a", "target")) { (allowlist, tagAndAttributes) =>
        val attributes = "class" :: tagAndAttributes.attributes
        allowlist.addAttributes(tagAndAttributes.tag, attributes: _*)
      }
  }

  private def createMessage(
    message: SecureMessage)(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Result] =
    for {
      messageWithVerificationBrake <- checkAndUpdateMessageBrake(message)
      messageWithCleanAlertQueue   <- ignoreAlertQueueIfGmcAndSa(messageWithVerificationBrake)
      messageWithTaxpayerName      <- addTaxpayerNameToMessageIfRequired(messageWithCleanAlertQueue)
      isUnique                     <- secureMessageRepository.save(messageWithTaxpayerName)
    } yield {
      if (isUnique) {
        val messageId = message._id.toString
        auditCreateMessageFor(EventTypes.Succeeded, messageWithTaxpayerName, "Message Created")
        statsMetricRepository
          .incrementCreated(
            messageWithTaxpayerName.recipient.identifier.name,
            messageWithTaxpayerName.details.map(_.formId).getOrElse("NoForm"))

        addExtraAlerts(messageWithTaxpayerName)
        Created(Json.obj("id" -> messageId))
      } else {
        val ref = message.externalRef.source
        logger.warn(s"Duplicate message with ref $ref has not been stored")
        statsMetricRepository.incrementDuplicate(ref)
        auditCreateMessageFor(EventTypes.Failed, messageWithTaxpayerName, "Message Duplicated")
        errorResponseWithErrorId(
          "The backend has rejected the message due to duplicated message content or external reference ID.",
          CONFLICT)

      }
    }

  def auditCreateMessageFor(auditType: String, m: SecureMessage, transactionName: String)(
    implicit hc: HeaderCarrier,
    request: Request[AnyContent]
  ): Future[Unit] = {

    val params = Map(
      "batchId"     -> m.details.flatMap(_.batchId),
      "replyTo"     -> m.details.flatMap(_.replyTo),
      "threadId"    -> m.details.flatMap(_.threadId),
      "enquiryType" -> m.details.flatMap(_.enquiryType),
      "adviser"     -> m.details.flatMap(_.adviser).map(_.pidId),
      "topic"       -> m.details.flatMap(_.topic)
    )

    auditConnector
      .sendEvent(
        DataEvent(
          auditSource = appName,
          auditType = auditType,
          tags = Map("transactionName" -> transactionName),
          detail = Map(
            "messageId"                 -> m._id.toString,
            "formId"                    -> m.details.map(_.formId).getOrElse(""),
            "messageType"               -> m.messageType,
            m.recipient.identifier.name -> m.recipient.identifier.value,
            "originalRequest" -> {
              val requestStr = Json.stringify(request.body.asJson.getOrElse(JsArray(Array.empty[JsValue])))
              if (requestStr.length > auditEventMaxSize) {
                val truncatedRequest = handleBiggerContent(request.body.asJson.getOrElse(JsArray(Array.empty[JsValue])))
                if (truncatedRequest.length > auditEventMaxSize) {
                  "request is too big even without content and sourceData"
                } else {
                  truncatedRequest
                }
              } else {
                requestStr
              }
            }
          ) ++ params.collect { case (k, Some(v)) => k -> v } ++ getOptionalTagValue(NotificationType, m.tags)
        )
      )
      .map {
        case AuditResult.Disabled => logger.warn(s"Audit disabled for create message with id: ${m._id.toString}")
        case AuditResult.Success  => logger.trace("Successful Audit for create message")
        case AuditResult.Failure(msg, _) =>
          logger.error(s"Unable to send an audit event for messageId: ${m._id.toString} : $msg")
      }
  }

  def handleBiggerContent(body: JsValue): String = {
    val sourceDataAlternativeText = "sourceData is removed to reduce size"
    val contentAlternativeText = "content is removed to reduce size"
    val bodyObj = body.as[JsObject]
    Json.stringify((bodyObj.keys.contains("sourceData"), bodyObj.keys.contains("content")) match {
      case (true, true) =>
        bodyObj ++ Json.obj("sourceData" -> sourceDataAlternativeText, "content" -> contentAlternativeText)
      case (false, true) => bodyObj ++ Json.obj("content" -> contentAlternativeText)
      case (true, false) => bodyObj ++ Json.obj("sourceData" -> sourceDataAlternativeText)
      case _             => bodyObj
    })
  }

  import scala.language.postfixOps
  private def getOptionalTagValue(key: String, tags: Option[Map[String, String]]): Map[String, String] =
    (for {
      m <- tags
      v <- m.get(key)
    } yield (key, v)) toMap

  def auditCreateMessageForFailure(transactionName: String)(
    implicit hc: HeaderCarrier,
    request: Request[JsValue]
  ): Future[Unit] =
    auditConnector
      .sendEvent(
        DataEvent(
          auditSource = appName,
          auditType = EventTypes.Failed,
          tags = Map("transactionName" -> transactionName),
          detail = Map(
            "originalRequest" -> {
              val requestStr = Json.stringify(request.body)
              if (requestStr.length > auditEventMaxSize) {
                val truncatedRequest = handleBiggerContent(request.body)
                if (truncatedRequest.length > auditEventMaxSize) {
                  "request is too big even without content and sourceData"
                } else {
                  truncatedRequest
                }
              } else {
                requestStr
              }
            }
          )
        )
      )
      .map {
        case AuditResult.Disabled => logger.warn(s"Audit disabled for request id: ${request.id}")
        case AuditResult.Success  => logger.trace("Successful Audit for failed request")
        case AuditResult.Failure(msg, _) =>
          logger.error(s"Unable to send an audit event for messageId: ${request.id} : $msg")
      }

  def addTaxpayerNameToMessageIfRequired(message: SecureMessage)(implicit hc: HeaderCarrier): Future[SecureMessage] =
    message.alertDetails.recipientName match {
      case Some(_) => Future.successful(message)
      case None =>
        taxpayerNameConnector
          .taxpayerName(SaUtr(message.recipient.identifier.value))
          .flatMap(name =>
            Future.successful(message.copy(alertDetails = message.alertDetails.copy(recipientName = name))))
    }

  // DC-1722
  def ignoreAlertQueueIfGmcAndSa(message: SecureMessage): Future[SecureMessage] = message match {
    case m if isGmc(m) && m.recipient.identifier.name.toLowerCase == "sautr" =>
      Future.successful(m.copy(alertQueue = None))
    case _ => Future.successful(message)
  }

  private def addExtraAlerts(message: SecureMessage): Future[Seq[WorkItem[ExtraAlert]]] = {
    val recipient = message.alertDetails.recipientName
    val templateId = message.alertDetails.templateId
    val iterator = Iterator.from(1)
    Future.sequence(
      extraAlerts.filter(_.mainTemplate == templateId).map { extraAlert =>
        val alertDetails = AlertDetails(extraAlert.extraTemplate, recipient, Map[String, String]())
        val item = ExtraAlert.build(
          message.recipient,
          message._id.toString,
          extraAlert.extraTemplate,
          alertDetails,
          Some(message.externalRef),
          Some(s"${iterator.next}"),
          message.details.map(_.formId)
        )
        extraAlertRepository
          .pushNew(
            item
          )
      }
    )
  }

  def checkAndUpdateMessageBrake(message: SecureMessage): Future[SecureMessage] =
    if (!disableMessageBrake && isGmc(message)) {
      message.details
        .map { d =>
          messageBrakeService.allowlistContains(d.formId) transform {
            case Success(false) => Success(message.copy(status = Deferred, verificationBrake = Some(true)))
            case _              => Success(message)
          }
        }
        .getOrElse(Future.successful(message))
    } else {
      Future.successful(message)
    }

  def countBy(authTaxIds: Set[TaxIdWithName])(
    implicit messageFilter: MessageFilter
  ): Future[MessagesCount] = secureMessageRepository.countBy(authTaxIds)

  def getSecureMessageCount(identifiers: Set[Identifier], tags: Option[List[FilterTag]])(
    implicit ec: ExecutionContext): Future[Count] = secureMessageRepository.getSecureMessageCount(identifiers, tags)

  def findById(id: ObjectId): Future[Option[SecureMessage]] = secureMessageRepository.findById(id)

  def findBy(authTaxIds: Set[TaxIdWithName])(
    implicit messageFilter: MessageFilter,
    ec: ExecutionContext
  ): Future[List[SecureMessage]] = secureMessageRepository.findBy(authTaxIds)

  def getMessages(identifiers: Set[Identifier], tags: Option[List[FilterTag]])(
    implicit ec: ExecutionContext): Future[List[SecureMessage]] =
    secureMessageRepository.getSecureMessages(identifiers, tags)

  def getMessage(id: ObjectId, identifiers: Set[Identifier])(
    implicit ec: ExecutionContext): Future[Either[SecureMessageError, SecureMessage]] =
    secureMessageRepository.getSecureMessage(id, identifiers)

  def addReadTime(id: ObjectId)(implicit ec: ExecutionContext): Future[Either[SecureMessageError, Unit]] =
    secureMessageRepository.addReadTime(id, DateTime.now())
}

case class MessageValidationException(message: String) extends RuntimeException(message)

case class AllowedTagAndAttributes(tag: String, attributes: List[String] = List())
