package uk.gov.hmrc.agentsubscription.connectors

import java.net.URL

import org.scalatestplus.play.OneAppPerSuite
import uk.gov.hmrc.agentsubscription.WSHttp
import uk.gov.hmrc.agentsubscription.stubs.GGStubs
import uk.gov.hmrc.agentsubscription.support.WireMockSupport
import uk.gov.hmrc.play.http.{HeaderCarrier, Upstream5xxResponse}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

class GovernmentGatewayConnectorISpec extends UnitSpec with OneAppPerSuite with WireMockSupport with GGStubs{
  private lazy val connector = new GovernmentGatewayConnector(new URL(s"http://localhost:$wireMockPort"), WSHttp)

  private implicit val hc = HeaderCarrier()
  private val friendlyName = "Mr Friendly"
  private val arn = "AARN1234567"
  private val postcode = "SY12 8RN"

  "addEnrolment" should {
    "return status 200 after a successful enrolment" in {
      enrolmentSucceeds()
      val result = await(connector.enrol(friendlyName,arn,postcode))
      result shouldBe 200
    }

    "propogate an exception for a failed enrolment" in {
      enrolmentFails()

      val exception = intercept[Upstream5xxResponse] {
        await(connector.enrol(friendlyName,arn,postcode))
      }

      exception.upstreamResponseCode shouldBe 500
    }
  }
}
