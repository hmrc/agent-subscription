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

import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.test.Helpers._
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscription.config.AppConfig
import uk.gov.hmrc.agentsubscription.model._
import uk.gov.hmrc.agentsubscription.model.subscriptionJourney._
import uk.gov.hmrc.agentsubscription.repository.SubscriptionJourneyRepository
import uk.gov.hmrc.agentsubscription.stubs.AuthStub
import uk.gov.hmrc.agentsubscription.support.BaseISpec
import uk.gov.hmrc.agentsubscription.support.Resource
import uk.gov.hmrc.mongo.test.CleanMongoCollectionSupport

class SubscriptionJourneyControllerISpec
extends BaseISpec
with AuthStub
with CleanMongoCollectionSupport {

  implicit lazy val appConfig: AppConfig = app.injector.instanceOf[AppConfig]
  implicit val ws: WSClient = app.injector.instanceOf[WSClient]

  val repo: SubscriptionJourneyRepository = app.injector.instanceOf[SubscriptionJourneyRepository]

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

  val validUtr = Utr("2000000000")
  val otherUtr = Utr("0123456789")
  val registrationName = "My Agency"
  val businessAddress = BusinessAddress(
    "AddressLine1 A",
    Some("AddressLine2 A"),
    Some("AddressLine3 A"),
    Some("AddressLine4 A"),
    Some("AA11AA"),
    "GB"
  )

  private val subscriptionJourneyRecord = SubscriptionJourneyRecord(
    AuthProviderId("auth-id"),
    continueId = Some("XXX"),
    businessDetails = BusinessDetails(
      businessType = BusinessType.SoleTrader,
      utr = validUtr.value,
      postcode = "bn12 1hn",
      nino = Some("AE123456C")
    ),
    amlsData = None,
    userMappings = List(),
    mappingComplete = false,
    cleanCredsAuthProviderId = None,
    lastModifiedDate = None,
    contactEmailData = Some(ContactEmailData(useBusinessEmail = true, Some("email@email.com"))),
    contactTradingNameData = Some(ContactTradingNameData(hasTradingName = true, Some("My Trading Name"))),
    contactTradingAddressData = Some(ContactTradingAddressData(useBusinessAddress = true, Some(businessAddress))),
    contactTelephoneData = Some(ContactTelephoneData(useBusinessTelephone = true, Some("01273111111"))),
    verifiedEmails = VerifiedEmails(Set.empty)
  )

  "Subscription Journey Controller" should {

    "return OK with record body when record found by auth id" in {
      givenAuthorised()

      await(repo.upsert(subscriptionJourneyRecord.authProviderId, subscriptionJourneyRecord))

      val response = new Resource("/agent-subscription/subscription/journey/id/auth-id", port).get()
      response.status shouldBe 200

      await(repo.delete(subscriptionJourneyRecord.businessDetails.utr))
    }

    "return NoContent when not found by auth id" in {
      givenAuthorised()
      val response = new Resource("/agent-subscription/subscription/journey/id/missing", port).get()
      response.status shouldBe 204

      await(repo.delete(subscriptionJourneyRecord.businessDetails.utr))
    }

    "return OK with record body when record found by utr" in {
      givenAuthorised()
      await(repo.upsert(subscriptionJourneyRecord.authProviderId, subscriptionJourneyRecord))
      val response = new Resource(s"/agent-subscription/subscription/journey/utr/${validUtr.value}", port).get()
      response.status shouldBe 200

      await(repo.delete(subscriptionJourneyRecord.businessDetails.utr))
    }

    "return NoContent when record not found by utr" in {
      givenAuthorised()
      val response = new Resource("/agent-subscription/subscription/journey/utr/missing", port).get()
      response.status shouldBe 204
    }

    "return OK with record body when record found by continueId" in {
      givenAuthorised()
      await(repo.upsert(subscriptionJourneyRecord.authProviderId, subscriptionJourneyRecord))
      val response = new Resource(s"/agent-subscription/subscription/journey/continueId/${subscriptionJourneyRecord.continueId.get}", port).get()
      response.status shouldBe 200

      await(repo.delete(subscriptionJourneyRecord.businessDetails.utr))
    }

    "return NoContent when record not found by continueId" in {
      givenAuthorised()
      val response = new Resource("/agent-subscription/subscription/journey/continueId/missing", port).get()
      response.status shouldBe 204
    }

    "return bad request when invalid json provided in createOrUpdate" in {
      givenAuthorised()
      await(repo.upsert(subscriptionJourneyRecord.authProviderId, subscriptionJourneyRecord))
      val response = new Resource("/agent-subscription/subscription/journey/primaryId/auth-id", port).postAsJson(
        "invalid json"
      )
      response.status shouldBe 400

      await(repo.delete(subscriptionJourneyRecord.businessDetails.utr))
    }

    "return bad request when provided auth id doesn't match record primary auth id" in {
      givenAuthorised()
      await(repo.upsert(subscriptionJourneyRecord.authProviderId, subscriptionJourneyRecord))
      val response = new Resource("/agent-subscription/subscription/journey/primaryId/missing", port).postAsJson(
        Json.toJson(subscriptionJourneyRecord).toString()
      )
      response.status shouldBe 400

      await(repo.delete(subscriptionJourneyRecord.businessDetails.utr))
    }

    "return no content when a successful create has been done" in {
      givenAuthorised()
      val response = new Resource(s"/agent-subscription/subscription/journey/primaryId/${subscriptionJourneyRecord.authProviderId.id}", port).postAsJson(
        Json.toJson(subscriptionJourneyRecord).toString()
      )
      response.status shouldBe 204

      await(repo.delete(subscriptionJourneyRecord.businessDetails.utr))
    }

    "return no content when a successful update has been done" in {
      givenAuthorised()
      await(repo.upsert(subscriptionJourneyRecord.authProviderId, subscriptionJourneyRecord))
      val response = new Resource(s"/agent-subscription/subscription/journey/primaryId/${subscriptionJourneyRecord.authProviderId.id}", port).postAsJson(
        Json.toJson(subscriptionJourneyRecord).toString()
      )
      response.status shouldBe 204

      await(repo.delete(subscriptionJourneyRecord.businessDetails.utr))
    }

    "return 200 with authProviderID updated when there is already an existing record" in {
      givenAuthorised()
      await(repo.upsert(subscriptionJourneyRecord.authProviderId, subscriptionJourneyRecord))
      val response = new Resource("/agent-subscription/subscription/journey/primaryId/auth-id2", port).postAsJson(
        Json.toJson(subscriptionJourneyRecord.copy(authProviderId = AuthProviderId("auth-id2"))).toString()
      )
      response.status shouldBe 200

      await(repo.delete(subscriptionJourneyRecord.businessDetails.utr))
    }

    "return 200 with authProviderID, cleanCredsAuthProviderID updated when there is already an existing record" in {
      givenAuthorised()
      await(repo.upsert(subscriptionJourneyRecord.authProviderId, subscriptionJourneyRecord))

      val newSubscriptionJourneyRecord = subscriptionJourneyRecord.copy(
        authProviderId = AuthProviderId("auth-id2"),
        cleanCredsAuthProviderId = Some(AuthProviderId("auth-id-clean-creds")))
      val response = new Resource("/agent-subscription/subscription/journey/primaryId/auth-id2", port).postAsJson(
        Json.toJson(newSubscriptionJourneyRecord).toString()
      )
      response.status shouldBe 200

      await(repo.delete(subscriptionJourneyRecord.businessDetails.utr))
    }

    "throw IllegalStateException if there's a conflict updating existing record" in {}
  }

}
