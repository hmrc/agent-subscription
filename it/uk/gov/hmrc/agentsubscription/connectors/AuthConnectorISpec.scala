package uk.gov.hmrc.agentsubscription.connectors

import java.net.URL

import org.scalatestplus.play.OneAppPerSuite
import uk.gov.hmrc.agentsubscription.WSHttp
import uk.gov.hmrc.agentsubscription.stubs.AuthStub
import uk.gov.hmrc.agentsubscription.support.WireMockSupport
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

class AuthConnectorISpec extends UnitSpec with OneAppPerSuite with WireMockSupport {
  private implicit val hc = HeaderCarrier()

  private lazy val connector: AuthConnector = new AuthConnector(new URL(s"http://localhost:${wireMockPort}"), WSHttp)

  "AuthConnector isAuthenticated" should {
    "return true when an authority detail is available" in {
      AuthStub.requestIsAuthenticated()
      await(connector.isAuthenticated()) shouldBe true
    }

    "return false when an authority detail is unavailable" in {
      AuthStub.requestIsNotAuthenticated()
      await(connector.isAuthenticated()) shouldBe false
    }
  }
}
