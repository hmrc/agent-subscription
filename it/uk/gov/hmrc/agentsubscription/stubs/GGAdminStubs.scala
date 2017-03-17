package uk.gov.hmrc.agentsubscription.stubs

import com.github.tomakehurst.wiremock.client.WireMock._

trait GGAdminStubs {
  val serviceUrl = "/government-gateway-admin/service/HMRC-AS-AGENT/known-facts"

  def createKnownFactsSucceeds(): Unit = {
    stubFor(post(urlEqualTo(serviceUrl)).willReturn(aResponse().withStatus(200)))
  }

  def createKnownFactsFails(): Unit = {
    stubFor(post(urlEqualTo(serviceUrl)).willReturn(aResponse().withStatus(500)))
  }
}
