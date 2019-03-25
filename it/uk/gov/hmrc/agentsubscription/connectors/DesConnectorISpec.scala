package uk.gov.hmrc.agentsubscription.connectors

import java.net.URL

import akka.actor.ActorSystem
import com.kenshoo.play.metrics.Metrics
import com.typesafe.config.Config
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.verify
import org.scalatest.concurrent.Eventually
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.OneAppPerSuite
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr}
import uk.gov.hmrc.agentsubscription.model
import uk.gov.hmrc.agentsubscription.model.{AgentRecord, Crn}
import uk.gov.hmrc.agentsubscription.stubs.DesStubs
import uk.gov.hmrc.agentsubscription.support.{MetricsTestSupport, WireMockSupport}
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.audit.http.HttpAuditing
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.MergedDataEvent
import uk.gov.hmrc.play.http.ws.{WSGet, WSPost}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global

class DesConnectorISpec extends UnitSpec with OneAppPerSuite with WireMockSupport with DesStubs with MetricsTestSupport with MockitoSugar {
  private implicit val hc = HeaderCarrier()
  val utr = Utr("1234567890")
  val crn = Crn("SC123456")

  private val bearerToken = "auth-token"
  private val environment = "des-env"

  override protected def expectedBearerToken = Some(bearerToken)

  override protected def expectedEnvironment = Some(environment)

  private lazy val metrics = app.injector.instanceOf[Metrics]
  private lazy val httpPost: HttpPost = app.injector.instanceOf[HttpPost]
  private lazy val httpGet: HttpGet = app.injector.instanceOf[HttpGet]

  private lazy val connector: DesConnector =
    new DesConnector(environment, bearerToken, new URL(s"http://localhost:$wireMockPort"), httpPost, httpGet, metrics)

  "subscribeToAgentServices" should {
    "return an ARN when subscription is successful" in {
      subscriptionSucceeds(utr, request)

      val result = await(connector.subscribeToAgentServices(utr, request))

      result shouldBe Arn("TARN0000001")
    }

    "return an ARN when subscription is successful and the request does not have a telephone number" in {
      subscriptionSucceedsWithoutTelephoneNo(utr, request.copy(telephoneNumber = None))

      val result = await(connector.subscribeToAgentServices(utr, request.copy(telephoneNumber = None)))

      result shouldBe Arn("TARN0000001")
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
        new DesConnector(environment, bearerToken, new URL(s"http://localhost:$wireMockPort"), wsHttp, wsHttp, metrics)
      subscriptionSucceeds(utr, request)

      await(connector.subscribeToAgentServices(utr, request))

      val auditEvent: MergedDataEvent = capturedEvent()
      auditEvent.request.tags("path") shouldBe s"$wireMockBaseUrl/registration/agents/utr/${utr.value}"
      auditEvent.auditType shouldBe "OutboundCall"
      val requestJson: JsValue = Json.parse(auditEvent.request.detail("requestBody"))
      (requestJson \ "agencyName").as[String] shouldBe "My Agency"
      (requestJson \ "telephoneNumber").as[String] shouldBe "0123 456 7890"
      (requestJson \ "agencyEmail").as[String] shouldBe "agency@example.com"
      (requestJson \ "agencyAddress" \ "addressLine1").as[String] shouldBe "1 Some Street"
      (requestJson \ "agencyAddress" \ "addressLine2").as[String] shouldBe "MyTown"
      (requestJson \ "agencyAddress" \ "postalCode").as[String] shouldBe "AA1 1AA"
      (requestJson \ "agencyAddress" \ "countryCode").as[String] shouldBe "GB"

      val responseJson: JsValue = Json.parse(auditEvent.response.detail("responseMessage"))
      (responseJson \ "agentRegistrationNumber").as[String] shouldBe "TARN0000001"
      verifyTimerExistsAndBeenUpdated("DES-SubscribeAgent-POST")
    }

  }

  "getRegistration" should {
    val businessAddress =
      BusinessAddress("AddressLine1 A", Some("AddressLine2 A"), Some("AddressLine3 A"), Some("AddressLine4 A"), Some("AA1 1AA"), "GB")

    "return registration details for a organisation UTR that is known by DES" in {
      organisationRegistrationExists(utr)

      val registration = await(connector.getRegistration(utr))

      registration shouldBe Some(DesRegistrationResponse(isAnASAgent = true, Some("My Agency"), None, Some(Arn("TARN0000001")), businessAddress, Some("agent1@example.com")))
    }

    "return registration details for an individual UTR that is known by DES" in {
      individualRegistrationExists(utr)

      val registration = await(connector.getRegistration(utr))

      registration shouldBe Some(DesRegistrationResponse(isAnASAgent = true, None, Some(DesIndividual("First", "Last")), Some(Arn("AARN0000002")), businessAddress, Some("individual@example.com")))
    }

    "return registration details without organisationName for a UTR that is known by DES" in {
      registrationExistsWithNoOrganisationName(utr)

      val registration = await(connector.getRegistration(utr))

      registration shouldBe Some(DesRegistrationResponse(isAnASAgent = true, None, None, None, businessAddress, Some("agent1@example.com")))
    }

    "return registration details without postcode for a UTR that is known by DES" in {
      registrationExistsWithNoPostcode(utr)

      val registration = await(connector.getRegistration(utr))

      registration shouldBe Some(DesRegistrationResponse(isAnASAgent = true, None, None, None,
        BusinessAddress("AddressLine1 A", None, None, None, None, "GB"), Some("agent1@example.com")))
    }

    "return registration details without email for a UTR that is known by DES" in {
      registrationExistsWithNoEmail(utr)

      val registration = await(connector.getRegistration(utr))

      registration shouldBe Some(DesRegistrationResponse(isAnASAgent = false, None, None, None,
        BusinessAddress("AddressLine1 A", None, None, None, Some("AA1 1AA"), "GB"), None))
    }

    "not return a registration for a UTR that is unknown to DES" in {
      registrationDoesNotExist(utr)

      val registration = await(connector.getRegistration(utr))

      registration shouldBe None
    }

    "audit the request and response" in new MockAuditingContext {
      givenCleanMetricRegistry()
      val connector: DesConnector =
        new DesConnector(environment, bearerToken, new URL(s"http://localhost:$wireMockPort"), wsHttp, wsHttp, metrics)
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

  "getAgentRecord" should {
    "return agent record for a organisation UTR that is known by DES" in {
      agentRecordExists(utr)

      val agentRecord = await(connector.getAgentRecordDetails(utr))

      agentRecord shouldBe AgentRecord(
        arn = Arn("TARN0000001"),
        isAnASAgent = true,
        agencyName = "My Agency",
        agencyAddress = model.Address(
          addressLine1 = "Flat 1",
          addressLine2 = Some("1 Some Street"),
          addressLine3 = Some("Anytown"),
          addressLine4 = Some("County"),
          postcode = "AA1 2AA",
          countryCode = "GB"),
        agencyEmail = "agency@example.com",
        businessPostcode = "TF3 4ER",
        phoneNumber = Some("0123 456 7890"))
    }

    "not return a agent record for a UTR that is unknown to DES" in {
      agentRecordDoesNotExist(utr)

      an[NotFoundException] shouldBe thrownBy(await(connector.getAgentRecordDetails(utr)))
    }

    "audit the request and response" in new MockAuditingContext {
      givenCleanMetricRegistry()
      val connector: DesConnector =
        new DesConnector(environment, bearerToken, new URL(s"http://localhost:$wireMockPort"), wsHttp, wsHttp, metrics)
      agentRecordExists(utr)

      await(connector.getAgentRecordDetails(utr))

      val auditEvent = capturedEvent()
      auditEvent.request.tags("path") shouldBe s"$wireMockBaseUrl/registration/personal-details/utr/${utr.value}"
      auditEvent.auditType shouldBe "OutboundCall"

      val responseJson = Json.parse(auditEvent.response.detail("responseMessage"))
      (responseJson \ "agencyDetails" \ "agencyAddress" \ "postalCode").as[String] shouldBe "AA1 2AA"
      (responseJson \ "isAnASAgent").as[Boolean] shouldBe true
      verifyTimerExistsAndBeenUpdated("ConsumedAPI-DES-GetAgentRecord-GET")
    }
  }

  "getCorporationTaxUtr" should {
    "return CT UTR for a company registration number that is known by DES" in {
      ctUtrRecordExists(crn)

      val ctUtr = await(connector.getCorporationTaxUtr(crn))

      ctUtr shouldBe utr
    }

    "not return a CT UTR for a a company registration number that is unknown to DES" in {
      ctUtrRecordDoesNotExist(crn)

      an[NotFoundException] shouldBe thrownBy(await(connector.getCorporationTaxUtr(crn)))
    }

    "return BadRequestException for an invalid crn" in {
      crnIsInvalid(Crn("1234"))

      an[BadRequestException] shouldBe thrownBy(await(connector.getCorporationTaxUtr(Crn("1234"))))
    }

    "return 5xx exception if DES fails to respond" in {
      ctUtrRecordFails()

      an[Upstream5xxResponse] shouldBe thrownBy(await(connector.getCorporationTaxUtr(crn)))
    }
  }

  def request = DesSubscriptionRequest(
    agencyName = "My Agency",
    agencyAddress = Address(addressLine1 = "1 Some Street", addressLine2 = Some("MyTown"), postalCode = "AA1 1AA", countryCode = "GB"),
    agencyEmail = "agency@example.com",
    telephoneNumber = Some("0123 456 7890"))

  trait MockAuditingContext extends MockitoSugar with Eventually {
    private val mockAuditConnector = mock[AuditConnector]

    val wsHttp = new HttpPost with HttpGet with WSPost with WSGet with HttpAuditing {
      val auditConnector = mockAuditConnector
      val appName = "agent-subscription"
      val actorSystem = ActorSystem()
      override val hooks = Seq(AuditingHook)
      override protected def configuration: Option[Config] = None
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