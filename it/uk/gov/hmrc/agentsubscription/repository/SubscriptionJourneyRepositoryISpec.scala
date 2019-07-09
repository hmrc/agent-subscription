package uk.gov.hmrc.agentsubscription.repository

import java.time.{ LocalDate, LocalDateTime }

import org.scalatest.concurrent.Eventually
import org.scalatestplus.play.OneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscription.connectors.BusinessAddress
import uk.gov.hmrc.agentsubscription.model.subscriptionJourneyRepositoryModel._
import uk.gov.hmrc.agentsubscription.model.{ AmlsDetails, RegisteredDetails }
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

  private val subscriptionJourneyRecord = SubscriptionJourneyRecord("internal-id", IdentifyBusinessTask(businessType = Some(BusinessType.SoleTrader), utr = Some(validUtr), postcode = Some(Postcode("bn12 1hn")), nino = Some(Nino("AE123456C"))), AMLSTask(), CopyTask(Seq.empty), CreateTask("internal-id"), LocalDateTime.now())

  override def beforeEach() {
    super.beforeEach()
    await(repo.drop)
  }

  "SubscriptionJourneyRepository" should {

    "create a SubscriptionJourney record" in {
      await(repo.create(subscriptionJourneyRecord))

      await(repo.find("internal-id")).head shouldBe subscriptionJourneyRecord
    }

    "find a SubscriptionJourney by Utr" in {
      await(repo.insert(subscriptionJourneyRecord))

      await(repo.find("internal-id")) shouldBe Some(subscriptionJourneyRecord)
    }

    "return None when there is no SubscriptionJourney record for this Utr" in {
      await(repo.insert(subscriptionJourneyRecord))

      await(repo.find("foo")) shouldBe None
    }

    "delete a SubscriptionJourney record by Utr" in {
      await(repo.insert(subscriptionJourneyRecord))
      await(repo.delete("internal-id"))

      await(repo.find("innternal-id")) shouldBe empty
    }

    "update a SubscriptionJourney record using utr" in {
      val updatedSubscriptionJourney = SubscriptionJourneyRecord("internal-id", IdentifyBusinessTask(businessType = Some(BusinessType.LimitedCompany), utr = Some(otherUtr), postcode = Some(Postcode("BN3 2TN")), nino = Some(Nino("AE123456D"))), AMLSTask(), CopyTask(Seq.empty), CreateTask("internal-id"), LocalDateTime.now())
      await(repo.insert(subscriptionJourneyRecord))
      await(repo.update("internal-id", updatedSubscriptionJourney))

      await(repo.find("internal-id")) shouldBe Some(updatedSubscriptionJourney)
    }
  }
}
