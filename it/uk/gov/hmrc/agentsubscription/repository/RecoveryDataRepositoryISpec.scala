package uk.gov.hmrc.agentsubscription.repository

import org.scalatest.concurrent.Eventually
import org.scalatestplus.play.OneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import uk.gov.hmrc.agentmtdidentifiers.model.{ Arn, Utr }
import uk.gov.hmrc.agentsubscription.connectors.AuthIds
import uk.gov.hmrc.agentsubscription.model.SubscriptionRequest
import uk.gov.hmrc.agentsubscription.support.MongoApp
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

class RecoveryDataRepositoryISpec extends UnitSpec with OneAppPerSuite with MongoApp with Eventually {

  protected def appBuilder: GuiceApplicationBuilder = new GuiceApplicationBuilder()
    .configure(mongoConfiguration)

  override implicit lazy val app: Application = appBuilder.build()

  private lazy val repo = app.injector.instanceOf[RecoveryRepository]

  private val utr = Utr("7000000002")

  private val subscriptionRequest: String =
    s"""
       |{
       |  "utr": "${utr.value}",
       |  "knownFacts": {
       |    "postcode": "AA1 1AA"
       |  },
       |  "agency": {
       |    "name": "My Agency",
       |    "address": {
       |      "addressLine1": "Flat 1",
       |      "addressLine2": "1 Some Street",
       |      "addressLine3": "Anytown",
       |      "addressLine4": "County",
       |      "postcode": "AA1 2AA",
       |      "countryCode": "GB"
       |    },
       |    "email": "agency@example.com",
       |    "telephone": "0123 456 7890"
       |  }
       |}
     """.stripMargin

  val arn = Arn("TARN0000001")
  val authIds = AuthIds("userId", "groupId")
  val subscriptionRequestBody: SubscriptionRequest = Json.parse(subscriptionRequest).as[SubscriptionRequest]

  "RecoveryDataRepository" should {
    "create a record if Upsert KnownFacts failed" in {
      val result = await(repo.create(authIds, arn, subscriptionRequestBody, "Failed to Upsert Known Facts"))
      result shouldBe ()
    }

    "create a record if Allocate Enrolment failed" in {
      val result = await(repo.create(authIds, arn, subscriptionRequestBody, "Failed to Enrol to HMRC-AS-AGENT"))
      result shouldBe ()
    }
  }
}
