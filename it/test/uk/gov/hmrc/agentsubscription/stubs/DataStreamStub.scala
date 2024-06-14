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

import uk.gov.hmrc.agentsubscription.audit.AgentSubscriptionEvent

object DataStreamStub {
  import com.github.tomakehurst.wiremock.client.WireMock._
  import play.api.libs.json.JsObject

  def writeAuditSucceeds(): Unit = {
    stubFor(
      post(urlEqualTo(auditUrl))
        .willReturn(
          aResponse()
            .withStatus(204)
        )
    )
    ()
  }

  def writeAuditMergedSucceeds(): Unit = {
    stubFor(
      post(urlEqualTo(auditUrl + "/merged"))
        .willReturn(
          aResponse()
            .withStatus(204)
        )
    )
    ()
  }

  def verifyAuditRequestSent(event: AgentSubscriptionEvent, tags: JsObject, detail: JsObject) =
    verify(
      1,
      postRequestedFor(urlPathEqualTo(auditUrl))
        .withRequestBody(similarToJson(s"""{
           |  "auditSource": "agent-subscription",
           |  "auditType": "$event",
           |  "tags": $tags,
           |  "detail": $detail
           |}"""))
    )

  private def similarToJson(value: String) = equalToJson(value.stripMargin, true, true)

  private def auditUrl = "/write/audit"
}
