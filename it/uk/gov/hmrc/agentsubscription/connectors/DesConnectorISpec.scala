package uk.gov.hmrc.agentsubscription.connectors

import java.net.URL

import org.scalatestplus.play.OneAppPerSuite
import uk.gov.hmrc.agentsubscription.WSHttp
import uk.gov.hmrc.agentsubscription.model.Arn
import uk.gov.hmrc.agentsubscription.stubs.DesStubs
import uk.gov.hmrc.agentsubscription.support.WireMockSupport
import uk.gov.hmrc.play.http.{HeaderCarrier, NotFoundException, Upstream4xxResponse}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

class DesConnectorISpec extends UnitSpec with OneAppPerSuite with WireMockSupport with DesStubs {
  private implicit val hc = HeaderCarrier()
  val utr = "1234567890"

  private val bearerToken = "auth-token"
  private val environment = "des-env"

  override protected def expectedBearerToken = Some(bearerToken)
  override protected def expectedEnvironment = Some(environment)

  private lazy val connector: DesConnector =
    new DesConnector(environment, bearerToken, new URL(s"http://localhost:$wireMockPort"), WSHttp)

  "subscribeToAgentServices" should {
    "return an ARN when subscription is successful" in {
      subscriptionSucceeds(utr, request)

      val result = await(connector.subscribeToAgentServices(utr, request))

      result shouldBe Arn("ARN0001")
    }

    "propagate an exception if there is a duplicate submission" in {
      subscriptionAlreadyExists(utr)

      val exception = intercept[Upstream4xxResponse] {
        await(connector.subscribeToAgentServices(utr, request))
      }

      exception.upstreamResponseCode shouldBe 409
    }

    "propagate an exception if the agency is not registered" in {
      agencyNotRegistered(utr)

      intercept[NotFoundException] {
        await(connector.subscribeToAgentServices(utr, request))
      }
    }
  }

  "getRegistration" should {
    "return registration details for a UTR that is known by DES" in {
      registrationExists(utr)

      val registration = await(connector.getRegistration(utr))

      registration shouldBe Some(DesRegistrationResponse(Some("AA1 1AA"), isAnASAgent = true, Some("My Agency")))
    }

    "return registration details without organisationName for a UTR that is known by DES" in {
      registrationExistsWithNoOrganisationName(utr)

      val registration = await(connector.getRegistration(utr))

      registration shouldBe Some(DesRegistrationResponse(Some("AA1 1AA"), isAnASAgent = true, None))
    }

    "return registration details without postcode for a UTR that is known by DES" in {
      registrationExistsWithNoPostcode(utr)

      val registration = await(connector.getRegistration(utr))

      registration shouldBe Some(DesRegistrationResponse(None, isAnASAgent = true, None))
    }


    "not return a registration for a UTR that is unknown to DES" in {
      registrationDoesNotExist(utr)

      val registration = await(connector.getRegistration(utr))

      registration shouldBe None
    }
  }

  def request = DesSubscriptionRequest( agencyName = "My Agency",
                                       agencyAddress =Address(addressLine1 = "1 Some Street", addressLine2 = "MyTown", postalCode = "AA1 1AA", countryCode = "GB"),
                                       agencyEmail = "agency@example.com",
                                       telephoneNumber = "0123 456 7890")


}
