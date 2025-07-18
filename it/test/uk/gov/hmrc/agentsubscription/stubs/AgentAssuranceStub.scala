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

package uk.gov.hmrc.agentsubscription.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import play.api.libs.json.Json
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscription.connectors.AgentAssuranceConnector.CreateAmlsRequest
import uk.gov.hmrc.agentsubscription.connectors.AgentAssuranceConnector.CreateOverseasAmlsRequest
import uk.gov.hmrc.agentsubscription.model.AmlsDetails
import uk.gov.hmrc.agentsubscription.model.OverseasAmlsDetails

trait AgentAssuranceStub {

  private val createUrl: String = s"/agent-assurance/amls"

  private val createOverseasAmlsUrl: String = s"/agent-assurance/overseas-agents/amls"

  private def updateUrl(utr: Utr): String = s"/agent-assurance/amls/utr/${utr.value}"

  def createAmlsSucceeds(
    utr: Utr,
    amlsDetails: AmlsDetails
  ): StubMapping = stubFor(
    post(urlEqualTo(createUrl))
      .withRequestBody(equalToJson(Json.toJson(CreateAmlsRequest(utr, amlsDetails)).toString()))
      .willReturn(
        aResponse()
          .withStatus(201)
      )
  )

  def createAmlsFailsWithStatus(status: Int): StubMapping = stubFor(
    post(urlEqualTo(createUrl))
      .willReturn(aResponse().withStatus(status))
  )

  def updateAmlsSucceeds(
    utr: Utr,
    arn: Arn,
    amlsDetails: AmlsDetails
  ): StubMapping = stubFor(
    put(urlEqualTo(updateUrl(utr)))
      .withRequestBody(equalToJson(s"""{"value": "${arn.value}"}"""))
      .willReturn(
        aResponse()
          .withBody(Json.toJson(amlsDetails).toString())
          .withStatus(200)
      )
  )

  def updateAmlsFailsWithStatus(
    utr: Utr,
    arn: Arn,
    status: Int
  ): StubMapping = stubFor(
    put(urlEqualTo(updateUrl(utr)))
      .withRequestBody(equalToJson(s"""{"value": "${arn.value}"}"""))
      .willReturn(aResponse().withStatus(status))
  )

  def createOverseasAmlsSucceeds(
    arn: Arn,
    amlsDetails: OverseasAmlsDetails
  ): StubMapping = stubFor(
    post(urlEqualTo(createOverseasAmlsUrl))
      .withRequestBody(equalToJson(Json.toJson(CreateOverseasAmlsRequest(arn, amlsDetails)).toString()))
      .willReturn(
        aResponse()
          .withStatus(201)
      )
  )

  def createOverseasAmlsFailsWithStatus(status: Int): StubMapping = stubFor(
    post(urlEqualTo(createOverseasAmlsUrl))
      .willReturn(aResponse().withStatus(status))
  )

  def verifyCreateOverseasAmlsCall(times: Int = 0) = verify(times, postRequestedFor(urlEqualTo(createOverseasAmlsUrl)))

}
