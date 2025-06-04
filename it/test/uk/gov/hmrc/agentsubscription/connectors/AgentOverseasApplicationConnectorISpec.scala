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
import play.api.test.Helpers._
import uk.gov.hmrc.agentsubscription.config.AppConfig
import uk.gov.hmrc.agentsubscription.model.ApplicationStatus.Accepted
import uk.gov.hmrc.agentsubscription.model.ApplicationStatus.AttemptingRegistration
import uk.gov.hmrc.agentsubscription.model.ApplicationStatus.Registered
import uk.gov.hmrc.agentsubscription.model._
import uk.gov.hmrc.agentsubscription.stubs.AgentOverseasApplicationStubs
import uk.gov.hmrc.agentsubscription.support.BaseISpec
import uk.gov.hmrc.agentsubscription.support.MetricsTestSupport
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import scala.concurrent.ExecutionContext.Implicits.global

class AgentOverseasApplicationConnectorISpec
extends BaseISpec
with AgentOverseasApplicationStubs
with MetricsTestSupport
with MockitoSugar {

  private lazy val http = app.injector.instanceOf[HttpClientV2]
  private val appConfig = app.injector.instanceOf[AppConfig]
  private val metrics = app.injector.instanceOf[Metrics]

  private lazy val connector: AgentOverseasApplicationConnector =
    new AgentOverseasApplicationConnector(
      appConfig,
      http,
      metrics
    )

  private val agencyDetails = OverseasAgencyDetails(
    "Agency name",
    "agencyemail@domain.com",
    OverseasAgencyAddress(
      "Mandatory Address Line 1",
      "Mandatory Address Line 2",
      None,
      None,
      "IE"
    )
  )

  private val businessDetails = TradingDetails(
    "tradingName",
    OverseasBusinessAddress(
      "addressLine1",
      "addressLine2",
      None,
      None,
      "CC"
    )
  )

  private val businessContactDetails = OverseasContactDetails(businessTelephone = "BUSINESS PHONE 123456789", businessEmail = "email@domain.com")

  "updateApplicationStatus" should {
    val targetAppStatus = AttemptingRegistration
    "successful status update" in {
      givenUpdateApplicationStatus(AttemptingRegistration, 204)

      val result = await(connector.updateApplicationStatus(targetAppStatus, "currentUserAuthId"))

      result shouldBe true
    }

    "successful status update with safeId for registered status" in {
      givenUpdateApplicationStatus(
        Registered,
        204,
        s"""{"safeId" : "12345"}"""
      )

      val result = await(connector.updateApplicationStatus(
        Registered,
        "currentUserAuthId",
        Some(SafeId("12345"))
      ))

      result shouldBe true
    }

    "failure, status not changed" when {
      "receives NotFound" in {
        givenUpdateApplicationStatus(AttemptingRegistration, 404)

        an[RuntimeException] shouldBe thrownBy(
          await(connector.updateApplicationStatus(targetAppStatus, "currentUserAuthId"))
        )
      }
      "receives conflict" in {
        givenUpdateApplicationStatus(AttemptingRegistration, 409)

        an[RuntimeException] shouldBe thrownBy(
          await(connector.updateApplicationStatus(targetAppStatus, "currentUserAuthId"))
        )
      }
    }
  }

  "currentApplication" should {
    "return a valid status, safeId and amls details" in {
      givenValidApplication("registered", safeId = Some("XE0001234567890"))

      await(connector.currentApplication) shouldBe CurrentApplication(
        Registered,
        Some(SafeId("XE0001234567890")),
        Some(OverseasAmlsDetails("supervisoryName", Some("supervisoryId"))),
        businessContactDetails,
        businessDetails,
        agencyDetails
      )
    }

    "return no safeId for if application has not yet reached registered state" in {
      givenValidApplication("accepted", safeId = None)

      await(connector.currentApplication) shouldBe CurrentApplication(
        Accepted,
        safeId = None,
        Some(OverseasAmlsDetails("supervisoryName", Some("supervisoryId"))),
        businessContactDetails,
        businessDetails,
        agencyDetails
      )
    }

    "return exception for validation errors" when {
      "API response is completely invalid" in {
        givenInvalidApplication

        an[RuntimeException] shouldBe thrownBy(await(connector.currentApplication))
      }

      "application contains invalid safeID" in {
        givenValidApplication("accepted", safeId = Some("notValid"))

        a[RuntimeException] shouldBe thrownBy(await(connector.currentApplication))
      }

      "application contains invalid status" in {
        givenValidApplication("invalid")

        a[RuntimeException] shouldBe thrownBy(await(connector.currentApplication))
      }

      "application contains invalid business details" in {
        givenValidApplication("accepted", businessTradingName = "~tilde not allowed~")

        a[RuntimeException] shouldBe thrownBy(await(connector.currentApplication))
      }

      "application contains invalid agency details" in {
        givenValidApplication("accepted", agencyName = "~tilde not allowed~")

        a[RuntimeException] shouldBe thrownBy(await(connector.currentApplication))
      }
    }

  }

}
