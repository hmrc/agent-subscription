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

package uk.gov.hmrc.agentsubscription.service

import org.mockito.ArgumentMatchers.{any, anyString, eq => eqs}
import org.mockito.Mockito.{verify, when}
import org.scalatest.concurrent.Eventually
import play.api.i18n.Lang
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr}
import uk.gov.hmrc.agentsubscription.audit.{AgentSubscription, AuditService}
import uk.gov.hmrc.agentsubscription.auth.AuthActions.AuthIds
import uk.gov.hmrc.agentsubscription.connectors.{Address => _, EnrolmentRequest, _}
import uk.gov.hmrc.agentsubscription.model._
import uk.gov.hmrc.agentsubscription.repository.SubscriptionJourneyRepository
import uk.gov.hmrc.agentsubscription.support.{ResettingMockitoSugar, UnitSpec}
import uk.gov.hmrc.http.{GatewayTimeoutException, HeaderCarrier}

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class SubscriptionServiceSpec extends UnitSpec with ResettingMockitoSugar with Eventually {

  private val desConnector = resettingMock[DesConnector]
  private val taxEnrolmentConnector = resettingMock[TaxEnrolmentsConnector]
  private val auditService = resettingMock[AuditService]
  private val subscriptionJourneyRepository = resettingMock[SubscriptionJourneyRepository]
  private val agentAssuranceConnector = resettingMock[AgentAssuranceConnector]
  private val agentOverseasAppConn = resettingMock[AgentOverseasApplicationConnector]
  private val emailConnector = resettingMock[EmailConnector]
  private val mappingConnector = resettingMock[MappingConnector]

  private val authIds = AuthIds("userId", "groupId")

  private val service = new SubscriptionService(
    desConnector,
    taxEnrolmentConnector,
    auditService,
    subscriptionJourneyRepository,
    agentAssuranceConnector,
    agentOverseasAppConn,
    emailConnector,
    mappingConnector
  )
  private implicit val hc: HeaderCarrier = HeaderCarrier()

  private implicit val fakeRequest: FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest("POST", "/agent-subscription/subscription")

  "CreateSubscription" should {
    val businessUtr = Utr("4000000009")
    val businessPostcode = "AA1 1AA"
    val arn = "ARN0001"
    val amlsDetails = AmlsDetails(
      "supervisory",
      membershipNumber = Some("12345"),
      appliedOn = None,
      membershipExpiresOn = Some(LocalDate.now()),
      amlsSafeId = Some("amlsSafeId"),
      agentBPRSafeId = Some("agentBPRSafeId")
    )

    "audit appropriate values" in {

      subscriptionWillBeCreated(businessUtr, businessPostcode, arn, amlsDetails)

      val subscriptionRequest = SubscriptionRequest(
        businessUtr,
        KnownFacts(businessPostcode),
        Agency(
          "Test Agency",
          Address(
            "1 Test Street",
            Some("address line 2"),
            Some("address line 3"),
            Some("address line 4"),
            postcode = "BB1 1BB",
            countryCode = "GB"
          ),
          Some("01234 567890"),
          "testagency@example.com"
        ),
        Some(Lang("en")),
        Some(amlsDetails)
      )

      await(service.createSubscription(subscriptionRequest, authIds))

      val expectedExtraDetail = Json
        .parse(s"""
                  |{
                  |  "agencyName": "Test Agency",
                  |  "agencyAddress": {
                  |     "addressLine1": "1 Test Street",
                  |     "addressLine2": "address line 2",
                  |     "addressLine3": "address line 3",
                  |     "addressLine4": "address line 4",
                  |     "postcode": "BB1 1BB",
                  |     "countryCode": "GB"
                  |  },
                  |  "agentReferenceNumber": "$arn",
                  |  "agencyEmail": "testagency@example.com",
                  |  "utr": "${businessUtr.value}",
                  |  "amlsDetails": {
                  |      "supervisoryBody":"supervisory",
                  |      "membershipNumber":"12345",
                  |      "membershipExpiresOn":"${LocalDate.now()}",
                  |      "amlsSafeId": "amlsSafeId",
                  |      "agentBPRSafeId": "agentBPRSafeId"
                  |   }
                  |}
                  |""".stripMargin)
        .asInstanceOf[JsObject]
      eventually {
        verify(auditService)
          .auditEvent(AgentSubscription, "Agent services subscription", expectedExtraDetail)(hc, fakeRequest)
      }
    }

    "add the agency postcode, not the business postcode, to the HMRC-AS-AGENT enrolment known facts" in {
      val utr = Utr("4000000009")
      val arn = Arn("ARN0001")

      val businessPostcode = "BU1 1BB"
      val agencyPostcode = "AG1 1CY"

      subscriptionWillBeCreated(utr, businessPostcode, arn.value, amlsDetails)

      val subscriptionRequest = SubscriptionRequest(
        utr,
        KnownFacts(businessPostcode),
        Agency(
          "Test Agency",
          Address(
            "1 Test Street",
            Some("address line 2"),
            Some("address line 3"),
            Some("address line 4"),
            postcode = agencyPostcode,
            countryCode = "GB"
          ),
          Some("01234 567890"),
          "testagency@example.com"
        ),
        Some(Lang("en")),
        Some(amlsDetails)
      )

      await(service.createSubscription(subscriptionRequest, authIds))

      verify(taxEnrolmentConnector)
        .addKnownFacts(eqs(arn.value), eqs("AgencyPostcode"), eqs(agencyPostcode))(eqs(hc), any[ExecutionContext])

      val expectedEnrolmentRequest =
        EnrolmentRequest(authIds.userId, "principal", "Test Agency", Seq(KnownFact("AgencyPostcode", agencyPostcode)))
      verify(taxEnrolmentConnector)
        .enrol(anyString, eqs(arn), eqs(expectedEnrolmentRequest))(eqs(hc), any[ExecutionContext])
    }

    "fail after retrying 3 times to add known facts and enrol" when {
      val utr = Utr("4000000009")
      val arn = Arn("ARN0001")

      val businessPostcode = "BU1 1BB"
      val agencyPostcode = "AG1 1CY"

      def addKnownFactsAndEnrolFailsMoreThan3Times(recoveryMsgContains: String): Unit = {
        val subscriptionRequest = SubscriptionRequest(
          utr,
          KnownFacts(businessPostcode),
          Agency(
            "Test Agency",
            Address(
              "1 Test Street",
              Some("address line 2"),
              Some("address line 3"),
              Some("address line 4"),
              postcode = agencyPostcode,
              countryCode = "GB"
            ),
            Some("01234 567890"),
            "testagency@example.com"
          ),
          Some(Lang("en")),
          Some(amlsDetails)
        )

        val thrown =
          intercept[IllegalStateException](await(service.createSubscription(subscriptionRequest, authIds))).getMessage

        thrown shouldBe "Failed to add known facts and enrol in EMAC for utr: 4000000009 and arn: ARN0001"

        ()
      }

      "the query for existing allocated enrolments fails more than 3 times" in {
        subscriptionHasPrincipalGroupIdsFailed(utr, businessPostcode, arn.value, amlsDetails)

        behave like addKnownFactsAndEnrolFailsMoreThan3Times("Failed to contact ES1")
      }

      "delete known facts fails more than 3 times" in {
        subscriptionDeleteKnownFactsFailed(utr, businessPostcode, arn.value, amlsDetails)

        behave like addKnownFactsAndEnrolFailsMoreThan3Times("Failed to contact ES7")
      }

      "create known facts fails more than 3 times" in {
        subscriptionCreateKnownFactsFailed(utr, businessPostcode, arn.value, amlsDetails)

        behave like addKnownFactsAndEnrolFailsMoreThan3Times("Failed to contact ES6")
      }

      "the call to enrol fails more than 3 times" in {
        subscriptionEnrolmentsFailed(utr, businessPostcode, arn.value, amlsDetails)

        behave like addKnownFactsAndEnrolFailsMoreThan3Times("Failed to contact ES8")
      }

    }

  }

  private def subscriptionWillBeCreated(
    businessUtr: Utr,
    businessPostcode: String,
    arn: String,
    amlsDetails: AmlsDetails
  ) = {
    when(desConnector.getRegistration(eqs(businessUtr))(eqs(hc), any[ExecutionContext]))
      .thenReturn(
        Future successful Some(
          DesRegistrationResponse(
            isAnASAgent = false,
            organisationName = Some("Test Business"),
            None,
            None,
            DesBusinessAddress(
              "AddressLine1 A",
              Some("AddressLine2 A"),
              Some("AddressLine3 A"),
              Some("AddressLine4 A"),
              Some(businessPostcode),
              "GB"
            ),
            None,
            None,
            Some("safeId")
          )
        )
      )

    when(desConnector.subscribeToAgentServices(any[Utr], any[DesSubscriptionRequest])(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful Arn(arn))

    when(subscriptionJourneyRepository.delete(any[String]))
      .thenReturn(Future successful (Some(1L)))

    when(mappingConnector.createMappings(any[Arn])(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful (()))

    when(mappingConnector.createMappingDetails(any[Arn])(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful [Unit] (()))

    when(taxEnrolmentConnector.hasPrincipalGroupIds(eqs(Arn(arn)))(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful false)

    when(taxEnrolmentConnector.deleteKnownFacts(eqs(Arn(arn)))(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful Integer.valueOf(204))

    when(taxEnrolmentConnector.addKnownFacts(eqs(arn), anyString, anyString)(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful Integer.valueOf(200))

    when(taxEnrolmentConnector.enrol(anyString, eqs(Arn(arn)), any[EnrolmentRequest])(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful Integer.valueOf(200))

    when(agentAssuranceConnector.createAmls(any[Utr], any[AmlsDetails])(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful true)

    when(agentAssuranceConnector.updateAmls(any[Utr], any[Arn])(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful Some(amlsDetails))

    when(emailConnector.sendEmail(any[EmailInformation])(any[HeaderCarrier], any[ExecutionContext]))
      .thenReturn(Future successful [Unit] (()))
  }

  private def subscriptionHasPrincipalGroupIdsFailed(
    businessUtr: Utr,
    businessPostcode: String,
    arn: String,
    amlsDetails: AmlsDetails
  ) = {
    when(desConnector.getRegistration(eqs(businessUtr))(eqs(hc), any[ExecutionContext]))
      .thenReturn(
        Future successful Some(
          DesRegistrationResponse(
            isAnASAgent = false,
            organisationName = Some("Test Business"),
            None,
            None,
            DesBusinessAddress(
              "AddressLine1 A",
              Some("AddressLine2 A"),
              Some("AddressLine3 A"),
              Some("AddressLine4 A"),
              Some(businessPostcode),
              "GB"
            ),
            None,
            None,
            Some("safeId")
          )
        )
      )

    when(desConnector.subscribeToAgentServices(any[Utr], any[DesSubscriptionRequest])(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful Arn(arn))

    when(subscriptionJourneyRepository.delete(any[String]))
      .thenReturn(Future successful (Some(1L)))

    when(mappingConnector.createMappings(any[Arn])(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful (()))

    when(mappingConnector.createMappingDetails(any[Arn])(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful [Unit] (()))

    when(taxEnrolmentConnector.hasPrincipalGroupIds(eqs(Arn(arn)))(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future failed new GatewayTimeoutException("Failed to contact ES1"))

    when(agentAssuranceConnector.createAmls(any[Utr], any[AmlsDetails])(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful true)

    when(agentAssuranceConnector.updateAmls(any[Utr], any[Arn])(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful Some(amlsDetails))
  }

  private def subscriptionDeleteKnownFactsFailed(
    businessUtr: Utr,
    businessPostcode: String,
    arn: String,
    amlsDetails: AmlsDetails
  ) = {
    when(desConnector.getRegistration(eqs(businessUtr))(eqs(hc), any[ExecutionContext]))
      .thenReturn(
        Future successful Some(
          DesRegistrationResponse(
            isAnASAgent = false,
            organisationName = Some("Test Business"),
            None,
            None,
            DesBusinessAddress(
              "AddressLine1 A",
              Some("AddressLine2 A"),
              Some("AddressLine3 A"),
              Some("AddressLine4 A"),
              Some(businessPostcode),
              "GB"
            ),
            None,
            None,
            Some("safeId")
          )
        )
      )

    when(desConnector.subscribeToAgentServices(any[Utr], any[DesSubscriptionRequest])(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful Arn(arn))

    when(subscriptionJourneyRepository.delete(any[String]))
      .thenReturn(Future successful (Some(1L)))

    when(mappingConnector.createMappings(any[Arn])(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful (()))

    when(mappingConnector.createMappingDetails(any[Arn])(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful [Unit] (()))

    when(taxEnrolmentConnector.hasPrincipalGroupIds(eqs(Arn(arn)))(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful false)

    when(taxEnrolmentConnector.deleteKnownFacts(eqs(Arn(arn)))(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future failed new GatewayTimeoutException("Failed to contact ES7"))

    when(agentAssuranceConnector.createAmls(any[Utr], any[AmlsDetails])(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful true)

    when(agentAssuranceConnector.updateAmls(any[Utr], any[Arn])(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful Some(amlsDetails))
  }

  private def subscriptionCreateKnownFactsFailed(
    businessUtr: Utr,
    businessPostcode: String,
    arn: String,
    amlsDetails: AmlsDetails
  ) = {
    when(desConnector.getRegistration(eqs(businessUtr))(eqs(hc), any[ExecutionContext]))
      .thenReturn(
        Future successful Some(
          DesRegistrationResponse(
            isAnASAgent = false,
            organisationName = Some("Test Business"),
            None,
            None,
            DesBusinessAddress(
              "AddressLine1 A",
              Some("AddressLine2 A"),
              Some("AddressLine3 A"),
              Some("AddressLine4 A"),
              Some(businessPostcode),
              "GB"
            ),
            None,
            None,
            Some("safeId")
          )
        )
      )

    when(desConnector.subscribeToAgentServices(any[Utr], any[DesSubscriptionRequest])(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful Arn(arn))

    when(subscriptionJourneyRepository.delete(any[String]))
      .thenReturn(Future successful (Some(1L)))

    when(mappingConnector.createMappings(any[Arn])(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful (()))

    when(mappingConnector.createMappingDetails(any[Arn])(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful [Unit] (()))

    when(taxEnrolmentConnector.hasPrincipalGroupIds(eqs(Arn(arn)))(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful false)

    when(taxEnrolmentConnector.deleteKnownFacts(eqs(Arn(arn)))(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful Integer.valueOf(204))

    when(taxEnrolmentConnector.addKnownFacts(eqs(arn), anyString, anyString)(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future failed new GatewayTimeoutException("Failed to contact ES6"))

    when(agentAssuranceConnector.createAmls(any[Utr], any[AmlsDetails])(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful true)

    when(agentAssuranceConnector.updateAmls(any[Utr], any[Arn])(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful Some(amlsDetails))
  }

  private def subscriptionEnrolmentsFailed(
    businessUtr: Utr,
    businessPostcode: String,
    arn: String,
    amlsDetails: AmlsDetails
  ) = {
    when(desConnector.getRegistration(eqs(businessUtr))(eqs(hc), any[ExecutionContext]))
      .thenReturn(
        Future successful Some(
          DesRegistrationResponse(
            isAnASAgent = false,
            organisationName = Some("Test Business"),
            None,
            None,
            DesBusinessAddress(
              "AddressLine1 A",
              Some("AddressLine2 A"),
              Some("AddressLine3 A"),
              Some("AddressLine4 A"),
              Some(businessPostcode),
              "GB"
            ),
            None,
            None,
            Some("safeId")
          )
        )
      )

    when(desConnector.subscribeToAgentServices(any[Utr], any[DesSubscriptionRequest])(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful Arn(arn))

    when(subscriptionJourneyRepository.delete(any[String]))
      .thenReturn(Future successful (Some(1L)))

    when(mappingConnector.createMappings(any[Arn])(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful (()))

    when(mappingConnector.createMappingDetails(any[Arn])(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful [Unit] (()))

    when(taxEnrolmentConnector.hasPrincipalGroupIds(eqs(Arn(arn)))(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful false)

    when(taxEnrolmentConnector.deleteKnownFacts(eqs(Arn(arn)))(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful Integer.valueOf(204))

    when(taxEnrolmentConnector.addKnownFacts(eqs(arn), anyString, anyString)(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful Integer.valueOf(200))

    when(taxEnrolmentConnector.enrol(anyString, eqs(Arn(arn)), any[EnrolmentRequest])(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future failed new GatewayTimeoutException("Failed to contact ES8"))

    when(agentAssuranceConnector.createAmls(any[Utr], any[AmlsDetails])(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful true)

    when(agentAssuranceConnector.updateAmls(any[Utr], any[Arn])(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful Some(amlsDetails))
  }
}
