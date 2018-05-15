package uk.gov.hmrc.agentsubscription.connectors

import java.net.URL

import com.kenshoo.play.metrics.Metrics
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.verify
import org.scalatest.concurrent.Eventually
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.OneAppPerSuite
import play.api.libs.json.{ JsValue, Json }
import uk.gov.hmrc.agentmtdidentifiers.model.{ Arn, Utr }
import uk.gov.hmrc.agentsubscription.stubs.DesStubs
import uk.gov.hmrc.agentsubscription.support.{ MetricsTestSupport, WireMockSupport }
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.audit.http.HttpAuditing
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.MergedDataEvent
import uk.gov.hmrc.play.http.ws.WSPost
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global

class DesConnectorISpec extends UnitSpec with OneAppPerSuite with WireMockSupport with DesStubs with MetricsTestSupport with MockitoSugar {
  private implicit val hc = HeaderCarrier()
  val utr = Utr("1234567890")

  private val bearerToken = "auth-token"
  private val environment = "des-env"

  override protected def expectedBearerToken = Some(bearerToken)

  override protected def expectedEnvironment = Some(environment)

  private lazy val metrics = app.injector.instanceOf[Metrics]
  private lazy val httpPost: HttpPost = app.injector.instanceOf[HttpPost]

  private lazy val connector: DesConnector =
    new DesConnector(environment, bearerToken, new URL(s"http://localhost:$wireMockPort"), httpPost, metrics)

  "subscribeToAgentServices" should {
    "return an ARN when subscription is successful" in {
      subscriptionSucceeds(utr, request)

      val result = await(connector.subscribeToAgentServices(utr, request))

      result shouldBe Arn("ARN0001")
    }

    "propagate an exception containing the utr if there is a duplicate submission" in {
      subscriptionAlreadyExists(utr)

      val exception = intercept[RuntimeException] {
        await(connector.subscribeToAgentServices(utr, request))
      }

      exception.getMessage.contains(utr.value) shouldBe true
      exception.getCause.asInstanceOf[Upstream4xxResponse].upstreamResponseCode shouldBe 409
    }

    "propagate an exception containing the utr if the agency is not registered" in {
      agencyNotRegistered(utr)

      val exception = intercept[RuntimeException] {
        await(connector.subscribeToAgentServices(utr, request))
      }

      exception.getMessage.contains(utr.value) shouldBe true
    }

    "audit the request and response" in new MockAuditingContext {
      givenCleanMetricRegistry()
      val connector: DesConnector =
        new DesConnector(environment, bearerToken, new URL(s"http://localhost:$wireMockPort"), wsHttp, metrics)
      subscriptionSucceeds(utr, request)

      await(connector.subscribeToAgentServices(utr, request))

      val auditEvent: MergedDataEvent = capturedEvent()
      auditEvent.request.tags("path") shouldBe s"$wireMockBaseUrl/registration/agents/utr/${utr.value}"
      auditEvent.auditType shouldBe "OutboundCall"
      val requestJson: JsValue = Json.parse(auditEvent.request.detail("requestBody"))
      (requestJson \ "regime").as[String] shouldBe "ITSA"
      (requestJson \ "agencyName").as[String] shouldBe "My Agency"
      (requestJson \ "telephoneNumber").as[String] shouldBe "0123 456 7890"
      (requestJson \ "agencyEmail").as[String] shouldBe "agency@example.com"
      (requestJson \ "agencyAddress" \ "addressLine1").as[String] shouldBe "1 Some Street"
      (requestJson \ "agencyAddress" \ "addressLine2").as[String] shouldBe "MyTown"
      (requestJson \ "agencyAddress" \ "postalCode").as[String] shouldBe "AA1 1AA"
      (requestJson \ "agencyAddress" \ "countryCode").as[String] shouldBe "GB"

      val responseJson: JsValue = Json.parse(auditEvent.response.detail("responseMessage"))
      (responseJson \ "agentRegistrationNumber").as[String] shouldBe "ARN0001"
      verifyTimerExistsAndBeenUpdated("DES-SubscribeAgent-POST")
    }

  }

  "getRegistration" should {
    "return registration details for a organisation UTR that is known by DES" in {
      organisationRegistrationExists(utr)

      val registration = await(connector.getRegistration(utr))

      registration shouldBe Some(DesRegistrationResponse(Some("AA1 1AA"), isAnASAgent = true, Some("My Agency"), None, Some(Arn("TARN0000001"))))
    }

    "return registration details for an individual UTR that is known by DES" in {
      individualRegistrationExists(utr)

      val registration = await(connector.getRegistration(utr))

      registration shouldBe Some(DesRegistrationResponse(Some("AA1 1AA"), isAnASAgent = true, None, Some(DesIndividual("First", "Last")), Some(Arn("AARN0000002"))))
    }

    "return registration details without organisationName for a UTR that is known by DES" in {
      registrationExistsWithNoOrganisationName(utr)

      val registration = await(connector.getRegistration(utr))

      registration shouldBe Some(DesRegistrationResponse(Some("AA1 1AA"), isAnASAgent = true, None, None, None))
    }

    "return registration details without postcode for a UTR that is known by DES" in {
      registrationExistsWithNoPostcode(utr)

      val registration = await(connector.getRegistration(utr))

      registration shouldBe Some(DesRegistrationResponse(None, isAnASAgent = true, None, None, None))
    }

    "not return a registration for a UTR that is unknown to DES" in {
      registrationDoesNotExist(utr)

      val registration = await(connector.getRegistration(utr))

      registration shouldBe None
    }

    "audit the request and response" in new MockAuditingContext {
      givenCleanMetricRegistry()
      val connector: DesConnector =
        new DesConnector(environment, bearerToken, new URL(s"http://localhost:$wireMockPort"), wsHttp, metrics)
      organisationRegistrationExists(utr)

      await(connector.getRegistration(utr))

      val auditEvent = capturedEvent()
      auditEvent.request.tags("path") shouldBe s"$wireMockBaseUrl/registration/individual/utr/${utr.value}"
      auditEvent.auditType shouldBe "OutboundCall"

      val responseJson = Json.parse(auditEvent.response.detail("responseMessage"))
      (responseJson \ "address" \ "postalCode").as[String] shouldBe "AA1 1AA"
      (responseJson \ "isAnASAgent").as[Boolean] shouldBe true
      (responseJson \ "organisation" \ "organisationName").as[String] shouldBe "My Agency"
      verifyTimerExistsAndBeenUpdated("DES-GetAgentRegistration-POST")
    }
  }

  def request = DesSubscriptionRequest(
    agencyName = "My Agency",
    agencyAddress = Address(addressLine1 = "1 Some Street", addressLine2 = Some("MyTown"), postalCode = "AA1 1AA", countryCode = "GB"),
    agencyEmail = "agency@example.com",
    telephoneNumber = "0123 456 7890")

  trait MockAuditingContext extends MockitoSugar with Eventually {
    private val mockAuditConnector = mock[AuditConnector]

    val wsHttp = new HttpPost with WSPost with HttpAuditing {
      val auditConnector = mockAuditConnector
      val appName = "agent-subscription"
      override val hooks = Seq(AuditingHook)
    }

    def capturedEvent(): MergedDataEvent = {
      eventually[MergedDataEvent]({
        val captor = ArgumentCaptor.forClass(classOf[MergedDataEvent])
        verify(mockAuditConnector).sendMergedEvent(captor.capture())(any[HeaderCarrier], any[ExecutionContext])
        captor.getValue
      })
    }
  }

}