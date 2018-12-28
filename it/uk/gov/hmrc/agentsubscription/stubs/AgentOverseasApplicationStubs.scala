package uk.gov.hmrc.agentsubscription.stubs

import com.github.tomakehurst.wiremock.client.WireMock.{ aResponse, get, stubFor, urlEqualTo }
import uk.gov.hmrc.agentsubscription.model.ApplicationStatus

trait AgentOverseasApplicationStubs {

  def givenGetUpdateApplicationStatus(appStatus: ApplicationStatus, responseStatus: Int): Unit = {
    stubFor(get(urlEqualTo(s"/application/${appStatus.key}"))
      .willReturn(aResponse()
        .withStatus(responseStatus)))
  }
}
