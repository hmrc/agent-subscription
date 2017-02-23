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

class DesConnectorISpec extends UnitSpec with OneAppPerSuite with WireMockSupport {
  private implicit val hc = HeaderCarrier()

  private lazy val connector: DesConnector =
    new DesConnector("auth-token", "des-env", new URL(s"http://localhost:$wireMockPort"), WSHttp)

  "subscribeToAgentServices" should {
    "return an ARN when subscription is successful" in {
      val utr = "1234567890"
      DesStubs.subscriptionSucceeds(utr, request)

      val result = await(connector.subscribeToAgentServices(utr, request))

      result shouldBe Arn("ARN0001")
    }

    "propagate an exception if there is a duplicate submission" in {
      val utr = "1234567890"
      DesStubs.subscriptionAlreadyExists(utr)

      val exception = intercept[Upstream4xxResponse] {
        await(connector.subscribeToAgentServices(utr, request))
      }

      exception.upstreamResponseCode shouldBe 409
    }

    "propagate an exception if the agency is not registered" in {
      val utr = "1234567890"
      DesStubs.agencyNotRegistered(utr)

      intercept[NotFoundException] {
        await(connector.subscribeToAgentServices(utr, request))
      }
    }

  }

  def request = DesSubscriptionRequest(agencyName = "My Agency",
                                       agencyAddress =Address(addressLine1 = "1 Some Street", addressLine2 = "MyTown", postalCode = "AA1 1AA", countryCode = "GB"),
                                       agencyEmail = "agency@example.com",
                                       telephoneNumber = "0123 456 7890")


}
