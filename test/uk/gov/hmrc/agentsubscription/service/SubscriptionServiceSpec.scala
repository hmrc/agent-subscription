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
import org.scalatest.mock.MockitoSugar
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Request
import play.api.test.FakeRequest
import uk.gov.hmrc.agentsubscription.audit.AgentSubscriptionEvent.{AgentSubscription, AgentSubscriptionEvent}
import uk.gov.hmrc.agentsubscription.audit.AuditService
import uk.gov.hmrc.agentsubscription.connectors.{Address => _, KnownFacts => _, _}
import uk.gov.hmrc.agentsubscription.model._
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class SubscriptionServiceSpec extends UnitSpec with MockitoSugar {

  implicit val hc = HeaderCarrier()
  implicit val fakeRequest = FakeRequest("POST", "/agent-subscription/subscription")

  "subscribeAgentToMtd" should {
    "audit appropriate values" in {
      val utr = "4000000009"

      val desConnector = mock[DesConnector]
      val ggAdminConnector = mock[GovernmentGatewayAdminConnector]
      val ggConnector = mock[GovernmentGatewayConnector]
      val auditService = mock[AuditService]

      when(desConnector.getRegistration(eqs(utr))(eqs(hc), any[ExecutionContext]))
        .thenReturn(Future successful Some(DesRegistrationResponse(
          postalCode = Some("AA1 1AA"), isAnASAgent = false, organisationName = Some("Test Business"), None)))

      when(desConnector.subscribeToAgentServices(anyString, any[DesSubscriptionRequest])(eqs(hc), any[ExecutionContext]))
        .thenReturn(Future successful Arn("ARN0001"))

      when(ggAdminConnector.createKnownFacts(eqs("ARN0001"), anyString)(eqs(hc), any[ExecutionContext]))
        .thenReturn(Future successful new Integer(200))

      when(ggConnector.enrol(anyString, anyString, anyString)(eqs(hc), any[ExecutionContext]))
        .thenReturn(Future successful new Integer(200))

      when(auditService.auditEvent(any[AgentSubscriptionEvent], anyString, any[JsObject])(eqs(hc), any[Request[Any]]))
        .thenReturn(Future.successful(()))

      val service = new SubscriptionService(desConnector, ggAdminConnector, ggConnector, auditService)

      val subscriptionRequest = SubscriptionRequest(
        utr,
        KnownFacts("AA1 1AA"),
        Agency(
          "Test Agency",
          Address("1 Test Street", Some("address line 2"), Some("address line 3"), Some("address line 4"), postcode = "BB1 1BB", countryCode = "GB"),
          "01234 567890",
          "testagency@example.com"))

      await(service.subscribeAgentToMtd(subscriptionRequest))

      // TODO add these fields to expectedExtraDetail
//       {
//              safeId: "XH0000100032510",
//              agentRegistrationNumber: "GARN0000247",
//              agencyEmail: "ab@xy.com",
//              telephoneNumber: "07000000000",
//              utr: "1403050305"
//      }
      val expectedExtraDetail = Json.parse(
        """
          |{
          |  "agencyName": "Test Agency",
          |  "agencyAddress": {
          |     "addressLine1": "1 Test Street",
          |     "addressLine2": "address line 2",
          |     "addressLine3": "address line 3",
          |     "addressLine4": "address line 4",
          |     "postcode": "BB1 1BB",
          |     "countryCode": "GB"
          |  }
          |}
        """.stripMargin).asInstanceOf[JsObject]
      verify(auditService)
        .auditEvent(AgentSubscription, "Agent services subscription", expectedExtraDetail)(hc, fakeRequest)

    }
  }
}
