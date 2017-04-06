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
import uk.gov.hmrc.agentsubscription.audit.AgentSubscriptionEvent.CheckAgencyStatus
import uk.gov.hmrc.agentsubscription.audit.AuditService
import uk.gov.hmrc.agentsubscription.connectors.{DesConnector, DesIndividual, DesRegistrationResponse}
import uk.gov.hmrc.agentsubscription.support.ResettingMockitoSugar
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.{ExecutionContext, Future}

class RegistrationServiceSpec extends UnitSpec with ResettingMockitoSugar with Eventually {

  private val desConnector = resettingMock[DesConnector]
  private val auditService = resettingMock[AuditService]

  private val service = new RegistrationService(desConnector, auditService)

  private implicit val hc = HeaderCarrier()
  private implicit val fakeRequest = FakeRequest()

  "getRegistration" should {
    "audit appropriate values when a matching organisation registration is found" in {
      val utr = "4000000009"
      val postcode = "AA1 1AA"

      when(desConnector.getRegistration(anyString)(eqs(hc), any[ExecutionContext]))
        .thenReturn(Future successful Some(DesRegistrationResponse(
          Some(postcode),
          isAnASAgent = false,
          Some("Organisation name"),
          None)))

      await(service.getRegistration(utr, postcode))

      val expectedExtraDetail = Json.parse(
        s"""
          |{
          |  "utr": "$utr",
          |  "postcode": "$postcode",
          |  "knownFactsMatched": true,
          |  "isSubscribedToAgentServices": false
          |}
          |""".stripMargin).asInstanceOf[JsObject]
      eventually {
        verify(auditService)
          .auditEvent(CheckAgencyStatus, "Check agency status", expectedExtraDetail)(hc, fakeRequest)
      }
    }

    "audit appropriate values when a matching individual registration is found" in {
      val utr = "4000000009"
      val postcode = "AA1 1AA"

      when(desConnector.getRegistration(anyString)(eqs(hc), any[ExecutionContext]))
        .thenReturn(Future successful Some(DesRegistrationResponse(
          Some(postcode),
          isAnASAgent = false,
          None,
          Some(DesIndividual("First", "Last")))))

      await(service.getRegistration(utr, postcode))

      val expectedExtraDetail = Json.parse(
        s"""
          |{
          |  "utr": "$utr",
          |  "postcode": "$postcode",
          |  "knownFactsMatched": true,
          |  "isSubscribedToAgentServices": false
          |}
          |""".stripMargin).asInstanceOf[JsObject]
      eventually {
        verify(auditService)
          .auditEvent(CheckAgencyStatus, "Check agency status", expectedExtraDetail)(hc, fakeRequest)
      }
    }

    "audit appropriate values when no matching registration is found" in {
      val utr = "4000000009"
      val postcode = "AA1 1AA"
      val nonMatchingPostcode = "BB2 2BB"

      when(desConnector.getRegistration(anyString)(eqs(hc), any[ExecutionContext]))
        .thenReturn(Future successful Some(DesRegistrationResponse(
          Some(nonMatchingPostcode),
          isAnASAgent = false,
          None,
          Some(DesIndividual("First", "Last")))))

      await(service.getRegistration(utr, postcode))

      val expectedExtraDetail = Json.parse(
        s"""
          |{
          |  "utr": "$utr",
          |  "postcode": "$postcode",
          |  "knownFactsMatched": false
          |}
          |""".stripMargin).asInstanceOf[JsObject]
      eventually {
        verify(auditService)
          .auditEvent(CheckAgencyStatus, "Check agency status", expectedExtraDetail)(hc, fakeRequest)
      }
    }
  }

}
