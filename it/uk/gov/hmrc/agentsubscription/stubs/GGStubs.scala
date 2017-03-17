package uk.gov.hmrc.agentsubscription.stubs

import com.github.tomakehurst.wiremock.client.WireMock._

trait GGStubs {
  def enrolmentSucceeds(): Unit = {
    stubFor(post(urlEqualTo("/enrol")).willReturn(aResponse().withStatus(200)))
  }

  def enrolmentFails(): Unit = {
    stubFor(post(urlEqualTo("/enrol")).willReturn(aResponse().withStatus(500)))
  }
}
