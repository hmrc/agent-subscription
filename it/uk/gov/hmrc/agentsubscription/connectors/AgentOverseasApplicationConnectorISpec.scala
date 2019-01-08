package uk.gov.hmrc.agentsubscription.connectors

import java.net.URL

import com.kenshoo.play.metrics.Metrics
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.OneAppPerSuite
import uk.gov.hmrc.agentsubscription.model.ApplicationStatus.{ Accepted, AttemptingRegistration, Registered }
import uk.gov.hmrc.agentsubscription.model.{ CurrentApplication, OverseasAmlsDetails, SafeId }
import uk.gov.hmrc.agentsubscription.stubs.AgentOverseasApplicationStubs
import uk.gov.hmrc.agentsubscription.support.{ MetricsTestSupport, WireMockSupport }
import uk.gov.hmrc.http.{ HeaderCarrier, HttpGet, HttpPut }
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

class AgentOverseasApplicationConnectorISpec extends AgentOverseasApplicationStubs with UnitSpec with OneAppPerSuite with WireMockSupport with MetricsTestSupport with MockitoSugar {

  private lazy val metrics = app.injector.instanceOf[Metrics]
  private lazy val http = app.injector.instanceOf[HttpPut with HttpGet]

  private lazy val connector: AgentOverseasApplicationConnector =
    new AgentOverseasApplicationConnector(new URL(s"http://localhost:$wireMockPort"), http, metrics)

  private implicit val hc = HeaderCarrier()

  "updateApplicationStatus" should {
    val targetAppStatus = AttemptingRegistration
    "successful status update" in {
      givenUpdateApplicationStatus(AttemptingRegistration, 204)

      val result = await(connector.updateApplicationStatus(targetAppStatus, "currentUserAuthId"))

      result shouldBe true
    }

    "successful status update with safeId for registered status" in {
      givenUpdateApplicationStatus(Registered, 204, s"""{"safeId" : "12345"}""")

      val result = await(connector.updateApplicationStatus(Registered, "currentUserAuthId", Some(SafeId("12345"))))

      result shouldBe true
    }

    "failure, status not changed" when {
      "receives NotFound" in {
        givenUpdateApplicationStatus(AttemptingRegistration, 404)

        an[RuntimeException] shouldBe thrownBy(await(connector.updateApplicationStatus(targetAppStatus, "currentUserAuthId")))
      }
      "receives conflict" in {
        givenUpdateApplicationStatus(AttemptingRegistration, 409)

        an[RuntimeException] shouldBe thrownBy(await(connector.updateApplicationStatus(targetAppStatus, "currentUserAuthId")))
      }
    }
  }

  "currentApplication" should {
    "return a valid status, safeId and amls details" in {
      givenValidApplication("registered", "12345")

      await(connector.currentApplication) shouldBe CurrentApplication(Registered, Some(SafeId("12345")), OverseasAmlsDetails("supervisoryName", Some("supervisoryId")))
    }

    "return empty safeId for statuses other than registered" in {
      givenValidApplication("accepted")

      await(connector.currentApplication) shouldBe CurrentApplication(Accepted, Some(SafeId("")), OverseasAmlsDetails("supervisoryName", Some("supervisoryId")))
    }

    "return exception for invalid API response" in {
      givenInvalidApplication

      an[RuntimeException] shouldBe thrownBy(await(connector.currentApplication))
    }

  }
}
