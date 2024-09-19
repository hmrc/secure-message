///*
// * Copyright 2023 HM Revenue & Customs
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package uk.gov.hmrc.securemessage.controllers
//
//import org.apache.pekko.stream.Materializer
//import org.apache.pekko.stream.testkit.NoMaterializer
//import java.time.LocalDate
//import org.mockito.ArgumentMatchers
//import org.mockito.ArgumentMatchers.any
//import org.mockito.Mockito.when
//import org.scalatest.concurrent.ScalaFutures
//import org.scalatestplus.mockito.MockitoSugar
//import org.scalatestplus.play.PlaySpec
//import play.api.http.ContentTypes.JSON
//import play.api.http.HeaderNames.CONTENT_TYPE
//import play.api.http.Status.OK
//import play.api.libs.json.{ JsValue, Json }
//import play.api.test.Helpers.{ contentAsJson, defaultAwaitTimeout, status }
//import play.api.test.{ FakeHeaders, FakeRequest, Helpers }
//import uk.gov.hmrc.http.HttpVerbs.POST
//import uk.gov.hmrc.securemessage.helpers.Resources
//import uk.gov.hmrc.securemessage.models.v4.{ BrakeBatch, BrakeBatchApproval, BrakeBatchDetails, BrakeBatchMessage, SecureMessage }
//import uk.gov.hmrc.securemessage.repository.{ ExtraAlertRepository, Instances, SecureMessageRepository }
//
//import scala.concurrent.ExecutionContext.Implicits.global
//import scala.concurrent.{ ExecutionContext, Future }
//
//class AdminControllerSpec extends PlaySpec with ScalaFutures with MockitoSugar {
//  val mockExtraAlertRepository = mock[ExtraAlertRepository]
//  val mockMessageRepository = mock[SecureMessageRepository]
//  val mockInstances = mock[Instances]
//  val controller = new AdminController(mockInstances, Helpers.stubControllerComponents())
//  val fakeRequest = FakeRequest()
//  implicit val mat: Materializer = NoMaterializer
//
//  "AdminController" must {
//    "pull brake batches" in {
//      val brakeBatchDetails = BrakeBatchDetails("batchId", "formId", LocalDate.now(), "templateId", 1)
//      when(mockMessageRepository.pullBrakeBatchDetails()).thenReturn(Future.successful(List(brakeBatchDetails)))
//      when(mockInstances.messageRepository).thenReturn(mockMessageRepository)
//
//      val response = controller.getGMCBrakeBatches()(fakeRequest)
//      status(response) mustBe OK
//      contentAsJson(response) mustBe Json.parse(s"""[{"batchId":"batchId","formId":"formId","issueDate":"${LocalDate
//          .now()}","templateId":"templateId","count":1}]""")
//    }
//
//    "accept brake batch" in {
//      val brakeBatchapproval = BrakeBatchApproval("batchId", "formId", LocalDate.now(), "templateId", "reason")
//      val fakeRequest: FakeRequest[JsValue] = FakeRequest(
//        method = POST,
//        uri = routes.AdminController.acceptBrakeBatch().url,
//        headers = FakeHeaders(Seq(CONTENT_TYPE -> JSON)),
//        body = Json.toJson(brakeBatchapproval)
//      )
//
//      when(mockMessageRepository.brakeBatchAccepted(ArgumentMatchers.eq(brakeBatchapproval)))
//        .thenReturn(Future.successful(true))
//      when(mockExtraAlertRepository.brakeBatchAccepted(ArgumentMatchers.eq(brakeBatchapproval))(any[ExecutionContext]))
//        .thenReturn(Future.successful(true))
//
//      when(mockInstances.messageRepository).thenReturn(mockMessageRepository)
//      when(mockInstances.extraAlertRepository).thenReturn(mockExtraAlertRepository)
//
//      val response = controller.acceptBrakeBatch()(fakeRequest)
//      status(response) mustBe OK
//    }
//
//    "reject brake batch" in {
//      val brakeBatchapproval = BrakeBatchApproval("batchId", "formId", LocalDate.now(), "templateId", "reason")
//      val fakeRequest: FakeRequest[JsValue] = FakeRequest(
//        method = POST,
//        uri = routes.AdminController.rejectBrakeBatch().url,
//        headers = FakeHeaders(Seq(CONTENT_TYPE -> JSON)),
//        body = Json.toJson(brakeBatchapproval)
//      )
//
//      when(mockMessageRepository.brakeBatchRejected(ArgumentMatchers.eq(brakeBatchapproval)))
//        .thenReturn(Future.successful(true))
//      when(mockExtraAlertRepository.brakeBatchRejected(ArgumentMatchers.eq(brakeBatchapproval))(any[ExecutionContext]))
//        .thenReturn(Future.successful(true))
//
//      when(mockInstances.messageRepository).thenReturn(mockMessageRepository)
//      when(mockInstances.extraAlertRepository).thenReturn(mockExtraAlertRepository)
//
//      val response = controller.rejectBrakeBatch()(fakeRequest)
//      status(response) mustBe OK
//    }
//
//    "random brake batch message" in {
//      val brakeBatch = BrakeBatch("batchId", "formId", LocalDate.now(), "templateId")
//      val fakeRequest: FakeRequest[JsValue] = FakeRequest(
//        method = POST,
//        uri = routes.AdminController.randomBrakeBatchMessage().url,
//        headers = FakeHeaders(Seq(CONTENT_TYPE -> JSON)),
//        body = Json.toJson(brakeBatch)
//      )
//      val message: SecureMessage = Resources.readJson("model/core/v4/valid_message.json").as[SecureMessage]
//
//      when(mockMessageRepository.brakeBatchMessageRandom(ArgumentMatchers.eq(brakeBatch)))
//        .thenReturn(Future.successful(Some(BrakeBatchMessage(message))))
//
//      when(mockInstances.messageRepository).thenReturn(mockMessageRepository)
//
//      val response = controller.randomBrakeBatchMessage()(fakeRequest)
//      status(response) mustBe OK
//      contentAsJson(response) mustBe Json.parse(
//        """{"subject":"Reminder to file a Self Assessment return","welshSubject":"Nodyn atgoffa i ffeilio ffurflen Hunanasesiad","content":"Message content - 4254101384174917141","welshContent":"Cynnwys - 4254101384174917141","externalRefId":"abcd1234","messageType":"sdAlertMessage","issueDate":"2020-05-04","taxIdentifierName":"HMRC-OBTDS-ORG"}"""
//      )
//    }
//  }
//}
