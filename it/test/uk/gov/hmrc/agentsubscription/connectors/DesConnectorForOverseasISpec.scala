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

import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.Json
import play.api.test.Helpers._
import uk.gov.hmrc.agentsubscription.config.AppConfig
import uk.gov.hmrc.agentsubscription.model._
import uk.gov.hmrc.agentsubscription.stubs.OverseasDesStubs
import uk.gov.hmrc.agentsubscription.support.BaseISpec
import uk.gov.hmrc.agentsubscription.support.MetricsTestSupport
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global

class DesConnectorForOverseasISpec
extends BaseISpec
with OverseasDesStubs
with MetricsTestSupport
with MockitoSugar {

  private val bearerToken = "secret"
  private val environment = "test"

  override protected def expectedBearerToken = Some(bearerToken)

  override protected def expectedEnvironment = Some(environment)

  private lazy val metrics = app.injector.instanceOf[Metrics]
  private lazy val http: HttpClientV2 = app.injector.instanceOf[HttpClientV2]
  private lazy val appConfig = app.injector.instanceOf[AppConfig]

  private lazy val connector: DesConnector =
    new DesConnector(
      appConfig,
      http,
      metrics
    )

  private val overseasRegistrationRequest = OverseasRegistrationRequest(
    "AGSV",
    UUID.randomUUID.toString.replaceAll("-", ""),
    isAnAgent = false,
    isAGroup = false,
    Organisation("Test Organisation Name"),
    OverseasBusinessAddress(
      "Mandatory Address Line 1",
      "Mandatory Address Line 2",
      Some("Optional Address Line 3"),
      Some("Optional Address Line 4"),
      "IE"
    ),
    ContactDetails("00491234567890", "test@test.example")
  )

  "createOverseasBusinessPartnerRecord" should {
    "return safeId for when an overseas BPR is created" in {
      organisationRegistrationSucceeds(Json.toJson(overseasRegistrationRequest).toString())

      val response = await(connector.createOverseasBusinessPartnerRecord(overseasRegistrationRequest))

      response.value shouldBe "XE0001234567890"
    }

    "return exception for when an overseas BPR creation fails with NOT_FOUND error" in {
      organisationRegistrationFailsWithNotFound()

      an[RuntimeException] should be thrownBy await(
        connector.createOverseasBusinessPartnerRecord(overseasRegistrationRequest)
      )
    }

    "return exception for when an overseas BPR creation fails for an invalid payload" in {
      organisationRegistrationFailsWithInvalidPayload()

      an[RuntimeException] should be thrownBy await(
        connector.createOverseasBusinessPartnerRecord(overseasRegistrationRequest.copy(regime = ""))
      )
    }
  }

}
