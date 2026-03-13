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
import play.api.libs.json.Json

trait HipStubs {

  def hipSubscriptionSucceeds(
    safeId: String,
    requestJson: String
  ): Unit = stubFor(
    subscriptionRequest(safeId, requestJson)
      .willReturn(
        aResponse()
          .withStatus(201)
          .withBody(Json.obj(
            "success" -> Json.obj(
              "processingDate" -> "2026-01-01T12:00:00Z",
              "arn" -> "TARN0000001"
            )
          ).toString())
      )
  )

  def hipSubscriptionFails(
    safeId: String,
    requestJson: String,
    status: Int
  ): Unit = stubFor(
    subscriptionRequest(safeId, requestJson)
      .willReturn(
        aResponse()
          .withStatus(status)
      )
  )

  private def subscriptionRequest(
    safeId: String,
    json: String
  ) = post(urlEqualTo(s"/etmp/RESTAdapter/generic/agent/subscription/$safeId"))
    .withRequestBody(equalToJson(json))

}
