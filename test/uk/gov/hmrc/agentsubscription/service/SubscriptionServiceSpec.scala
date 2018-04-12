/*
 * Copyright 2018 HM Revenue & Customs
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

import org.mockito.ArgumentMatchers.{ any, anyString, eq => eqs, contains }
import org.mockito.Mockito.{ verify, when }
import org.scalatest.concurrent.Eventually
import play.api.libs.json.{ JsObject, Json }
import play.api.test.FakeRequest
import uk.gov.hmrc.agentmtdidentifiers.model.{ Arn, Utr }
import uk.gov.hmrc.agentsubscription.audit.AgentSubscriptionEvent.AgentSubscription
import uk.gov.hmrc.agentsubscription.audit.AuditService
import uk.gov.hmrc.agentsubscription.connectors.{ EnrolmentRequest, Address => _, _ }
import uk.gov.hmrc.agentsubscription.model._
import uk.gov.hmrc.agentsubscription.repository.RecoveryRepository
import uk.gov.hmrc.agentsubscription.support.ResettingMockitoSugar
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.GatewayTimeoutException
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ ExecutionContext, Future }

class SubscriptionServiceSpec extends UnitSpec with ResettingMockitoSugar with Eventually {

  private val desConnector = resettingMock[DesConnector]
  private val taxEnrolmentConnector = resettingMock[TaxEnrolmentsConnector]
  private val auditService = resettingMock[AuditService]
  private val recoveryRepository = resettingMock[RecoveryRepository]

  private val authIds = AuthIds("userId", "groupId")

  private val service = new SubscriptionService(desConnector, taxEnrolmentConnector, auditService, recoveryRepository)
  private implicit val hc = HeaderCarrier()

  private implicit val fakeRequest = FakeRequest("POST", "/agent-subscription/subscription")

  "subscribeAgentToMtd" should {
    "audit appropriate values" in {
      val businessUtr = Utr("4000000009")
      val businessPostcode = "AA1 1AA"

      val arn = "ARN0001"

      subscriptionWillBeCreated(businessUtr, businessPostcode, arn)

      val subscriptionRequest = SubscriptionRequest(
        businessUtr,
        KnownFacts(businessPostcode),
        Agency(
          "Test Agency",
          Address("1 Test Street", Some("address line 2"), Some("address line 3"), Some("address line 4"), postcode = "BB1 1BB", countryCode = "GB"),
          "01234 567890",
          "testagency@example.com"))
      await(service.subscribeAgentToMtd(subscriptionRequest, authIds))

      val expectedExtraDetail = Json.parse(
        s"""
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
          |  "agencyTelephoneNumber": "01234 567890",
          |  "utr": "${businessUtr.value}"
          |}
          |""".stripMargin).asInstanceOf[JsObject]
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

      subscriptionWillBeCreated(utr, businessPostcode, arn.value)

      val subscriptionRequest = SubscriptionRequest(
        utr,
        KnownFacts(businessPostcode),
        Agency(
          "Test Agency",
          Address("1 Test Street", Some("address line 2"), Some("address line 3"), Some("address line 4"), postcode = agencyPostcode, countryCode = "GB"),
          "01234 567890",
          "testagency@example.com"))

      await(service.subscribeAgentToMtd(subscriptionRequest, authIds))

      verify(taxEnrolmentConnector).sendKnownFacts(eqs(arn.value), eqs(agencyPostcode))(eqs(hc), any[ExecutionContext])

      val expectedEnrolmentRequest = EnrolmentRequest(authIds.userId, "principal", "Test Agency", Seq(KnownFact("AgencyPostcode", agencyPostcode)))
      verify(taxEnrolmentConnector).enrol(anyString, eqs(arn), eqs(expectedEnrolmentRequest))(eqs(hc), any[ExecutionContext])
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
            Address("1 Test Street", Some("address line 2"), Some("address line 3"), Some("address line 4"), postcode = agencyPostcode, countryCode = "GB"),
            "01234 567890",
            "testagency@example.com"))

        val thrown = intercept[IllegalStateException](
          await(service.subscribeAgentToMtd(subscriptionRequest, authIds))).getMessage

        thrown shouldBe "Failed to add known facts and enrol in EMAC for utr: 4000000009 and arn: ARN0001"

        verify(recoveryRepository).create(eqs(authIds), eqs(arn), eqs(subscriptionRequest), contains(recoveryMsgContains))(any())
      }

      "the query for existing allocated enrolments fails more than 3 times" in {
        subscriptionHasPrincipalGroupIdsFailed(utr, businessPostcode, arn.value)

        behave like addKnownFactsAndEnrolFailsMoreThan3Times("Failed to contact ES1")
      }

      "delete known facts fails more than 3 times" in {
        subscriptionDeleteKnownFactsFailed(utr, businessPostcode, arn.value)

        behave like addKnownFactsAndEnrolFailsMoreThan3Times("Failed to contact ES7")
      }

      "create known facts fails more than 3 times" in {
        subscriptionCreateKnownFactsFailed(utr, businessPostcode, arn.value)

        behave like addKnownFactsAndEnrolFailsMoreThan3Times("Failed to contact ES6")
      }

      "the call to enrol fails more than 3 times" in {
        subscriptionEnrolmentsFailed(utr, businessPostcode, arn.value)

        behave like addKnownFactsAndEnrolFailsMoreThan3Times("Failed to contact ES8")
      }

    }

  }

  private def subscriptionWillBeCreated(businessUtr: Utr, businessPostcode: String, arn: String) = {
    when(desConnector.getRegistration(eqs(businessUtr))(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful Some(DesRegistrationResponse(
        postalCode = Some(businessPostcode), isAnASAgent = false, organisationName = Some("Test Business"), None, None)))

    when(desConnector.subscribeToAgentServices(any[Utr], any[DesSubscriptionRequest])(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful Arn(arn))

    when(taxEnrolmentConnector.hasPrincipalGroupIds(eqs(Arn(arn)))(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful false)

    when(taxEnrolmentConnector.deleteKnownFacts(eqs(Arn(arn)))(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful new Integer(204))

    when(taxEnrolmentConnector.sendKnownFacts(eqs(arn), anyString)(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful new Integer(200))

    when(taxEnrolmentConnector.enrol(anyString, eqs(Arn(arn)), any[EnrolmentRequest])(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful new Integer(200))
  }

  private def subscriptionHasPrincipalGroupIdsFailed(businessUtr: Utr, businessPostcode: String, arn: String) = {
    when(desConnector.getRegistration(eqs(businessUtr))(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful Some(DesRegistrationResponse(
        postalCode = Some(businessPostcode), isAnASAgent = false, organisationName = Some("Test Business"), None, None)))

    when(desConnector.subscribeToAgentServices(any[Utr], any[DesSubscriptionRequest])(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful Arn(arn))

    when(taxEnrolmentConnector.hasPrincipalGroupIds(eqs(Arn(arn)))(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future failed new GatewayTimeoutException("Failed to contact ES1"))

    when(recoveryRepository.create(any[AuthIds](), any[Arn](), any[SubscriptionRequest](), any())(any()))
      .thenReturn(Future successful (()))
  }

  private def subscriptionDeleteKnownFactsFailed(businessUtr: Utr, businessPostcode: String, arn: String) = {
    when(desConnector.getRegistration(eqs(businessUtr))(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful Some(DesRegistrationResponse(
        postalCode = Some(businessPostcode), isAnASAgent = false, organisationName = Some("Test Business"), None, None)))

    when(desConnector.subscribeToAgentServices(any[Utr], any[DesSubscriptionRequest])(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful Arn(arn))

    when(taxEnrolmentConnector.hasPrincipalGroupIds(eqs(Arn(arn)))(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful false)

    when(taxEnrolmentConnector.deleteKnownFacts(eqs(Arn(arn)))(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future failed new GatewayTimeoutException("Failed to contact ES7"))

    when(recoveryRepository.create(any[AuthIds](), any[Arn](), any[SubscriptionRequest](), any())(any()))
      .thenReturn(Future successful (()))
  }

  private def subscriptionCreateKnownFactsFailed(businessUtr: Utr, businessPostcode: String, arn: String) = {
    when(desConnector.getRegistration(eqs(businessUtr))(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful Some(DesRegistrationResponse(
        postalCode = Some(businessPostcode), isAnASAgent = false, organisationName = Some("Test Business"), None, None)))

    when(desConnector.subscribeToAgentServices(any[Utr], any[DesSubscriptionRequest])(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful Arn(arn))

    when(taxEnrolmentConnector.hasPrincipalGroupIds(eqs(Arn(arn)))(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful false)

    when(taxEnrolmentConnector.deleteKnownFacts(eqs(Arn(arn)))(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful new Integer(204))

    when(taxEnrolmentConnector.sendKnownFacts(eqs(arn), anyString)(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future failed new GatewayTimeoutException("Failed to contact ES6"))

    when(recoveryRepository.create(any[AuthIds](), any[Arn](), any[SubscriptionRequest](), any())(any()))
      .thenReturn(Future successful (()))
  }

  private def subscriptionEnrolmentsFailed(businessUtr: Utr, businessPostcode: String, arn: String) = {
    when(desConnector.getRegistration(eqs(businessUtr))(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful Some(DesRegistrationResponse(
        postalCode = Some(businessPostcode), isAnASAgent = false, organisationName = Some("Test Business"), None, None)))

    when(desConnector.subscribeToAgentServices(any[Utr], any[DesSubscriptionRequest])(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful Arn(arn))

    when(taxEnrolmentConnector.hasPrincipalGroupIds(eqs(Arn(arn)))(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful false)

    when(taxEnrolmentConnector.deleteKnownFacts(eqs(Arn(arn)))(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful new Integer(204))

    when(taxEnrolmentConnector.sendKnownFacts(eqs(arn), anyString)(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful new Integer(200))

    when(taxEnrolmentConnector.enrol(anyString, eqs(Arn(arn)), any[EnrolmentRequest])(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future failed new GatewayTimeoutException("Failed to contact ES8"))
  }
}
