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

import java.time.LocalDate
import org.scalatestplus.mockito.MockitoSugar
import play.api.test.Helpers._
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscription.config.AppConfig
import uk.gov.hmrc.agentsubscription.model.AmlsDetails
import uk.gov.hmrc.agentsubscription.model.OverseasAmlsDetails
import uk.gov.hmrc.agentsubscription.stubs.AgentAssuranceStub
import uk.gov.hmrc.agentsubscription.support.BaseISpec
import uk.gov.hmrc.agentsubscription.support.MetricsTestSupport
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import scala.concurrent.ExecutionContext.Implicits.global

class AgentAssuranceConnectorISpec
extends BaseISpec
with AgentAssuranceStub
with MetricsTestSupport
with MockitoSugar {

  val utr = Utr("7000000002")
  val arn = Arn("TARN0000001")

  private lazy val metrics = app.injector.instanceOf[Metrics]
  private lazy val http: HttpClientV2 = app.injector.instanceOf[HttpClientV2]
  private lazy val appConfig: AppConfig = app.injector.instanceOf[AppConfig]

  private lazy val connector: AgentAssuranceConnector =
    new AgentAssuranceConnector(
      appConfig,
      http,
      metrics
    )

  val amlsDetails: AmlsDetails = AmlsDetails(
    "supervisory",
    membershipNumber = Some("12345"),
    appliedOn = None,
    membershipExpiresOn = Some(LocalDate.now()),
    amlsSafeId = Some("amlsSafeId"),
    agentBPRSafeId = Some("agentBPRSafeId")
  )

  val overseasAmlsDetails = OverseasAmlsDetails("supervisory", Some("12345"))

  "creating AMLS" should {
    "return a successful response" in {

      createAmlsSucceeds(utr, amlsDetails)

      val result = await(connector.createAmls(utr, amlsDetails))

      result shouldBe true

    }

    "handle failure responses from agent-assurance backend during create amls" in {

      createAmlsFailsWithStatus(403)

      val result = await(connector.createAmls(utr, amlsDetails))

      result shouldBe false
    }
  }

  "updating AMLS" should {

    "return a successful response" in {

      updateAmlsSucceeds(
        utr,
        arn,
        amlsDetails
      )

      val result = await(connector.updateAmls(utr, arn))

      result shouldBe Some(amlsDetails)

    }

    "return a None when agent assurance return 404" in {

      updateAmlsFailsWithStatus(utr, arn, 404)

      val result = await(connector.updateAmls(utr, arn))

      result shouldBe None
    }
  }

  "creating Overseas AMLS" should {
    "return a successful response" in {

      createOverseasAmlsSucceeds(arn, overseasAmlsDetails)

      val result = await(connector.createOverseasAmls(arn, overseasAmlsDetails))

      result shouldBe (())
    }

    "handle conflict responses" in {

      createOverseasAmlsFailsWithStatus(409)

      val result = await(connector.createOverseasAmls(arn, overseasAmlsDetails))

      result shouldBe (())
    }

    "handle failure responses" in {

      createOverseasAmlsFailsWithStatus(500)

      an[Exception] should be thrownBy (await(connector.createOverseasAmls(arn, overseasAmlsDetails)))
    }
  }

}
