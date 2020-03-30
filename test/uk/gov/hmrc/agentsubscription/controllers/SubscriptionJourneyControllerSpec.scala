/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.agentsubscription.controllers

import org.mockito.ArgumentMatchers.{ any, eq => eqs }
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.{ Result, Results }
import play.api.test.FakeRequest
import play.api.test.Helpers._
import reactivemongo.bson.BSONDocument
import reactivemongo.core.errors.DatabaseException
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscription.model.AuthProviderId
import uk.gov.hmrc.agentsubscription.model.subscriptionJourney._
import uk.gov.hmrc.agentsubscription.repository.SubscriptionJourneyRepository
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.{ ExecutionContext, Future }

class SubscriptionJourneyControllerSpec extends UnitSpec with Results with MockitoSugar {

  val minimalRecord = SubscriptionJourneyRecord(
    AuthProviderId("cred-1234"),
    None,
    BusinessDetails(BusinessType.LimitedCompany, Utr("12345"), Postcode("BN25GJ"), None, None, None, None, None, None),
    None,
    List.empty,
    mappingComplete = false,
    None,
    None,
    None,
    None,
    None)

  val mockRepo: SubscriptionJourneyRepository = mock[SubscriptionJourneyRepository]
  import play.api.test.Helpers.stubControllerComponents

  val cc = stubControllerComponents()

  val hc = HeaderCarrier()

  implicit val ec = ExecutionContext.Implicits.global

  val controller = new SubscriptionJourneyController(mockRepo, cc)

  "Subscription Journey Controller" should {

    "return OK with record body when record found by auth id" in {
      when(
        mockRepo.findByAuthId(eqs[AuthProviderId](AuthProviderId("minimal")))(any[ExecutionContext]))
        .thenReturn(Future.successful(Some(minimalRecord)))

      //println(s"controller is ${controller.findByAuthId(AuthProviderId(""))(ec)}")
      val result: Result = await(controller.findByAuthId(AuthProviderId("minimal")).apply(FakeRequest()))
      result.header.status shouldBe 200
    }

    "return NoContent when not found by auth id" in {
      when(
        mockRepo.findByAuthId(eqs(AuthProviderId("missing")))(any[ExecutionContext]))
        .thenReturn(Future.successful(None))

      val result: Result = await(controller.findByAuthId(AuthProviderId("missing")).apply(FakeRequest()))
      result.header.status shouldBe 204
    }

    "return OK with record body when record found by utr" in {
      when(
        mockRepo.findByUtr(eqs(Utr("minimal")))(any[ExecutionContext]))
        .thenReturn(Future.successful(Some(minimalRecord)))

      val result: Result = await(controller.findByUtr(Utr("minimal")).apply(FakeRequest()))
      result.header.status shouldBe 200
    }

    "return NoContent when record not found by utr" in {
      when(
        mockRepo.findByUtr(eqs(Utr("missing")))(any[ExecutionContext]))
        .thenReturn(Future.successful(None))

      val result: Result = await(controller.findByUtr(Utr("missing")).apply(FakeRequest()))
      result.header.status shouldBe 204
    }

    "return OK with record body when record found by continueId" in {
      when(
        mockRepo.findByContinueId(eqs("minimal"))(any[ExecutionContext]))
        .thenReturn(Future.successful(Some(minimalRecord)))

      val result: Result = await(controller.findByContinueId("minimal").apply(FakeRequest()))
      result.header.status shouldBe 200
    }

    "return NoContent when record not found by continueId" in {
      when(
        mockRepo.findByContinueId(eqs("missing"))(any[ExecutionContext]))
        .thenReturn(Future.successful(None))

      val result: Result = await(controller.findByContinueId("missing").apply(FakeRequest()))
      result.header.status shouldBe 204
    }

    "return bad request when invalid json provided in createOrUpdate" in {

      val request = FakeRequest().withBody[JsValue](Json.parse("""{"bad":"things"}"""))

      val result: Result = await(controller.createOrUpdate(AuthProviderId("minimal")).apply(request))
      result.header.status shouldBe 400
      contentAsString(result) should include("Invalid SubscriptionJourneyRecord payload")
    }

    "return bad request when provided auth id doesn't match record primary auth id" in {

      val request = FakeRequest().withBody[JsValue](Json.toJson(minimalRecord))

      val result: Result = await(controller.createOrUpdate(AuthProviderId("minimal")).apply(request))
      result.header.status shouldBe 400
      contentAsString(result) shouldBe "Auth ids in request URL and body do not match"
    }

    "return bad request when trying to add duplicate mapping" in {

      val recordWithDupes = minimalRecord.copy(
        userMappings = List(
          UserMapping(AuthProviderId("xxx"), None, List.empty, 0, ""),
          UserMapping(AuthProviderId("xxx"), None, List.empty, 0, "")))

      val request = FakeRequest().withBody[JsValue](Json.toJson(recordWithDupes))

      val result: Result = await(controller.createOrUpdate(AuthProviderId("cred-1234")).apply(request))
      result.header.status shouldBe 400
      contentAsString(result) shouldBe "Duplicate mapped auth ids in request body"
    }

    "return no content when a successful update has been done" in {
      when(
        mockRepo.upsert(any[AuthProviderId], any[SubscriptionJourneyRecord])(any[ExecutionContext]))
        .thenReturn(Future.successful(()))

      val request = FakeRequest().withBody[JsValue](Json.toJson(minimalRecord))

      val result: Result = await(controller.createOrUpdate(AuthProviderId("cred-1234")).apply(request))
      result.header.status shouldBe 204
    }

    "return conflict when there is already an existing record" in {
      when(
        mockRepo.upsert(any[AuthProviderId], any[SubscriptionJourneyRecord])(any[ExecutionContext]))
        .thenReturn(Future.failed(new DatabaseException {
          override def originalDocument: Option[BSONDocument] = None
          override def code: Option[Int] = Some(11000)
          override def message: String = "duplicate exception"
        }))

      val request = FakeRequest().withBody[JsValue](Json.toJson(minimalRecord))

      val result: Result = await(controller.createOrUpdate(AuthProviderId("cred-1234")).apply(request))
      result.header.status shouldBe 409
    }
  }

}
