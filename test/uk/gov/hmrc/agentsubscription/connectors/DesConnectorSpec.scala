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

import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.Json
import play.api.mvc.RequestHeader
import play.api.test.FakeRequest
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscription.config.AppConfig
import uk.gov.hmrc.agentsubscription.support.UnitSpec
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.Authorization
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HeaderNames
import uk.gov.hmrc.http.RequestId
import uk.gov.hmrc.http.SessionId
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import java.util.UUID
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class DesConnectorSpec
extends UnitSpec
with MockitoSugar {

  val appConfig: AppConfig = mock[AppConfig]
  val hc: HeaderCarrier = mock[HeaderCarrier]
  val httpClient: HttpClientV2 = mock[HttpClientV2]
  val hipConnector: HipConnector = mock[HipConnector]
  val metrics: Metrics = mock[Metrics]

  when(appConfig.desAuthToken).thenReturn("testAuthToken")
  when(appConfig.desEnvironment).thenReturn("testEnv")
  when(appConfig.internalHostPatterns).thenReturn(
    Seq(
      "^.*\\.service$",
      "^.*\\.mdtp$",
      "^localhost$"
    ).map(_.r)
  )

  val underTest: DesConnector =
    new DesConnector(
      appConfig,
      httpClient,
      hipConnector,
      metrics
    )

  "desHeaders" should {
    "contain correct headers" when {
      "service is internal (localhost)" in {

        val url = "http://localhost:9009/registration/agents/utr/01234567890"

        val hc = HeaderCarrier(
          authorization = Some(Authorization("Bearer sessionToken")),
          sessionId = Some(SessionId("session-xyz")),
          requestId = Some(RequestId("requestId"))
        )

        val headersConfig = underTest.makeHeadersConfig(url)(hc)
        val headersMap = headersConfig.explicitHeaders.toMap
        val headerCarrier = headersConfig.hc

        headersMap should contain("Environment" -> "testEnv")
        headersMap should contain key "CorrelationId"
        UUID.fromString(headersMap("CorrelationId")) should not be null
        headersMap should not contain key(HeaderNames.authorisation)
        headersMap should not contain key(HeaderNames.xSessionId)
        headersMap should not contain key(HeaderNames.xRequestId)

        headerCarrier.authorization.get should be(Authorization("Bearer testAuthToken"))
        headerCarrier.sessionId.get should be(SessionId("session-xyz"))
        headerCarrier.requestId.get should be(RequestId("requestId"))
      }

      "service is internal (mdtp)" in {

        val url = "https://agents-external-stubs.protected.mdtp/registration/agents/utr/01234567890"

        val hc = HeaderCarrier(
          authorization = Some(Authorization("Bearer sessionToken")),
          sessionId = Some(SessionId("session-xyz")),
          requestId = Some(RequestId("requestId"))
        )

        val headersConfig = underTest.makeHeadersConfig(url)(hc)
        val headersMap = headersConfig.explicitHeaders.toMap
        val headerCarrier = headersConfig.hc

        headersMap should contain("Environment" -> "testEnv")
        headersMap should contain key "CorrelationId"
        UUID.fromString(headersMap("CorrelationId")) should not be null
        headersMap should not contain key(HeaderNames.authorisation)
        headersMap should not contain key(HeaderNames.xSessionId)
        headersMap should not contain key(HeaderNames.xRequestId)

        headerCarrier.authorization.get should be(Authorization("Bearer testAuthToken"))
        headerCarrier.sessionId.get should be(SessionId("session-xyz"))
        headerCarrier.requestId.get should be(RequestId("requestId"))
      }

      "service is external (DES)" in {

        val url = "https://des.ws.ibt.hmrc.gov.uk/registration/agents/utr/01234567890"

        val hc = HeaderCarrier(
          authorization = Some(Authorization("Bearer sessionToken")),
          sessionId = Some(SessionId("session-xyz")),
          requestId = Some(RequestId("requestId"))
        )

        val headersConfig = underTest.makeHeadersConfig(url)(hc)
        val headersMap = headersConfig.explicitHeaders.toMap
        val headerCarrier = headersConfig.hc

        headersMap should contain("Environment" -> "testEnv")
        headersMap should contain key "CorrelationId"
        UUID.fromString(headersMap("CorrelationId")) should not be null

        headersMap should contain key (HeaderNames.authorisation)
        headersMap should contain(HeaderNames.xSessionId -> "session-xyz")
        headersMap should contain(HeaderNames.xRequestId -> "requestId")
        headerCarrier == hc should be(true)
      }
    }
  }

  "getRegistration" should {
    "use HIP when the HIP registration feature switch is enabled" in {
      val utr = Utr("1234567890")
      implicit val requestHeader: RequestHeader = FakeRequest()
      val registrationJson = Json.obj(
        "isAnASAgent" -> false,
        "address" -> Json.obj(
          "addressLine1" -> "Address line 1",
          "countryCode" -> "GB",
          "postalCode" -> "AA1 1AA"
        )
      )
      when(appConfig.hipRegistrationEnabled).thenReturn(true)
      when(hipConnector.getRegistration(utr)(requestHeader))
        .thenReturn(Future.successful(Some(registrationJson)))

      underTest.getRegistration(utr).futureValue shouldBe Some(
        DesRegistrationResponse(
          isAnASAgent = false,
          organisationName = None,
          individual = None,
          agentReferenceNumber = None,
          address = DesBusinessAddress(
            "Address line 1",
            None,
            None,
            None,
            Some("AA1 1AA"),
            "GB"
          ),
          emailAddress = None,
          primaryPhoneNumber = None,
          safeId = None
        )
      )
    }
  }

}
