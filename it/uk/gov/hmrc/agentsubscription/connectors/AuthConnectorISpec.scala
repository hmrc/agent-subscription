package uk.gov.hmrc.agentsubscription.connectors

import java.net.URL

import org.scalatestplus.play.OneAppPerSuite
import uk.gov.hmrc.agentsubscription.WSHttp
import uk.gov.hmrc.agentsubscription.auth.Authority
import uk.gov.hmrc.agentsubscription.stubs.AuthStub
import uk.gov.hmrc.agentsubscription.support.WireMockSupport
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.play.test.UnitSpec

class AuthConnectorISpec extends UnitSpec with OneAppPerSuite with WireMockSupport with AuthStub {
  private implicit val hc = HeaderCarrier()

  private lazy val connector: AuthConnector = new AuthConnector(new URL(s"http://localhost:$wireMockPort"), WSHttp)

  "AuthConnector currentAuthority" should {
    "return Authority when an authority detail is available" in {
      requestIsAuthenticated().andIsAnAgent()
      await(connector.currentAuthority()) shouldBe Some(Authority("Agent", "/auth/oid/556737e15500005500eaf68f/enrolments"))
    }

    "return none when an authority detail is unavailable" in {
      requestIsNotAuthenticated()
      await(connector.currentAuthority()) shouldBe None
    }
  }
}
