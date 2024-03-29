package uk.gov.hmrc.agentsubscription.connectors

import com.kenshoo.play.metrics.Metrics
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.agentsubscription.config.AppConfig
import uk.gov.hmrc.agentsubscription.model.ApplicationStatus.{Accepted, AttemptingRegistration, Registered}
import uk.gov.hmrc.agentsubscription.model._
import uk.gov.hmrc.agentsubscription.stubs.AgentOverseasApplicationStubs
import uk.gov.hmrc.agentsubscription.support.{BaseISpec, MetricsTestSupport}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpClient
import play.api.test.Helpers._

import scala.concurrent.ExecutionContext.Implicits.global

class AgentOverseasApplicationConnectorISpec
    extends BaseISpec with AgentOverseasApplicationStubs with MetricsTestSupport with MockitoSugar {

  private lazy val http = app.injector.instanceOf[HttpClient]
  private val appConfig = app.injector.instanceOf[AppConfig]
  private val metrics = app.injector.instanceOf[Metrics]

  private lazy val connector: AgentOverseasApplicationConnector =
    new AgentOverseasApplicationConnector(appConfig, http, metrics)

  private implicit val hc = HeaderCarrier()

  private val agencyDetails = OverseasAgencyDetails(
    "Agency name",
    "agencyemail@domain.com",
    OverseasAgencyAddress("Mandatory Address Line 1", "Mandatory Address Line 2", None, None, "IE")
  )

  private val businessDetails =
    TradingDetails("tradingName", OverseasBusinessAddress("addressLine1", "addressLine2", None, None, "CC"))

  private val businessContactDetails =
    OverseasContactDetails(businessTelephone = "BUSINESS PHONE 123456789", businessEmail = "email@domain.com")

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

        an[RuntimeException] shouldBe thrownBy(
          await(connector.updateApplicationStatus(targetAppStatus, "currentUserAuthId"))
        )
      }
      "receives conflict" in {
        givenUpdateApplicationStatus(AttemptingRegistration, 409)

        an[RuntimeException] shouldBe thrownBy(
          await(connector.updateApplicationStatus(targetAppStatus, "currentUserAuthId"))
        )
      }
    }
  }

  "currentApplication" should {
    "return a valid status, safeId and amls details" in {
      givenValidApplication("registered", safeId = Some("XE0001234567890"))

      await(connector.currentApplication) shouldBe CurrentApplication(
        Registered,
        Some(SafeId("XE0001234567890")),
        Some(OverseasAmlsDetails("supervisoryName", Some("supervisoryId"))),
        businessContactDetails,
        businessDetails,
        agencyDetails
      )
    }

    "return no safeId for if application has not yet reached registered state" in {
      givenValidApplication("accepted", safeId = None)

      await(connector.currentApplication) shouldBe CurrentApplication(
        Accepted,
        safeId = None,
        Some(OverseasAmlsDetails("supervisoryName", Some("supervisoryId"))),
        businessContactDetails,
        businessDetails,
        agencyDetails
      )
    }

    "return exception for validation errors" when {
      "API response is completely invalid" in {
        givenInvalidApplication

        an[RuntimeException] shouldBe thrownBy(await(connector.currentApplication))
      }

      "application contains invalid safeID" in {
        givenValidApplication("accepted", safeId = Some("notValid"))

        a[RuntimeException] shouldBe thrownBy(await(connector.currentApplication))
      }

      "application contains invalid status" in {
        givenValidApplication("invalid")

        a[RuntimeException] shouldBe thrownBy(await(connector.currentApplication))
      }

      "application contains invalid business details" in {
        givenValidApplication("accepted", businessTradingName = "~tilde not allowed~")

        a[RuntimeException] shouldBe thrownBy(await(connector.currentApplication))
      }

      "application contains invalid agency details" in {
        givenValidApplication("accepted", agencyName = "~tilde not allowed~")

        a[RuntimeException] shouldBe thrownBy(await(connector.currentApplication))
      }
    }

  }
}
