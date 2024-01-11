/*
 * Copyright 2023 HM Revenue & Customs
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

import com.kenshoo.play.metrics.Metrics
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.agentsubscription.config.AppConfig
import uk.gov.hmrc.agentsubscription.support.UnitSpec
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, RequestId, SessionId}

class DesConnectorSpec extends UnitSpec with MockitoSugar {

  val appConfig: AppConfig = mock[AppConfig]
  val hc: HeaderCarrier = mock[HeaderCarrier]
  val httpClient: HttpClient = mock[HttpClient]
  val metrics: Metrics = mock[Metrics]

  when(appConfig.desAuthToken).thenReturn("testAuthToken")
  when(appConfig.desEnvironment).thenReturn("testEnv")

  val underTest: DesConnector = new DesConnector(appConfig, httpClient, metrics)

  "desHeaders" should {
    "contain correct headers" when {
      "sessionId and requestId found" in {
        when(hc.sessionId).thenReturn(Option(SessionId("testSession")))
        when(hc.requestId).thenReturn(Option(RequestId("testRequestId")))

        val headersMap = underTest.desHeaders()(hc).toMap

        headersMap should contain("Authorization" -> "Bearer testAuthToken")
        headersMap should contain("Environment" -> "testEnv")
        headersMap should contain("x-session-id" -> "testSession")
        headersMap should contain("x-request-id" -> "testRequestId")
      }

      "sessionId and requestId not found" in {
        when(hc.sessionId).thenReturn(None)
        when(hc.requestId).thenReturn(None)

        val headersMap = underTest.desHeaders()(hc).toMap

        headersMap should contain("Authorization" -> "Bearer testAuthToken")
        headersMap should contain("Environment" -> "testEnv")
        headersMap.contains("x-session-id") shouldBe false
        headersMap.contains("x-request-id") shouldBe true
      }
    }
  }
}
