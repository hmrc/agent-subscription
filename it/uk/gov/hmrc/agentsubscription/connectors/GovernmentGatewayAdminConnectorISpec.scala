package uk.gov.hmrc.agentsubscription.connectors

import java.net.URL

import com.kenshoo.play.metrics.Metrics
import org.scalatestplus.play.OneAppPerSuite
import uk.gov.hmrc.agentsubscription.WSHttp
import uk.gov.hmrc.agentsubscription.stubs.GGAdminStubs
import uk.gov.hmrc.agentsubscription.support.{MetricsTestSupport, WireMockSupport}
import uk.gov.hmrc.http.{HeaderCarrier, Upstream5xxResponse}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

class GovernmentGatewayAdminConnectorISpec extends UnitSpec with OneAppPerSuite with WireMockSupport with GGAdminStubs with MetricsTestSupport {
  private lazy val connector = new GovernmentGatewayAdminConnector(new URL(s"http://localhost:$wireMockPort"), WSHttp, app.injector.instanceOf[Metrics])

  private implicit val hc = HeaderCarrier()
  private val arn = "AARN1234567"
  private val postcode = "SY12 8RN"

  "create known facts" should {
    "return status 200 after successfully creating known facts" in {
      givenCleanMetricRegistry()
      createKnownFactsSucceeds()
      val result = await(connector.createKnownFacts(arn,postcode))
      result shouldBe 200
      verifyTimerExistsAndBeenUpdated("GGW-AddKnownFacts-HMRC-AS-AGENT-POST")
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
