package uk.gov.hmrc.agentsubscription.connectors

import java.net.URL

import org.mockito.ArgumentMatchers.any
import org.mockito.{ArgumentCaptor, ArgumentMatchers, Matchers}
import org.mockito.Mockito.verify
import org.scalatest.concurrent.Eventually
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.OneAppPerSuite
import play.api.libs.json.Json
import uk.gov.hmrc.agentsubscription.WSHttp
import uk.gov.hmrc.agentsubscription.model.Arn
import uk.gov.hmrc.agentsubscription.stubs.DesStubs
import uk.gov.hmrc.agentsubscription.support.WireMockSupport
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.MergedDataEvent
import uk.gov.hmrc.play.http.{HeaderCarrier, NotFoundException, Upstream4xxResponse}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global

class DesConnectorISpec extends UnitSpec with OneAppPerSuite with WireMockSupport with DesStubs {
  private implicit val hc = HeaderCarrier()
  val utr = "1234567890"

  private val bearerToken = "auth-token"
  private val environment = "des-env"

  override protected def expectedBearerToken = Some(bearerToken)
  override protected def expectedEnvironment = Some(environment)

  private lazy val connector: DesConnector =
    new DesConnector(environment, bearerToken, new URL(s"http://localhost:$wireMockPort"), WSHttp)

  "subscribeToAgentServices" should {
    "return an ARN when subscription is successful" in {
      subscriptionSucceeds(utr, request)

      val result = await(connector.subscribeToAgentServices(utr, request))

      result shouldBe Arn("ARN0001")
    }

    "propagate an exception if there is a duplicate submission" in {
      subscriptionAlreadyExists(utr)

      val exception = intercept[Upstream4xxResponse] {
        await(connector.subscribeToAgentServices(utr, request))
      }

      exception.upstreamResponseCode shouldBe 409
    }

    "propagate an exception if the agency is not registered" in {
      agencyNotRegistered(utr)

      intercept[NotFoundException] {
        await(connector.subscribeToAgentServices(utr, request))
      }
    }

    "audit the request and response" in {
      pending
    }

  }

  "getRegistration" should {
    "return registration details for a organisation UTR that is known by DES" in {
      organisationRegistrationExists(utr)

      val registration = await(connector.getRegistration(utr))

      registration shouldBe Some(DesRegistrationResponse(Some("AA1 1AA"), isAnASAgent = true, Some("My Agency"), None))
    }

    "return registration details for an individual UTR that is known by DES" in {
      individualRegistrationExists(utr)

      val registration = await(connector.getRegistration(utr))

      registration shouldBe Some(DesRegistrationResponse(Some("AA1 1AA"), isAnASAgent = true, None, Some(DesIndividual("First", "Last"))))
    }

    "return registration details without organisationName for a UTR that is known by DES" in {
      registrationExistsWithNoOrganisationName(utr)

      val registration = await(connector.getRegistration(utr))

      registration shouldBe Some(DesRegistrationResponse(Some("AA1 1AA"), isAnASAgent = true, None, None))
    }

    "return registration details without postcode for a UTR that is known by DES" in {
      registrationExistsWithNoPostcode(utr)

      val registration = await(connector.getRegistration(utr))

      registration shouldBe Some(DesRegistrationResponse(None, isAnASAgent = true, None, None))
    }


    "not return a registration for a UTR that is unknown to DES" in {
      registrationDoesNotExist(utr)

      val registration = await(connector.getRegistration(utr))

      registration shouldBe None
    }

    "audit the request and response" in new MockAuditingContext {
      val connector: DesConnector =
        new DesConnector(environment, bearerToken, new URL(s"http://localhost:$wireMockPort"), wsHttp)
      organisationRegistrationExists(utr)

      await(connector.getRegistration(utr))

      val auditEvent = capturedEvent()
      auditEvent.request.tags("path") shouldBe s"$wireMockBaseUrl/registration/individual/utr/$utr"
      auditEvent.auditType shouldBe "OutboundCall"

      val responseJson = Json.parse(auditEvent.response.detail("responseMessage"))
      (responseJson \ "address" \ "postalCode").as[String] shouldBe "AA1 1AA"
      (responseJson \ "isAnASAgent").as[Boolean] shouldBe true
      (responseJson \ "organisation" \ "organisationName").as[String] shouldBe "My Agency"
    }
  }

  def request = DesSubscriptionRequest(agencyName = "My Agency",
                                       agencyAddress =Address(addressLine1 = "1 Some Street", addressLine2 = Some("MyTown"), postalCode = "AA1 1AA", countryCode = "GB"),
                                       agencyEmail = "agency@example.com",
                                       telephoneNumber = "0123 456 7890")


}

trait MockAuditingContext extends MockitoSugar with Eventually {
  private val mockAuditConnector = mock[AuditConnector]
  val wsHttp = new WSHttp {
    override def auditConnector = mockAuditConnector
  }

  def capturedEvent(): MergedDataEvent = {
    eventually[MergedDataEvent]({
      val captor = ArgumentCaptor.forClass(classOf[MergedDataEvent])
      verify(mockAuditConnector).sendMergedEvent(captor.capture())(any[HeaderCarrier], any[ExecutionContext])
      captor.getValue
    })
  }
}