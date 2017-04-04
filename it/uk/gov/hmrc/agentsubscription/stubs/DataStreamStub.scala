package uk.gov.hmrc.agentsubscription.stubs

object DataStreamStub {
  import com.github.tomakehurst.wiremock.client.WireMock._
  import play.api.libs.json.JsObject

  def writeAuditSucceeds(): Unit = {
    stubFor(post(urlEqualTo(auditUrl))
      .willReturn(aResponse()
        .withStatus(200)
      ))
  }

  def writeAuditMergedSucceeds(): Unit = {
    stubFor(post(urlEqualTo(auditUrl + "/merged"))
      .willReturn(aResponse()
        .withStatus(200)
      ))
  }

  def verifyAuditRequestSent(tags: JsObject, detail: JsObject) = {
    verify(1, postRequestedFor(urlPathEqualTo(auditUrl))
      .withRequestBody(similarToJson(
        s"""{
           |  "auditSource": "agent-subscription",
           |  "auditType": "AgentSubscription",
           |  "tags": ${tags},
           |  "detail": ${detail}
           |}"""
      ))
    )
  }

  private def similarToJson(value: String) = equalToJson(value.stripMargin, ignoreArrayOrder, ignoreExtraElements)

  private def auditUrl = "/write/audit"
  private def ignoreArrayOrder = true
  private def ignoreExtraElements = true //FIXME
}