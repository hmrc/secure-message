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

import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.testkit.NoMaterializer

import java.time.LocalDate
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.{ any, eq => eqTo }
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.http.ContentTypes.JSON
import play.api.http.HeaderNames.CONTENT_TYPE
import play.api.http.Status._
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.Result
import play.api.test.Helpers.{ contentAsJson, contentAsString, defaultAwaitTimeout, status }
import play.api.test.{ FakeHeaders, FakeRequest, Helpers }
import uk.gov.hmrc.http.HttpVerbs.POST
import uk.gov.hmrc.securemessage.helpers.Resources
import uk.gov.hmrc.securemessage.models.v4.{ BrakeBatch, BrakeBatchApproval, BrakeBatchDetails, BrakeBatchMessage, SecureMessage }
import uk.gov.hmrc.securemessage.repository.{ ExtraAlertRepository, Instances, SecureMessageRepository }
import uk.gov.hmrc.securemessage.services.MessageBrakeService
import uk.gov.hmrc.securemessage.models.v4.Allowlist
import uk.gov.hmrc.securemessage.models.AllowlistUpdateRequest

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ ExecutionContext, Future }

class AdminControllerSpec extends PlaySpec with ScalaFutures with MockitoSugar {
  val mockExtraAlertRepository = mock[ExtraAlertRepository]
  val mockMessageRepository = mock[SecureMessageRepository]
  val mockMessageBrakeService = mock[MessageBrakeService]
  val mockInstances = mock[Instances]
  val controller = new AdminController(mockInstances, Helpers.stubControllerComponents())
  val fakeRequest = FakeRequest()
  implicit val mat: Materializer = NoMaterializer

  "AdminController" must {
    "pull brake batches" in {
      val brakeBatchDetails = BrakeBatchDetails("batchId", "formId", LocalDate.now(), "templateId", 1)
      when(mockMessageRepository.pullBrakeBatchDetails()).thenReturn(Future.successful(List(brakeBatchDetails)))
      when(mockInstances.messageRepository).thenReturn(mockMessageRepository)

      val response: Future[Result] = controller.getGMCBrakeBatches()(fakeRequest)
      status(response) mustBe OK
      contentAsJson(response) mustBe Json.parse(s"""[{"batchId":"batchId","formId":"formId","issueDate":"${LocalDate
          .now()}","templateId":"templateId","count":1}]""")
    }

    "accept brake batch" in {
      val brakeBatchapproval = BrakeBatchApproval("batchId", "formId", LocalDate.now(), "templateId", "reason")
      val fakeRequest: FakeRequest[JsValue] = FakeRequest(
        method = POST,
        uri = routes.AdminController.acceptBrakeBatch().url,
        headers = FakeHeaders(Seq(CONTENT_TYPE -> JSON)),
        body = Json.toJson(brakeBatchapproval)
      )

      when(mockMessageRepository.brakeBatchAccepted(ArgumentMatchers.eq(brakeBatchapproval)))
        .thenReturn(Future.successful(true))
      when(mockExtraAlertRepository.brakeBatchAccepted(ArgumentMatchers.eq(brakeBatchapproval))(any[ExecutionContext]))
        .thenReturn(Future.successful(true))

      when(mockInstances.messageRepository).thenReturn(mockMessageRepository)
      when(mockInstances.extraAlertRepository).thenReturn(mockExtraAlertRepository)

      val response = controller.acceptBrakeBatch()(fakeRequest)
      status(response) mustBe OK
    }

    "reject brake batch" in {
      val brakeBatchapproval = BrakeBatchApproval("batchId", "formId", LocalDate.now(), "templateId", "reason")
      val fakeRequest: FakeRequest[JsValue] = FakeRequest(
        method = POST,
        uri = routes.AdminController.rejectBrakeBatch().url,
        headers = FakeHeaders(Seq(CONTENT_TYPE -> JSON)),
        body = Json.toJson(brakeBatchapproval)
      )

      when(mockMessageRepository.brakeBatchRejected(ArgumentMatchers.eq(brakeBatchapproval)))
        .thenReturn(Future.successful(true))
      when(mockExtraAlertRepository.brakeBatchRejected(ArgumentMatchers.eq(brakeBatchapproval))(any[ExecutionContext]))
        .thenReturn(Future.successful(true))

      when(mockInstances.messageRepository).thenReturn(mockMessageRepository)
      when(mockInstances.extraAlertRepository).thenReturn(mockExtraAlertRepository)

      val response = controller.rejectBrakeBatch()(fakeRequest)
      status(response) mustBe OK
    }

    "random brake batch message" in {
      val brakeBatch = BrakeBatch("batchId", "formId", LocalDate.now(), "templateId")
      val fakeRequest: FakeRequest[JsValue] = FakeRequest(
        method = POST,
        uri = routes.AdminController.randomBrakeBatchMessage().url,
        headers = FakeHeaders(Seq(CONTENT_TYPE -> JSON)),
        body = Json.toJson(brakeBatch)
      )
      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]

      when(mockMessageRepository.brakeBatchMessageRandom(ArgumentMatchers.eq(brakeBatch)))
        .thenReturn(Future.successful(Some(BrakeBatchMessage(message))))

      when(mockInstances.messageRepository).thenReturn(mockMessageRepository)

      val response = controller.randomBrakeBatchMessage()(fakeRequest)
      status(response) mustBe OK
      contentAsJson(response) mustBe Json.parse(
        """{"subject":"Reminder to file a Self Assessment return","welshSubject":"Nodyn atgoffa i ffeilio ffurflen Hunanasesiad","content":"Message content - 4254101384174917141","welshContent":"Cynnwys - 4254101384174917141","externalRefId":"abcd1234","messageType":"sdAlertMessage","issueDate":"2020-05-04","taxIdentifierName":"HMRC-OBTDS-ORG"}"""
      )
    }

    "get gmc allow list" must {

      "return allow list held in the database or cache" in {
        val allowlist = Allowlist(List("TEST1", "TEST2", "TEST3", "TEST4"))

        when(mockInstances.messageBrakeService).thenReturn(mockMessageBrakeService)

        when(mockMessageBrakeService.getOrInitialiseCachedAllowlist()(any[ExecutionContext]))
          .thenReturn(Future.successful(Some(allowlist)))

        val fakeRequest = FakeRequest(Helpers.GET, routes.AdminController.getGmcAllowlist().url)
        val result: Future[Result] = controller.getGmcAllowlist()(fakeRequest)
        status(result) mustBe OK
        contentAsString(result) mustBe """{"formIdList":["TEST1","TEST2","TEST3","TEST4"]}"""
      }

      "return internal server error if the cached allowlist expired and the database cannot be reached" in {
        when(mockInstances.messageBrakeService).thenReturn(mockMessageBrakeService)
        when(mockMessageBrakeService.getOrInitialiseCachedAllowlist()(any[ExecutionContext]))
          .thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(Helpers.GET, routes.AdminController.getGmcAllowlist().url)
        val result = controller.getGmcAllowlist()(fakeRequest)
        status(result) mustBe INTERNAL_SERVER_ERROR
        contentAsString(result) mustBe """{"error":"No allowlist present"}"""
      }
    }

    "add a form id to the allow list" must {

      "return allow list held in the database or cache" in {
        val allowlistUpdateRequest = AllowlistUpdateRequest("TEST10", "some reason to add this form id")
        val allowlist = Allowlist(List("TEST1", "TEST2", "TEST3", "TEST4", "TEST10"))

        when(mockInstances.messageBrakeService).thenReturn(mockMessageBrakeService)

        when(
          mockMessageBrakeService.addFormIdToAllowlist(eqTo(allowlistUpdateRequest))(
            any[ExecutionContext]()
          )
        )
          .thenReturn(Future.successful(Some(allowlist)))

        val fakeRequest = FakeRequest(
          Helpers.POST,
          routes.AdminController.addFormIdToGmcAllowlist().url,
          FakeHeaders(),
          Json.toJson(allowlistUpdateRequest)
        )
        val result = controller.addFormIdToGmcAllowlist()(fakeRequest)
        status(result) mustBe CREATED
        contentAsString(result) mustBe """{"formIdList":["TEST1","TEST2","TEST3","TEST4","TEST10"]}"""
      }

      "return internal server error if the cached allowlist expired and the database cannot be reached" in {
        val allowlistUpdateRequest = AllowlistUpdateRequest("TEST10", "some reason to add this form id")

        when(mockInstances.messageBrakeService).thenReturn(mockMessageBrakeService)

        when(
          mockMessageBrakeService.addFormIdToAllowlist(eqTo(allowlistUpdateRequest))(
            any[ExecutionContext]()
          )
        )
          .thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(
          Helpers.POST,
          routes.AdminController.addFormIdToGmcAllowlist().url,
          FakeHeaders(),
          Json.toJson(allowlistUpdateRequest)
        )
        val result = controller.addFormIdToGmcAllowlist()(fakeRequest)
        status(result) mustBe INTERNAL_SERVER_ERROR
        contentAsString(result) mustBe """{"error":"No allowlist present"}"""
      }
    }

    "remove a form id from the allow list" must {

      "return allow list held in the database or cache" in {
        val allowlistUpdateRequest = AllowlistUpdateRequest("TEST2", "some reason to remove this form id")
        val allowlist = Allowlist(List("TEST1", "TEST3", "TEST4"))

        when(mockInstances.messageBrakeService).thenReturn(mockMessageBrakeService)

        when(
          mockMessageBrakeService.deleteFormIdFromAllowlist(eqTo(allowlistUpdateRequest))(
            any[ExecutionContext]()
          )
        ).thenReturn(Future.successful(Some(allowlist)))

        val fakeRequest = FakeRequest(
          Helpers.POST,
          routes.AdminController.deleteFormIdFromGmcAllowlist().url,
          FakeHeaders(),
          Json.toJson(allowlistUpdateRequest)
        )
        val result = controller.deleteFormIdFromGmcAllowlist()(fakeRequest)
        status(result) mustBe OK
        contentAsString(result) mustBe """{"formIdList":["TEST1","TEST3","TEST4"]}"""
      }

      "return internal server error if the cached allowlist expired and the database cannot be reached" in {
        val allowlistUpdateRequest = AllowlistUpdateRequest("TEST2", "some reason to remove this form id")

        when(mockInstances.messageBrakeService).thenReturn(mockMessageBrakeService)

        when(
          mockMessageBrakeService.deleteFormIdFromAllowlist(eqTo(allowlistUpdateRequest))(
            any[ExecutionContext]()
          )
        ).thenReturn(Future.successful(None))

        val fakeRequest = FakeRequest(
          Helpers.POST,
          routes.AdminController.deleteFormIdFromGmcAllowlist().url,
          FakeHeaders(),
          Json.toJson(allowlistUpdateRequest)
        )
        val result = controller.deleteFormIdFromGmcAllowlist()(fakeRequest)
        status(result) mustBe INTERNAL_SERVER_ERROR
        contentAsString(result) mustBe """{"error":"No allowlist present"}"""
      }
    }
  }
}
