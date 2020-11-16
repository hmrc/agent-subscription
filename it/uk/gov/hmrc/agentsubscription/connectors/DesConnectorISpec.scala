package uk.gov.hmrc.agentsubscription.connectors

import com.kenshoo.play.metrics.Metrics
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.agentmtdidentifiers.model.{ Arn, Utr }
import uk.gov.hmrc.agentsubscription.config.AppConfig
import uk.gov.hmrc.agentsubscription.model
import uk.gov.hmrc.agentsubscription.model.{ AgentRecord, Crn }
import uk.gov.hmrc.agentsubscription.stubs.DesStubs
import uk.gov.hmrc.agentsubscription.support.{ BaseISpec, MetricsTestSupport }
import uk.gov.hmrc.domain.Vrn
import uk.gov.hmrc.http.{ HttpClient, _ }

import scala.concurrent.ExecutionContext.Implicits.global

class DesConnectorISpec extends BaseISpec with DesStubs with MetricsTestSupport with MockitoSugar {
  private implicit val hc = HeaderCarrier()
  val utr = Utr("1234567890")
  val crn = Crn("SC123456")
  val vrn = Vrn("888913457")

  private val bearerToken = "secret"
  private val environment = "test"

  override protected def expectedBearerToken = Some(bearerToken)

  override protected def expectedEnvironment = Some(environment)

  private lazy val metrics = app.injector.instanceOf[Metrics]
  private lazy val http: HttpClient = app.injector.instanceOf[HttpClient]
  private lazy val appConfig = app.injector.instanceOf[AppConfig]

  private lazy val connector: DesConnector =
    new DesConnector(appConfig, http, metrics)

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
      exception.getCause.asInstanceOf[UpstreamErrorResponse].statusCode shouldBe 409
    }

    "propagate an exception containing the utr if the agency is not registered" in {
      agencyNotRegistered(utr)

      val exception = intercept[RuntimeException] {
        await(connector.subscribeToAgentServices(utr, request))
      }

      exception.getMessage.contains(utr.value) shouldBe true
    }

  }

  "getRegistration" should {
    val businessAddress =
      BusinessAddress("AddressLine1 A", Some("AddressLine2 A"), Some("AddressLine3 A"), Some("AddressLine4 A"), Some("AA1 1AA"), "GB")

    "return registration details for a organisation UTR that is known by DES" in {
      organisationRegistrationExists(utr)

      val registration = await(connector.getRegistration(utr))

      registration shouldBe Some(DesRegistrationResponse(isAnASAgent = true, Some("My Agency"), None, Some(Arn("TARN0000001")), businessAddress, Some("agency@example.com")))
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

      an[UpstreamErrorResponse] shouldBe thrownBy(await(connector.getCorporationTaxUtr(crn)))
    }
  }

  "getVatKnownfacts" should {
    "return VAT registration date for a VAT registration number that is known by DES" in {
      vatKnownfactsRecordExists(vrn)

      await(connector.getVatKnownfacts(vrn)) shouldBe "2010-03-31"
    }

    "not return a VAT registration date for a VAT registration number that is unknown to DES" in {
      vatKnownfactsRecordDoesNotExist(vrn)

      an[NotFoundException] shouldBe thrownBy(await(connector.getVatKnownfacts(vrn)))
    }

    "return BadRequestException for an invalid vrn" in {
      vrnIsInvalid(Vrn("1234"))

      an[BadRequestException] shouldBe thrownBy(await(connector.getVatKnownfacts(Vrn("1234"))))
    }

    "return 5xx exception if DES fails to respond" in {
      vatKnownfactsRecordFails()

      an[UpstreamErrorResponse] shouldBe thrownBy(await(connector.getVatKnownfacts(vrn)))
    }
  }

  def request = DesSubscriptionRequest(
    agencyName = "My Agency",
    agencyAddress = Address(addressLine1 = "1 Some Street", addressLine2 = Some("MyTown"), postalCode = "AA1 1AA", countryCode = "GB"),
    agencyEmail = "agency@example.com",
    telephoneNumber = Some("0123 456 7890"))

}