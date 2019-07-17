package uk.gov.hmrc.agentsubscription.repository

import java.time.{ LocalDate, LocalDateTime, ZoneOffset }
import java.util.UUID

import org.scalatest.concurrent.Eventually
import org.scalatestplus.play.OneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscription.connectors.BusinessAddress
import uk.gov.hmrc.agentsubscription.model.subscriptionJourney._
import uk.gov.hmrc.agentsubscription.model.{ AmlsDetails, AuthProviderId, RegisteredDetails }
import uk.gov.hmrc.agentsubscription.support.MongoApp
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

class SubscriptionJourneyRepositoryISpec extends UnitSpec with OneAppPerSuite with MongoApp with Eventually {

  protected def appBuilder: GuiceApplicationBuilder = new GuiceApplicationBuilder()
    .configure(mongoConfiguration)

  val validUtr = Utr("2000000000")
  val otherUtr = Utr("0123456789")
  val registrationName = "My Agency"
  val businessAddress =
    BusinessAddress("AddressLine1 A", Some("AddressLine2 A"), Some("AddressLine3 A"), Some("AddressLine4 A"), Some("AA11AA"), "GB")
  val registration = Registration(Some(registrationName), false, false, businessAddress, Some("test@gmail.com"))

  override implicit lazy val app: Application = appBuilder.build()

  private lazy val repo = app.injector.instanceOf[SubscriptionJourneyRepository]

  val amlsDetails = AmlsDetails("supervisory", Right(RegisteredDetails("123456789", LocalDate.now())))

  private val subscriptionJourneyRecord =
    SubscriptionJourneyRecord(
      AuthProviderId("auth-id"),
      businessDetails = BusinessDetails(
        businessType = BusinessType.SoleTrader,
        utr = validUtr,
        postcode = Postcode("bn12 1hn"),
        nino = Some(Nino("AE123456C"))),
      continueId = "XXX",
      amlsData = None,
      cleanCredsAuthProviderId = None,
      mappingComplete = false,
      userMappings = List(),
      lastModifiedDate = None)

  override def beforeEach() {
    super.beforeEach()
    await(repo.drop)
    ()
  }

  "SubscriptionJourneyRepository" should {

    "create a SubscriptionJourney record" in {
      await(repo.upsert(subscriptionJourneyRecord.authProviderId, subscriptionJourneyRecord))

      await(repo.findByPrimaryId(AuthProviderId("auth-id"))).head shouldBe subscriptionJourneyRecord
    }

    "find a SubscriptionJourney by Utr" in {
      await(repo.insert(subscriptionJourneyRecord))

      await(repo.findByUtr(validUtr)) shouldBe Some(subscriptionJourneyRecord)
    }

    "return None when there is no SubscriptionJourney record for this Utr" in {
      await(repo.insert(subscriptionJourneyRecord))

      await(repo.findByUtr(Utr("foo"))) shouldBe None
    }

    "delete a SubscriptionJourney record by Utr" in {
      await(repo.insert(subscriptionJourneyRecord))
      await(repo.delete(AuthProviderId("auth-id")))
      await(repo.findByPrimaryId(AuthProviderId("auth-id"))) shouldBe empty
    }

    "update a SubscriptionJourney record" in {
      val updatedSubscriptionJourney = subscriptionJourneyRecord
        .copy(
          businessDetails = subscriptionJourneyRecord.businessDetails.copy(postcode = Postcode("AAABBB")))

      await(repo.insert(subscriptionJourneyRecord))
      await(repo.upsert(AuthProviderId("auth-id"), updatedSubscriptionJourney))

      await(repo.findByPrimaryId(AuthProviderId("auth-id"))) shouldBe Some(updatedSubscriptionJourney)
    }
  }
}
