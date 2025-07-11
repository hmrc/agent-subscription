/*
 * Copyright 2024 HM Revenue & Customs
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

import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.{eq => eqs}
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents
import play.api.mvc.Result
import play.api.mvc.Results
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscription.model.subscriptionJourney._
import uk.gov.hmrc.agentsubscription.model.AuthProviderId
import uk.gov.hmrc.agentsubscription.model.VerifiedEmails
import uk.gov.hmrc.agentsubscription.repository.RecordUpdated
import uk.gov.hmrc.agentsubscription.repository.SubscriptionJourneyRepository
import uk.gov.hmrc.agentsubscription.support.UnitSpec
import uk.gov.hmrc.crypto.Decrypter
import uk.gov.hmrc.crypto.Encrypter
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class SubscriptionJourneyControllerSpec
extends UnitSpec
with Results
with MockitoSugar {

  val minimalBusinessDetails: BusinessDetails = BusinessDetails(
    businessType = BusinessType.LimitedCompany,
    utr = "12345",
    postcode = "BN25GJ",
    registration = None,
    nino = None,
    companyRegistrationNumber = None,
    dateOfBirth = None,
    registeredForVat = None,
    vatDetails = None
  )

  val minimalRecord: SubscriptionJourneyRecord = SubscriptionJourneyRecord(
    authProviderId = AuthProviderId("cred-1234"),
    continueId = None,
    businessDetails = minimalBusinessDetails,
    amlsData = None,
    userMappings = List.empty,
    mappingComplete = false,
    cleanCredsAuthProviderId = None,
    lastModifiedDate = None,
    contactEmailData = None,
    contactTradingNameData = None,
    contactTradingAddressData = None,
    contactTelephoneData = None,
    verifiedEmails = VerifiedEmails(Set.empty)
  )

  val recordWithData: SubscriptionJourneyRecord = minimalRecord.copy(
    continueId = Some("XXX")
  )

  val mockRepo: SubscriptionJourneyRepository = mock[SubscriptionJourneyRepository]
  import play.api.test.Helpers.stubControllerComponents

  val cc: ControllerComponents = stubControllerComponents()

  val hc: HeaderCarrier = HeaderCarrier()

  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global
  implicit val crypto: Encrypter
    with Decrypter = aesCrypto

  val controller = new SubscriptionJourneyController(mockRepo, cc)

  "Subscription Journey Controller" should {

    "return OK with record body when record found by auth id" in {
      when(mockRepo.findByAuthId(eqs[AuthProviderId](AuthProviderId("minimal"))))
        .thenReturn(Future.successful(Some(minimalRecord)))

      val result: Result = await(controller.findByAuthId(AuthProviderId("minimal")).apply(FakeRequest()))
      result.header.status shouldBe 200
    }

    "return NoContent when not found by auth id" in {
      when(mockRepo.findByAuthId(eqs(AuthProviderId("missing"))))
        .thenReturn(Future.successful(None))

      val result: Result = await(controller.findByAuthId(AuthProviderId("missing")).apply(FakeRequest()))
      result.header.status shouldBe 204
    }

    "return OK with record body when record found by utr" in {
      when(mockRepo.findByUtr(eqs("minimal")))
        .thenReturn(Future.successful(Some(minimalRecord)))

      val result: Result = await(controller.findByUtr(Utr("minimal")).apply(FakeRequest()))
      result.header.status shouldBe 200
    }

    "return NoContent when record not found by utr" in {
      when(mockRepo.findByUtr(eqs("missing")))
        .thenReturn(Future.successful(None))

      val result: Result = await(controller.findByUtr(Utr("missing")).apply(FakeRequest()))
      result.header.status shouldBe 204
    }

    "return OK with record body when record found by continueId" in {
      when(mockRepo.findByContinueId(eqs("minimal")))
        .thenReturn(Future.successful(Some(minimalRecord)))

      val result: Result = await(controller.findByContinueId("minimal").apply(FakeRequest()))
      result.header.status shouldBe 200
    }

    "return NoContent when record not found by continueId" in {
      when(mockRepo.findByContinueId(eqs("missing")))
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
          UserMapping(
            AuthProviderId("xxx"),
            None,
            List.empty,
            0,
            ""
          ),
          UserMapping(
            AuthProviderId("xxx"),
            None,
            List.empty,
            0,
            ""
          )
        )
      )

      val request = FakeRequest().withBody[JsValue](Json.toJson(recordWithDupes))

      val result: Result = await(controller.createOrUpdate(AuthProviderId("cred-1234")).apply(request))
      result.header.status shouldBe 400
      contentAsString(result) shouldBe "Duplicate mapped auth ids in request body"
    }

    "return no content when a successful create has been done" in {
      when(mockRepo.findByUtr(anyString()))
        .thenReturn(Future.successful(None))
      when(mockRepo.upsert(any[AuthProviderId], any[SubscriptionJourneyRecord]))
        .thenReturn(Future.successful(Some(RecordUpdated)))

      val request = FakeRequest().withBody[JsValue](Json.toJson(minimalRecord))

      val result: Result = await(controller.createOrUpdate(AuthProviderId("cred-1234")).apply(request))
      result.header.status shouldBe 204
    }

    "return no content when a successful update has been done" in {
      when(mockRepo.findByUtr(anyString()))
        .thenReturn(Future.successful(Some(minimalRecord)))
      when(mockRepo.upsert(any[AuthProviderId], eqs(recordWithData)))
        .thenReturn(Future.successful(Some(RecordUpdated)))

      val request = FakeRequest().withBody[JsValue](Json.toJson(recordWithData))

      val result: Result = await(controller.createOrUpdate(AuthProviderId("cred-1234")).apply(request))
      result.header.status shouldBe 204
    }

    "return 200 with authProviderID updated when there is already an existing record" in {
      val existingRecord = recordWithData // The existing record in the repo
      val newAuthProviderId = AuthProviderId("cred-new")
      val newRecord = minimalRecord.copy(authProviderId = newAuthProviderId) // The new record (that we're trying to store)
      val updatedExistingRecord = existingRecord.copy(authProviderId = newAuthProviderId) // The existing record, modified with the new record's authId

      when(mockRepo.updateOnUtr(
        any[String],
        any[AuthProviderId],
        any[BusinessDetails],
        any[Option[AuthProviderId]]
      ))
        .thenReturn(Future.successful(Some(updatedExistingRecord)))

      when(mockRepo.findByUtr(any[String]))
        .thenReturn(Future.successful(Some(existingRecord)))

      val request = FakeRequest().withBody[JsValue](Json.toJson(newRecord))

      val result: Result = await(controller.createOrUpdate(newAuthProviderId).apply(request))
      result.header.status shouldBe 200
      (contentAsJson(result) \ "authProviderId").as[String] shouldBe updatedExistingRecord.authProviderId.id
    }

    "return 200 with authProviderID, cleanCredsAuthProviderID and businessDetails updated when there is already an existing record" in {
      val existingRecord = minimalRecord.copy(cleanCredsAuthProviderId = Some(AuthProviderId("existing-clean-creds"))) // The existing record in the repo
      val newAuthProviderId = AuthProviderId("cred-new-clean")
      val newBusinessDetails = BusinessDetails(
        businessType = BusinessType.LimitedCompany,
        utr = "12345",
        postcode = "BN65GJ",
        registration = None,
        nino = None,
        companyRegistrationNumber = None,
        dateOfBirth = None,
        registeredForVat = None,
        vatDetails = None
      )
      val newRecord = minimalRecord.copy(
        authProviderId = newAuthProviderId,
        cleanCredsAuthProviderId = Some(newAuthProviderId),
        businessDetails = newBusinessDetails
      ) // The new record (that we're trying to store)
      val updatedExistingRecord = existingRecord.copy(
        authProviderId = newAuthProviderId,
        cleanCredsAuthProviderId = Some(newAuthProviderId),
        businessDetails = newBusinessDetails
      ) // The existing record, modified with the new record's authId and clean credID

      when(mockRepo.updateOnUtr(
        any[String],
        any[AuthProviderId],
        any[BusinessDetails],
        any[Option[AuthProviderId]]
      ))
        .thenReturn(Future.successful(Some(updatedExistingRecord)))

      when(mockRepo.findByUtr(any[String]))
        .thenReturn(Future.successful(Some(existingRecord)))

      val request = FakeRequest().withBody[JsValue](Json.toJson(newRecord))

      val result: Result = await(controller.createOrUpdate(newAuthProviderId).apply(request))
      result.header.status shouldBe 200
      (contentAsJson(result) \ "authProviderId").as[String] shouldBe updatedExistingRecord.authProviderId.id
      (contentAsJson(result) \ "cleanCredsAuthProviderId")
        .asOpt[AuthProviderId] shouldBe updatedExistingRecord.cleanCredsAuthProviderId
      (contentAsJson(result) \ "businessDetails")
        .as[BusinessDetails] shouldBe updatedExistingRecord.businessDetails
    }

    "throw IllegalStateException if there's a conflict updating existing record" in {
      val existingRecord = minimalRecord // The existing record in the repo
      val newAuthProviderId = AuthProviderId("cred-new")
      val newRecord = minimalRecord.copy(authProviderId = newAuthProviderId) // The new record (that we're trying to store)

      when(mockRepo.updateOnUtr(
        any[String],
        any[AuthProviderId],
        any[BusinessDetails],
        any[Option[AuthProviderId]]
      ))
        .thenReturn(Future.successful((None)))

      when(mockRepo.findByUtr(any[String]))
        .thenReturn(Future.successful(Some(existingRecord)))

      val request = FakeRequest().withBody[JsValue](Json.toJson(newRecord))

      intercept[IllegalStateException] {
        await(controller.createOrUpdate(newAuthProviderId).apply(request))
      }

    }
  }

}
