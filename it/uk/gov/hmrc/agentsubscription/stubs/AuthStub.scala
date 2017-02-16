package uk.gov.hmrc.agentsubscription.stubs


import com.github.tomakehurst.wiremock.client.WireMock._

object AuthStub {
  def requestIsNotAuthenticated(): Unit = {
    stubFor(get(urlEqualTo("/auth/authority")).willReturn(aResponse().withStatus(401)))
  }

  def requestIsAuthenticated(): Unit = {
    stubFor(get(urlEqualTo("/auth/authority")).willReturn(aResponse().withStatus(200)))
  }
}
