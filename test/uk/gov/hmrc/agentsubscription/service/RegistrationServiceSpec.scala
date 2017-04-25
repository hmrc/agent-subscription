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
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr}
import uk.gov.hmrc.agentsubscription.audit.AgentSubscriptionEvent.CheckAgencyStatus
import uk.gov.hmrc.agentsubscription.audit.AuditService
import uk.gov.hmrc.agentsubscription.auth.{Authority, RequestWithAuthority}
import uk.gov.hmrc.agentsubscription.connectors.{DesConnector, DesIndividual, DesRegistrationResponse}
import uk.gov.hmrc.agentsubscription.support.ResettingMockitoSugar
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.{ExecutionContext, Future}

class RegistrationServiceSpec extends UnitSpec with ResettingMockitoSugar with Eventually {

  private val desConnector = resettingMock[DesConnector]
  private val auditService = resettingMock[AuditService]

  private val service = new RegistrationService(desConnector, auditService)

  private val hc = HeaderCarrier()
  private val request = RequestWithAuthority(Authority(authProviderId = Some("54321-credId"), authProviderType = Some("GovernmentGateway"), "", ""), FakeRequest())
  private val requestWithoutAuthProvider = RequestWithAuthority(Authority(authProviderId = None, authProviderType = None, "", ""), FakeRequest())

  "getRegistration" should {
    "audit appropriate values when a matching organisation registration is found" in {
      val utr = Utr("4000000009")
      val postcode = "AA1 1AA"

      when(desConnector.getRegistration(any[Utr])(eqs(hc), any[ExecutionContext]))
        .thenReturn(Future successful Some(DesRegistrationResponse(
          Some(postcode),
          isAnASAgent = true,
          Some("Organisation name"),
          None,
          Some(Arn("TARN0000001")))))

      await(service.getRegistration(utr, postcode)(hc, request))

      val expectedExtraDetail = Json.parse(
        s"""
          |{
          |  "authProviderId": "54321-credId",
          |  "authProviderType": "GovernmentGateway",
          |  "utr": "${utr.value}",
          |  "postcode": "$postcode",
          |  "knownFactsMatched": true,
          |  "isSubscribedToAgentServices": true,
          |  "agentReferenceNumber": "TARN0000001"
          |}
          |""".stripMargin).asInstanceOf[JsObject]
      eventually {
        verify(auditService)
          .auditEvent(CheckAgencyStatus, "Check agency status", expectedExtraDetail)(hc, request)
      }
    }

    "audit appropriate values when a matching individual registration is found" in {
      val utr = Utr("4000000009")
      val postcode = "AA1 1AA"

      when(desConnector.getRegistration(any[Utr])(eqs(hc), any[ExecutionContext]))
        .thenReturn(Future successful Some(DesRegistrationResponse(
          Some(postcode),
          isAnASAgent = true,
          None,
          Some(DesIndividual("First", "Last")),
          Some(Arn("AARN0000002")))))

      await(service.getRegistration(utr, postcode)(hc, request))

      val expectedExtraDetail = Json.parse(
        s"""
          |{
          |  "authProviderId": "54321-credId",
          |  "authProviderType": "GovernmentGateway",
          |  "utr": "${utr.value}",
          |  "postcode": "$postcode",
          |  "knownFactsMatched": true,
          |  "isSubscribedToAgentServices": true,
          |  "agentReferenceNumber": "AARN0000002"
          |}
          |""".stripMargin).asInstanceOf[JsObject]
      eventually {
        verify(auditService)
          .auditEvent(CheckAgencyStatus, "Check agency status", expectedExtraDetail)(hc, request)
      }
    }

    "tolerate optional fields being absent (agentReferenceNumber, authProviderId, authProviderType)" in {
      val utr = Utr("4000000009")
      val postcode = "AA1 1AA"

      when(desConnector.getRegistration(any[Utr])(eqs(hc), any[ExecutionContext]))
        .thenReturn(Future successful Some(DesRegistrationResponse(
          Some(postcode),
          isAnASAgent = false,
          None,
          Some(DesIndividual("First", "Last")),
          None)))

      await(service.getRegistration(utr, postcode)(hc, requestWithoutAuthProvider))

      val expectedExtraDetail = Json.parse(
        s"""
          |{
          |  "utr": "${utr.value}",
          |  "postcode": "$postcode",
          |  "knownFactsMatched": true,
          |  "isSubscribedToAgentServices": false
          |}
          |""".stripMargin).asInstanceOf[JsObject]
      eventually {
        verify(auditService)
          .auditEvent(CheckAgencyStatus, "Check agency status", expectedExtraDetail)(hc, requestWithoutAuthProvider)
      }
    }

    "audit appropriate values when no matching registration is found" in {
      val utr = Utr("4000000009")
      val postcode = "AA1 1AA"
      val nonMatchingPostcode = "BB2 2BB"

      when(desConnector.getRegistration(any[Utr])(eqs(hc), any[ExecutionContext]))
        .thenReturn(Future successful Some(DesRegistrationResponse(
          Some(nonMatchingPostcode),
          isAnASAgent = false,
          None,
          Some(DesIndividual("First", "Last")),
          None)))

      await(service.getRegistration(utr, postcode)(hc, request))

      val expectedExtraDetail = Json.parse(
        s"""
          |{
          |  "authProviderId": "54321-credId",
          |  "authProviderType": "GovernmentGateway",
          |  "utr": "${utr.value}",
          |  "postcode": "$postcode",
          |  "knownFactsMatched": false
          |}
          |""".stripMargin).asInstanceOf[JsObject]
      eventually {
        verify(auditService)
          .auditEvent(CheckAgencyStatus, "Check agency status", expectedExtraDetail)(hc, request)
      }
    }
  }

}
