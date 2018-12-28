package uk.gov.hmrc.agentsubscription.connectors

import java.net.URL

import com.kenshoo.play.metrics.Metrics
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.OneAppPerSuite
import uk.gov.hmrc.agentsubscription.model.ApplicationStatus.AttemptingRegistration
import uk.gov.hmrc.agentsubscription.stubs.AgentOverseasApplicationStubs
import uk.gov.hmrc.agentsubscription.support.{ MetricsTestSupport, WireMockSupport }
import uk.gov.hmrc.http.{ HeaderCarrier, HttpGet }
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

class AgentOverseasApplicationConnectorISpec extends AgentOverseasApplicationStubs with UnitSpec with OneAppPerSuite with WireMockSupport with MetricsTestSupport with MockitoSugar {

  private lazy val metrics = app.injector.instanceOf[Metrics]
  private lazy val http: HttpGet = app.injector.instanceOf[HttpGet]

  private lazy val connector: AgentOverseasApplicationConnector =
    new AgentOverseasApplicationConnector(new URL(s"http://localhost:$wireMockPort"), http, metrics)

  private implicit val hc = HeaderCarrier()

  "updateApplicationStatus" should {
    val targetAppStatus = AttemptingRegistration
    "successful status update" in {
      givenGetUpdateApplicationStatus(AttemptingRegistration, 204)

      val result = await(connector.updateApplicationStatus(targetAppStatus, "currentUserAuthId"))

      result shouldBe true
    }

    "failure, status not changed" when {
      "receives NotFound" in {
        givenGetUpdateApplicationStatus(AttemptingRegistration, 404)

        an[RuntimeException] shouldBe thrownBy(await(connector.updateApplicationStatus(targetAppStatus, "currentUserAuthId")))
      }
      "receives conflict" in {
        givenGetUpdateApplicationStatus(AttemptingRegistration, 409)

        an[RuntimeException] shouldBe thrownBy(await(connector.updateApplicationStatus(targetAppStatus, "currentUserAuthId")))
      }
    }
  }
}
