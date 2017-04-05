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

package uk.gov.hmrc.agentsubscription.audit

import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{verify, when}
import org.scalatest.concurrent.Eventually
import org.scalatest.mock.MockitoSugar
import play.api.libs.json.Json
import play.api.test.FakeRequest
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.audit.model.{AuditEvent, ExtendedDataEvent}
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.{Authorization, RequestId, SessionId}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.{ExecutionContext, Future}

class AuditServiceSpec extends UnitSpec with MockitoSugar with Eventually {
  "auditSubscriptionEvent" should {
    "send an event with the correct fields" in {

      // mock AuditConnector in order to capture calls to it
      val mockConnector = mock[AuditConnector]
      when(mockConnector.sendEvent(any[AuditEvent])(any[HeaderCarrier], any[ExecutionContext])).thenReturn(Future successful AuditResult.Success)

      // this is our AuditService which exposes a bit different interface in comparison to AuditConnector
      val service = new AuditService(mockConnector)

      val hc = HeaderCarrier(
        authorization = Some(Authorization("dummy bearer token")),
        sessionId = Some(SessionId("dummy session id")),
        requestId = Some(RequestId("dummy request id")),
        trueClientIp = Some("1.1.1.1"),
        trueClientPort = Some("12345")
      )

      val detail = Json.obj(
        "agencyName" -> "Test Agency",
        "agencyAddress" -> Json.obj(
          "addressLine1" -> "1 Test Street",
          "addressLine2" -> "Test village"
        )
      )
      service.auditSubscriptionEvent(
        "transaction name",
        detail
      )(
        hc,
        FakeRequest("GET", "/path")
      )

      eventually {
        val captor = ArgumentCaptor.forClass(classOf[AuditEvent])
        verify(mockConnector).sendEvent(captor.capture())(any[HeaderCarrier], any[ExecutionContext])
        captor.getValue shouldBe an[ExtendedDataEvent]
        val sentEvent = captor.getValue.asInstanceOf[ExtendedDataEvent]

        sentEvent.auditSource shouldBe "agent-subscription"
        sentEvent.auditType shouldBe "AgentSubscription"

        (sentEvent.detail \ "Authorization").as[String] shouldBe "dummy bearer token"

        (sentEvent.detail \ "agencyName").as[String] shouldBe "Test Agency"
        (sentEvent.detail \ "agencyAddress" \ "addressLine1").as[String] shouldBe "1 Test Street"
        (sentEvent.detail \ "agencyAddress" \ "addressLine2").as[String] shouldBe "Test village"

        sentEvent.tags("transactionName") shouldBe "transaction name"
        sentEvent.tags("path") shouldBe "/path"
        sentEvent.tags("X-Session-ID") shouldBe "dummy session id"
        sentEvent.tags("X-Request-ID") shouldBe "dummy request id"

        sentEvent.tags("clientIP") shouldBe "1.1.1.1"
        sentEvent.tags("clientPort") shouldBe "12345"
      }
    }

    // According to Graeme Blackwood the deviceID should be in the tags not the detail,
    // and the fact that AuditExtensions puts it into detail instead is a play-auditing bug
    "include the deviceID in the tags not the detail" in {
      pending
      val mockConnector = mock[AuditConnector]
      when(mockConnector.sendEvent(any[AuditEvent])(any[HeaderCarrier], any[ExecutionContext])).thenReturn(Future successful AuditResult.Success)
      val service = new AuditService(mockConnector)

      val hc = HeaderCarrier(
        deviceID = Some("device ID")
      )

      service.auditSubscriptionEvent(
        "transaction name",
        Json.obj()
      )(
        hc,
        FakeRequest("GET", "/path")
      )

      eventually {
        val captor = ArgumentCaptor.forClass(classOf[AuditEvent])
        verify(mockConnector).sendEvent(captor.capture())(any[HeaderCarrier], any[ExecutionContext])
        captor.getValue shouldBe an[ExtendedDataEvent]
        val sentEvent = captor.getValue.asInstanceOf[ExtendedDataEvent]

        sentEvent.tags("deviceID") shouldBe "device ID"
      }
    }
  }

  "auditAgencyStatusEvent" should {
    "send an event with the correct fields" in {

      // mock AuditConnector in order to capture calls to it
      val mockConnector = mock[AuditConnector]
      when(mockConnector.sendEvent(any[AuditEvent])(any[HeaderCarrier], any[ExecutionContext])).thenReturn(Future successful AuditResult.Success)

      // this is our AuditService which exposes a bit different interface in comparison to AuditConnector
      val service = new AuditService(mockConnector)

      val hc = HeaderCarrier(
        authorization = Some(Authorization("dummy bearer token")),
        sessionId = Some(SessionId("dummy session id")),
        requestId = Some(RequestId("dummy request id")),
        trueClientIp = Some("1.1.1.1"),
        trueClientPort = Some("12345")
      )

      val utr = "2000000000"
      val postcode = "AA1 1AA"
      val detail = Json.obj(
        "Authorization" -> "some sort of authorization",
        "utr" -> utr,
        "postcode" -> postcode,
        "knownFactsMatched" -> true,
        "isSubscribedToAgentServices" -> true
      )
      service.auditAgencyStatusEvent(
        "transaction name",
        s"/agent-subscription/registration/utr/${utr}/postcode/${postcode}",
        detail
      )(hc)

      eventually {
        val captor = ArgumentCaptor.forClass(classOf[AuditEvent])
        verify(mockConnector).sendEvent(captor.capture())(any[HeaderCarrier], any[ExecutionContext])
        captor.getValue shouldBe an[ExtendedDataEvent]
        val sentEvent = captor.getValue.asInstanceOf[ExtendedDataEvent]

        sentEvent.auditSource shouldBe "agent-subscription"
        sentEvent.auditType shouldBe "CheckAgencyStatus"

        (sentEvent.detail \ "Authorization").as[String] shouldBe "some sort of authorization"

        (sentEvent.detail \ "utr").as[String] shouldBe "2000000000"
        (sentEvent.detail \ "postcode").as[String] shouldBe "AA1 1AA"
        (sentEvent.detail \ "knownFactsMatched").as[Boolean] shouldBe true
        (sentEvent.detail \ "isSubscribedToAgentServices").as[Boolean] shouldBe true

        sentEvent.tags("transactionName") shouldBe "transaction name"
        sentEvent.tags("path") shouldBe "/agent-subscription/registration/utr/2000000000/postcode/AA1 1AA"
        sentEvent.tags("X-Session-ID") shouldBe "dummy session id"
        sentEvent.tags("X-Request-ID") shouldBe "dummy request id"

        sentEvent.tags("clientIP") shouldBe "1.1.1.1"
        sentEvent.tags("clientPort") shouldBe "12345"
      }
    }
  }

  "toJsObject" should {
    "convert a Map[String, String] into a JsObject with the same entries as the map" in {
      val service = new AuditService(null)

      val js = service.toJsObject(Map(
        "name" -> "value",
        "other name" -> "other value"))

      (js \ "name").as[String] shouldBe "value"
      (js \ "other name").as[String] shouldBe "other value"
    }
  }
}
