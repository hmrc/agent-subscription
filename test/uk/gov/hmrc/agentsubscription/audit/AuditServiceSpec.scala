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

package uk.gov.hmrc.agentsubscription.audit

import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.verify
import org.mockito.Mockito.when
import org.scalatest.concurrent.Eventually
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.Json
import play.api.test.FakeRequest
import uk.gov.hmrc.agentsubscription.support.UnitSpec
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.http.connector.AuditResult
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class AuditServiceSpec
extends UnitSpec
with MockitoSugar
with Eventually {

  "auditEvent" should {
    "send an event with the correct fields" in {
      val mockConnector = mock[AuditConnector]
      when(mockConnector.sendExtendedEvent(any[ExtendedDataEvent])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future successful AuditResult.Success)
      val service = new AuditService(mockConnector)

      val detail = Json.obj(
        "agencyName" -> "Test Agency",
        "agencyAddress" -> Json.obj("addressLine1" -> "1 Test Street", "addressLine2" -> "Test village")
      )
      service.auditEvent(
        AgentSubscription,
        "transaction name",
        detail
      )(
        FakeRequest("GET", "/path")
          .withHeaders(
            "Authorisation" -> "dummy bearer token",
            "X-Session-ID" -> "dummy session id",
            "X-Request-ID" -> "dummy request id",
            "True-Client-Port" -> "12345"
          )
      )

      eventually {
        val captor = ArgumentCaptor.forClass(classOf[ExtendedDataEvent])
        verify(mockConnector).sendExtendedEvent(captor.capture())(any[HeaderCarrier], any[ExecutionContext])

        val sentEvent = captor.getValue.asInstanceOf[ExtendedDataEvent]

        sentEvent.auditSource shouldBe "agent-subscription"
        sentEvent.auditType shouldBe "AgentSubscription"

        (sentEvent.detail \ "agencyName").as[String] shouldBe "Test Agency"
        (sentEvent.detail \ "agencyAddress" \ "addressLine1").as[String] shouldBe "1 Test Street"
        (sentEvent.detail \ "agencyAddress" \ "addressLine2").as[String] shouldBe "Test village"

        sentEvent.tags("transactionName") shouldBe "transaction name"
        sentEvent.tags("path") shouldBe "/path"
        sentEvent.tags("X-Session-ID") shouldBe "dummy session id"
        sentEvent.tags("X-Request-ID") shouldBe "dummy request id"

        sentEvent.tags("clientPort") shouldBe "12345"
      }
    }

    "include the deviceID in the tags not the detail" in {
      pending
      val mockConnector = mock[AuditConnector]
      when(mockConnector.sendEvent(any[DataEvent])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future successful AuditResult.Success)
      val service = new AuditService(mockConnector)

//      val hc = HeaderCarrier(deviceID = Some("device ID"))

      service.auditEvent(
        AgentSubscription,
        "transaction name",
        Json.obj()
      )(FakeRequest("GET", "/path"))

      eventually {
        val captor = ArgumentCaptor.forClass(classOf[DataEvent])
        verify(mockConnector).sendEvent(captor.capture())(any[HeaderCarrier], any[ExecutionContext])

        val sentEvent = captor.getValue.asInstanceOf[ExtendedDataEvent]

        sentEvent.tags("deviceID") shouldBe "device ID"
      }
    }
  }

  "toJsObject" should {
    "convert a Map[String, String] into a JsObject with the same entries as the map" in {
      val service = new AuditService(null)

      val js = service.toJsObject(Map("name" -> "value", "other name" -> "other value"))

      (js \ "name").as[String] shouldBe "value"
      (js \ "other name").as[String] shouldBe "other value"
    }
  }

}
