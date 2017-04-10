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
      await(connector.currentAuthority()) shouldBe Some(Authority(
        authProviderId = Some("12345-credId"),
        authProviderType = Some("GovernmentGateway"),
        affinityGroup = "Agent",
        enrolmentsUrl = "/auth/oid/556737e15500005500eaf68f/enrolments"))
    }

    "return Authority when user-details does not include an auth provider" in {
      requestIsAuthenticated().andIsAnAgentWithoutAuthProvider()
      await(connector.currentAuthority()) shouldBe Some(Authority(
        authProviderId = None,
        authProviderType = None,
        affinityGroup = "Agent",
        enrolmentsUrl = "/auth/oid/556737e15500005500eaf68f/enrolments"))
    }

    "return none when an authority detail is unavailable" in {
      requestIsNotAuthenticated()
      await(connector.currentAuthority()) shouldBe None
    }
  }
}
