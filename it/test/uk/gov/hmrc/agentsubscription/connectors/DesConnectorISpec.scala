/*
 * Copyright 2024 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.agentsubscription.connectors

import play.api.test.Helpers._
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscription.config.AppConfig
import uk.gov.hmrc.agentsubscription.model
import uk.gov.hmrc.agentsubscription.model.AgentRecord
import uk.gov.hmrc.agentsubscription.model.AmlsSubscriptionRecord
import uk.gov.hmrc.agentsubscription.model.Crn
import uk.gov.hmrc.agentsubscription.stubs.DesStubs
import uk.gov.hmrc.agentsubscription.support.BaseISpec
import uk.gov.hmrc.agentsubscription.support.MetricsTestSupport
import uk.gov.hmrc.domain.Vrn
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global

class DesConnectorISpec
extends BaseISpec
with DesStubs
with MetricsTestSupport {

  val utr = Utr("1234567890")
  val crn = Crn("SC123456")
  val vrn = Vrn("888913457")

  private val bearerToken = "secret"
  private val environment = "test"

  override protected def expectedBearerToken = Some(bearerToken)

  override protected def expectedEnvironment = Some(environment)

  private lazy val metrics = app.injector.instanceOf[Metrics]
  private lazy val http: HttpClientV2 = app.injector.instanceOf[HttpClientV2]
  private lazy val appConfig = app.injector.instanceOf[AppConfig]

  private lazy val connector: DesConnector =
    new DesConnector(
      appConfig,
      http,
      metrics
    )

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
    val businessAddress = DesBusinessAddress(
      "AddressLine1 A",
      Some("AddressLine2 A"),
      Some("AddressLine3 A"),
      Some("AddressLine4 A"),
      Some("AA1 1AA"),
      "GB"
    )

    "return registration details for a organisation UTR that is known by DES" in {
      organisationRegistrationExists(utr)

      val registration = await(connector.getRegistration(utr))

      registration shouldBe Some(
        DesRegistrationResponse(
          isAnASAgent = true,
          Some("My Agency"),
          None,
          Some(Arn("TARN0000001")),
          businessAddress,
          Some("agency@example.com"),
          Some("01273111111"),
          Some("safeId")
        )
      )
    }

    "return registration details for an individual UTR that is known by DES" in {
      individualRegistrationExists(utr)

      val registration = await(connector.getRegistration(utr))

      registration shouldBe Some(
        DesRegistrationResponse(
          isAnASAgent = true,
          None,
          Some(DesIndividual("First", "Last")),
          Some(Arn("AARN0000002")),
          businessAddress,
          Some("individual@example.com"),
          Some("01273111111"),
          Some("safeId")
        )
      )
    }

    "return registration details without organisationName for a UTR that is known by DES" in {
      registrationExistsWithNoOrganisationName(utr)

      val registration = await(connector.getRegistration(utr))

      registration shouldBe Some(
        DesRegistrationResponse(
          isAnASAgent = true,
          None,
          None,
          None,
          businessAddress,
          Some("agent1@example.com"),
          Some("01273111111"),
          None
        )
      )
    }

    "return registration details without postcode for a UTR that is known by DES" in {
      registrationExistsWithNoPostcode(utr)

      val registration = await(connector.getRegistration(utr))

      registration shouldBe Some(
        DesRegistrationResponse(
          isAnASAgent = true,
          None,
          None,
          None,
          DesBusinessAddress(
            "AddressLine1 A",
            None,
            None,
            None,
            None,
            "GB"
          ),
          Some("agent1@example.com"),
          None,
          None
        )
      )
    }

    "return registration details without email for a UTR that is known by DES" in {
      registrationExistsWithNoEmail(utr)

      val registration = await(connector.getRegistration(utr))

      registration shouldBe Some(
        DesRegistrationResponse(
          isAnASAgent = false,
          None,
          None,
          None,
          DesBusinessAddress(
            "AddressLine1 A",
            None,
            None,
            None,
            Some("AA1 1AA"),
            "GB"
          ),
          None,
          None,
          None
        )
      )
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
          countryCode = "GB"
        ),
        agencyEmail = "agency@example.com",
        businessPostcode = "TF3 4ER",
        phoneNumber = Some("0123 456 7890")
      )
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

  "getAmlsSubscription" should {
    "return AmlsSubscriptionRecord when AmlsRegistrationNumber is known in ETMP" in {

      amlsSubscriptionRecordExists("XAML00000200000")

      def parseDate(str: String) = Some(LocalDate.parse(str))

      val result = await(connector.getAmlsSubscriptionStatus("XAML00000200000"))

      result shouldBe AmlsSubscriptionRecord(
        "Approved",
        "xyz",
        parseDate("2021-01-01"),
        parseDate("2021-12-31"),
        Some(false)
      )
    }

    "return NotFoundException when AmlsRegistrationNumber is not known in ETMP" in {

      amlsSubscriptionRecordFails("XAML00000200000", 404)

      an[NotFoundException] shouldBe thrownBy(await(connector.getAmlsSubscriptionStatus("XAML00000200000")))
    }

    "return a BadRequestException when AmlsRegistrationNumber is invalid in DES" in {

      amlsSubscriptionRecordFails("XXX", 400)

      an[BadRequestException] shouldBe thrownBy(await(connector.getAmlsSubscriptionStatus("XXX")))
    }
  }

  def request = DesSubscriptionRequest(
    agencyName = "My Agency",
    agencyAddress = Address(
      addressLine1 = "1 Some Street",
      addressLine2 = Some("MyTown"),
      postalCode = "AA1 1AA",
      countryCode = "GB"
    ),
    agencyEmail = "agency@example.com",
    telephoneNumber = Some("0123 456 7890")
  )

}
