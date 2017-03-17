package uk.gov.hmrc.agentsubscription.connectors

import java.net.URL

import org.scalatestplus.play.OneAppPerSuite
import uk.gov.hmrc.agentsubscription.WSHttp
import uk.gov.hmrc.agentsubscription.stubs.GGAdminStubs
import uk.gov.hmrc.agentsubscription.support.WireMockSupport
import uk.gov.hmrc.play.http.{HeaderCarrier, Upstream5xxResponse}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

class GovernmentGatewayAdminConnectorISpec extends UnitSpec with OneAppPerSuite with WireMockSupport with GGAdminStubs{
  private lazy val connector = new GovernmentGatewayAdminConnector(new URL(s"http://localhost:$wireMockPort"), WSHttp)

  private implicit val hc = HeaderCarrier()
  private val arn = "AARN1234567"
  private val postcode = "SY12 8RN"

  "create known facts" should {
    "return status 200 after successfully creating known facts" in {
      createKnownFactsSucceeds()
      val result = await(connector.createKnownFacts(arn,postcode))
      result shouldBe 200
    }

    "propogate an exception after failing to create known facts" in {
      createKnownFactsFails()

      val exception = intercept[Upstream5xxResponse] {
        await(connector.createKnownFacts(arn,postcode))
      }

      exception.upstreamResponseCode shouldBe 500
    }
  }
}
