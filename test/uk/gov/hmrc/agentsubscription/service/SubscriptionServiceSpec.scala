/*
 * Copyright 2017 HM Revenue & Customs
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
import play.api.libs.json.{JsObject, Json}
import play.api.test.FakeRequest
import uk.gov.hmrc.agentsubscription.audit.AgentSubscriptionEvent.AgentSubscription
import uk.gov.hmrc.agentsubscription.audit.AuditService
import uk.gov.hmrc.agentsubscription.connectors.{Address => _, KnownFacts => _, _}
import uk.gov.hmrc.agentsubscription.model._
import uk.gov.hmrc.agentsubscription.support.ResettingMockitoSugar
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class SubscriptionServiceSpec extends UnitSpec with ResettingMockitoSugar with Eventually {

  private val desConnector = resettingMock[DesConnector]
  private val ggAdminConnector = resettingMock[GovernmentGatewayAdminConnector]
  private val ggConnector = resettingMock[GovernmentGatewayConnector]
  private val auditService = resettingMock[AuditService]

  private val service = new SubscriptionService(desConnector, ggAdminConnector, ggConnector, auditService)

  private implicit val hc = HeaderCarrier()
  private implicit val fakeRequest = FakeRequest("POST", "/agent-subscription/subscription")

  "subscribeAgentToMtd" should {
    "audit appropriate values" in {
      val businessUtr = "4000000009"
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

      await(service.subscribeAgentToMtd(subscriptionRequest))

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
          |  "agentRegistrationNumber": "$arn",
          |  "agencyEmail": "testagency@example.com",
          |  "agencyTelephoneNumber": "01234 567890",
          |  "utr": "$businessUtr"
          |}
          |""".stripMargin).asInstanceOf[JsObject]
      eventually {
        verify(auditService)
          .auditEvent(AgentSubscription, "Agent services subscription", expectedExtraDetail)(hc, fakeRequest)
      }
    }

    "add the agency postcode, not the business postcode, to the HMRC-AS-AGENT enrolment known facts" in {
      val utr = "4000000009"
      val arn = "ARN0001"

      val businessPostcode = "BU1 1BB"
      val agencyPostcode = "AG1 1CY"

      subscriptionWillBeCreated(utr, businessPostcode, arn)

      val subscriptionRequest = SubscriptionRequest(
        utr,
        KnownFacts(businessPostcode),
        Agency(
          "Test Agency",
          Address("1 Test Street", Some("address line 2"), Some("address line 3"), Some("address line 4"), postcode = agencyPostcode, countryCode = "GB"),
          "01234 567890",
          "testagency@example.com"))

      await(service.subscribeAgentToMtd(subscriptionRequest))

      verify(ggAdminConnector).createKnownFacts(eqs(arn), eqs(agencyPostcode))(eqs(hc), any[ExecutionContext])
      verify(ggConnector).enrol(anyString, eqs(arn), eqs(agencyPostcode))(eqs(hc), any[ExecutionContext])
    }
  }

  private def subscriptionWillBeCreated(businessUtr: String, businessPostcode: String, arn: String) = {
    when(desConnector.getRegistration(eqs(businessUtr))(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful Some(DesRegistrationResponse(
        postalCode = Some(businessPostcode), isAnASAgent = false, organisationName = Some("Test Business"), None)))

    when(desConnector.subscribeToAgentServices(anyString, any[DesSubscriptionRequest])(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful Arn(arn))

    when(ggAdminConnector.createKnownFacts(eqs(arn), anyString)(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful new Integer(200))

    when(ggConnector.enrol(anyString, anyString, anyString)(eqs(hc), any[ExecutionContext]))
      .thenReturn(Future successful new Integer(200))
  }
}
