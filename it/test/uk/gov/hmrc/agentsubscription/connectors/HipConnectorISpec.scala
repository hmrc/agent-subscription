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

import play.api.libs.json.Json
import play.api.test.Helpers._
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentsubscription.model.OverseasAgencyAddress
import uk.gov.hmrc.agentsubscription.model.OverseasAgencyDetails
import uk.gov.hmrc.agentsubscription.model.OverseasAmlsDetails
import uk.gov.hmrc.agentsubscription.model.SafeId
import uk.gov.hmrc.agentsubscription.stubs.HipStubs
import uk.gov.hmrc.agentsubscription.support.BaseISpec
import uk.gov.hmrc.agentsubscription.support.MetricsTestSupport
import uk.gov.hmrc.http._

class HipConnectorISpec
extends BaseISpec
with HipStubs
with MetricsTestSupport {

  private lazy val connector: HipConnector = app.injector.instanceOf[HipConnector]

  private val safeId = SafeId("XE0001234567890")
  private val overseasAmlsDetails = OverseasAmlsDetails("supervisoryName", Some("supervisoryId"))
  private val overseasAgencyDetails: OverseasAgencyDetails = OverseasAgencyDetails(
    agencyName = "Agency name",
    agencyEmail = "agencyemail@domain.com",
    agencyAddress = OverseasAgencyAddress(
      "Mandatory Address Line 1",
      "Mandatory Address Line 2",
      None,
      None,
      "IE"
    )
  )
  private val agencyDetailsJson =
    Json.obj(
      "name" -> "Agency name",
      "addr1" -> "Mandatory Address Line 1",
      "addr2" -> "Mandatory Address Line 2",
      "country" -> "IE",
      "email" -> "agencyemail@domain.com",
      "supervisoryBody" -> "supervisoryName",
      "membershipNumber" -> "supervisoryId",
      "updateDetailsStatus" -> "REQUIRED",
      "amlSupervisionUpdateStatus" -> "REQUIRED",
      "directorPartnerUpdateStatus" -> "REQUIRED",
      "acceptNewTermsStatus" -> "REQUIRED",
      "reriskStatus" -> "REQUIRED"
    ).toString

  private val agencyDetailsJsonWithoutAmls =
    Json.obj(
      "name" -> "Agency name",
      "addr1" -> "Mandatory Address Line 1",
      "addr2" -> "Mandatory Address Line 2",
      "country" -> "IE",
      "email" -> "agencyemail@domain.com",
      "updateDetailsStatus" -> "REQUIRED",
      "amlSupervisionUpdateStatus" -> "REQUIRED",
      "directorPartnerUpdateStatus" -> "REQUIRED",
      "acceptNewTermsStatus" -> "REQUIRED",
      "reriskStatus" -> "REQUIRED"
    ).toString

  "subscribeToAgentServices" should {
    "return an ARN when subscription is successful with amls details" in {
      hipSubscriptionSucceeds(safeId.value, agencyDetailsJson)

      val result = await(connector.subscribeToAgentServicesOverseas(
        safeId,
        overseasAgencyDetails,
        Some(overseasAmlsDetails)
      ))

      result shouldBe Arn("TARN0000001")
    }

    "return an ARN when subscription is successful without amls details" in {
      hipSubscriptionSucceeds(safeId.value, agencyDetailsJsonWithoutAmls)

      val result = await(connector.subscribeToAgentServicesOverseas(
        safeId,
        overseasAgencyDetails,
        None
      ))

      result shouldBe Arn("TARN0000001")
    }

    "propagate an exception containing the safeId if there is an error" in {
      hipSubscriptionFails(
        safeId.value,
        agencyDetailsJson,
        409
      )

      val exception = intercept[UpstreamErrorResponse] {
        await(connector.subscribeToAgentServicesOverseas(
          safeId,
          overseasAgencyDetails,
          Some(overseasAmlsDetails)
        ))
      }

      exception.getMessage.contains(safeId.value) shouldBe true
      exception.statusCode shouldBe 409
    }
  }

}
