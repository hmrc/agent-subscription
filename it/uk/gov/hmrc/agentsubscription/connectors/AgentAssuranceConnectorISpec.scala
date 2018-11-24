package uk.gov.hmrc.agentsubscription.connectors

import java.net.URL

import com.kenshoo.play.metrics.Metrics
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.OneAppPerSuite
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr}
import uk.gov.hmrc.agentsubscription.stubs.AgentAssuranceStub
import uk.gov.hmrc.agentsubscription.support.{MetricsTestSupport, WireMockSupport}
import uk.gov.hmrc.http.HttpPut
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

class AgentAssuranceConnectorISpec extends AgentAssuranceStub with UnitSpec with OneAppPerSuite with WireMockSupport with MetricsTestSupport with MockitoSugar {

  val utr = Utr("1234567890")
  val arn = Arn("UTR12345")
  private lazy val metrics = app.injector.instanceOf[Metrics]
  private lazy val httpPut: HttpPut = app.injector.instanceOf[HttpPut]

  private lazy val connector: AgentAssuranceConnector =
    new AgentAssuranceConnectorImpl(new URL(s"http://localhost:$wireMockPort"), httpPut, metrics)

  "updating AMLS" should {

    "return a successful response" in {

      updateAmlsSucceeds(utr, arn)

      val result = await(connector.updateAmls(utr, arn))

      result shouldBe Some(amlsDetails.copy(utr = utr, arn = Some(arn)))

    }

    "handle failure responses from agent-assurance backend during updateAmls" in {

      updateAmlsFailsWith404(utr, arn)

      val result = await(connector.updateAmls(utr, arn))

      result shouldBe None
    }
  }

}
