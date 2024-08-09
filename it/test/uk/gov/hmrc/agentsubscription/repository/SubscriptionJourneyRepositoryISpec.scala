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

package uk.gov.hmrc.agentsubscription.repository

import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.test.Helpers._
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscription.config.AppConfig
import uk.gov.hmrc.agentsubscription.model._
import uk.gov.hmrc.agentsubscription.model.subscriptionJourney._
import uk.gov.hmrc.agentsubscription.support.UnitSpec
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global

class SubscriptionJourneyRepositoryISpec
    extends UnitSpec with GuiceOneAppPerSuite with DefaultPlayMongoRepositorySupport[SubscriptionJourneyRecord] {

  implicit lazy val appConfig: AppConfig = app.injector.instanceOf[AppConfig]

  override def checkTtlIndex = false // temporary until we make last modified date field not optional

  override protected lazy val repository = new SubscriptionJourneyRepositoryImpl(mongoComponent, aesCrypto)

  val validUtr = Utr("2000000000")
  val otherUtr = Utr("0123456789")
  val registrationName = "My Agency"
  val businessAddress =
    BusinessAddress(
      "AddressLine1 A",
      Some("AddressLine2 A"),
      Some("AddressLine3 A"),
      Some("AddressLine4 A"),
      Some("AA11AA"),
      "GB",
      encrypted = Some(true)
    )
  val registration = Registration(
    Some(registrationName),
    isSubscribedToAgentServices = false,
    isSubscribedToETMP = false,
    businessAddress,
    Some("test@gmail.com"),
    Some("01273111111"),
    Some("safeId")
  )

  val amlsDetails = AmlsDetails(
    "supervisory",
    membershipNumber = Some("12345"),
    appliedOn = None,
    membershipExpiresOn = Some(LocalDate.now()),
    amlsSafeId = Some("amlsSafeId"),
    agentBPRSafeId = Some("agentBPRSafeId")
  )

  private val subscriptionJourneyRecord =
    SubscriptionJourneyRecord(
      AuthProviderId("auth-id"),
      continueId = Some("XXX"),
      businessDetails = BusinessDetails(
        businessType = BusinessType.SoleTrader,
        utr = validUtr.value,
        postcode = "bn12 1hn",
        nino = Some("AE123456C"),
        encrypted = Some(true)
      ),
      amlsData = None,
      userMappings = List(),
      mappingComplete = false,
      cleanCredsAuthProviderId = None,
      lastModifiedDate = None,
      contactEmailData =
        Some(ContactEmailData(useBusinessEmail = true, Some("email@email.com"), encrypted = Some(true))),
      contactTradingNameData =
        Some(ContactTradingNameData(hasTradingName = true, Some("My Trading Name"), encrypted = Some(true))),
      contactTradingAddressData = Some(ContactTradingAddressData(useBusinessAddress = true, Some(businessAddress))),
      contactTelephoneData =
        Some(ContactTelephoneData(useBusinessTelephone = true, Some("01273111111"), encrypted = Some(true))),
      verifiedEmails = VerifiedEmails(Set.empty, Some(true))
    )

  "SubscriptionJourneyRepository" should {

    "create a SubscriptionJourney record" in {
      await(repository.upsert(subscriptionJourneyRecord.authProviderId, subscriptionJourneyRecord))

      await(repository.findByAuthId(AuthProviderId("auth-id"))).head shouldBe subscriptionJourneyRecord
    }

    "find a SubscriptionJourney by Utr" in {
      await(repository.upsert(AuthProviderId("auth-id"), subscriptionJourneyRecord))

      await(repository.findByUtr(validUtr.value)) shouldBe Some(subscriptionJourneyRecord)
    }

    "return None when there is no SubscriptionJourney record for this Utr" in {
      await(repository.upsert(AuthProviderId("auth-id"), subscriptionJourneyRecord))

      await(repository.findByUtr("foo")) shouldBe None
    }

    "delete a SubscriptionJourney record by Utr" in {
      await(repository.upsert(AuthProviderId("auth-id"), subscriptionJourneyRecord))
      await(repository.delete(validUtr.value))
      await(repository.findByAuthId(AuthProviderId("auth-id"))) shouldBe empty
    }

    "update a SubscriptionJourney record" in {
      val updatedSubscriptionJourney = subscriptionJourneyRecord
        .copy(businessDetails = subscriptionJourneyRecord.businessDetails.copy(postcode = "AAABBB"))

      await(repository.upsert(AuthProviderId("auth-id"), subscriptionJourneyRecord))
      await(repository.upsert(AuthProviderId("auth-id"), updatedSubscriptionJourney))

      await(repository.findByAuthId(AuthProviderId("auth-id"))) shouldBe Some(updatedSubscriptionJourney)
    }

    "update a SubscriptionJourney record identified by its UTR" in {
      val updatedSubscriptionJourney = subscriptionJourneyRecord
        .copy(authProviderId = AuthProviderId("new-auth-id"))

      await(repository.upsert(AuthProviderId("auth-id"), subscriptionJourneyRecord))
      await(repository.updateOnUtr(subscriptionJourneyRecord.businessDetails.utr, updatedSubscriptionJourney))

      await(repository.findByAuthId(AuthProviderId("new-auth-id"))) shouldBe Some(updatedSubscriptionJourney)
    }

  }
}
