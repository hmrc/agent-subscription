package uk.gov.hmrc.agentsubscription.repository

import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.test.Helpers._
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscription.config.AppConfig
import uk.gov.hmrc.agentsubscription.connectors.BusinessAddress
import uk.gov.hmrc.agentsubscription.model._
import uk.gov.hmrc.agentsubscription.model.subscriptionJourney._
import uk.gov.hmrc.agentsubscription.support.UnitSpec
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global

class SubscriptionJourneyRepositoryISpec
    extends UnitSpec with GuiceOneAppPerSuite with DefaultPlayMongoRepositorySupport[SubscriptionJourneyRecord] {

  implicit lazy val appConfig = app.injector.instanceOf[AppConfig]

  override lazy val repository = new SubscriptionJourneyRepositoryImpl(mongoComponent)

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
      "GB"
    )
  val registration = Registration(
    Some(registrationName),
    isSubscribedToAgentServices = false,
    isSubscribedToETMP = false,
    businessAddress,
    Some("test@gmail.com"),
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
      businessDetails = BusinessDetails(
        businessType = BusinessType.SoleTrader,
        utr = validUtr,
        postcode = Postcode("bn12 1hn"),
        nino = Some(Nino("AE123456C"))
      ),
      continueId = Some("XXX"),
      amlsData = None,
      cleanCredsAuthProviderId = None,
      mappingComplete = false,
      userMappings = List(),
      lastModifiedDate = None,
      contactEmailData = Some(ContactEmailData(true, Some("email@email.com"))),
      contactTradingNameData = Some(ContactTradingNameData(true, Some("My Trading Name"))),
      contactTradingAddressData = Some(ContactTradingAddressData(true, Some(businessAddress)))
    )

  "SubscriptionJourneyRepository" should {

    "create a SubscriptionJourney record" in {
      await(repository.upsert(subscriptionJourneyRecord.authProviderId, subscriptionJourneyRecord))

      await(repository.findByAuthId(AuthProviderId("auth-id"))).head shouldBe subscriptionJourneyRecord
    }

    "find a SubscriptionJourney by Utr" in {
      await(repository.upsert(AuthProviderId("auth-id"), subscriptionJourneyRecord))

      await(repository.findByUtr(validUtr)) shouldBe Some(subscriptionJourneyRecord)
    }

    "return None when there is no SubscriptionJourney record for this Utr" in {
      await(repository.upsert(AuthProviderId("auth-id"), subscriptionJourneyRecord))

      await(repository.findByUtr(Utr("foo"))) shouldBe None
    }

    "delete a SubscriptionJourney record by Utr" in {
      await(repository.upsert(AuthProviderId("auth-id"), subscriptionJourneyRecord))
      await(repository.delete(validUtr))
      await(repository.findByAuthId(AuthProviderId("auth-id"))) shouldBe empty
    }

    "update a SubscriptionJourney record" in {
      val updatedSubscriptionJourney = subscriptionJourneyRecord
        .copy(businessDetails = subscriptionJourneyRecord.businessDetails.copy(postcode = Postcode("AAABBB")))

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
