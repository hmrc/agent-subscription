package uk.gov.hmrc.agentsubscription.stubs

import uk.gov.hmrc.agentsubscription.audit.AgentSubscriptionEvent

object DataStreamStub {
  import com.github.tomakehurst.wiremock.client.WireMock._
  import play.api.libs.json.JsObject

  def writeAuditSucceeds(): Unit = {
    stubFor(post(urlEqualTo(auditUrl))
      .willReturn(aResponse()
        .withStatus(204)))
    ()
  }

  def writeAuditMergedSucceeds(): Unit = {
    stubFor(post(urlEqualTo(auditUrl + "/merged"))
      .willReturn(aResponse()
        .withStatus(204)))
    ()
  }

  def verifyAuditRequestSent(event: AgentSubscriptionEvent, tags: JsObject, detail: JsObject) = {
    verify(1, postRequestedFor(urlPathEqualTo(auditUrl))
      .withRequestBody(similarToJson(
        s"""{
           |  "auditSource": "agent-subscription",
           |  "auditType": "$event",
           |  "tags": $tags,
           |  "detail": $detail
           |}""")))
  }

  private def similarToJson(value: String) = equalToJson(value.stripMargin, true, true)

  private def auditUrl = "/write/audit"
}
