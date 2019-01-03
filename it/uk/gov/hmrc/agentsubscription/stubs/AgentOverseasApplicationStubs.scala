package uk.gov.hmrc.agentsubscription.stubs

import com.github.tomakehurst.wiremock.client.WireMock.{ aResponse, put, stubFor, urlEqualTo, equalToJson }
import uk.gov.hmrc.agentsubscription.model.ApplicationStatus

trait AgentOverseasApplicationStubs {

  def givenUpdateApplicationStatus(appStatus: ApplicationStatus, responseStatus: Int, requestBody: String = "{}"): Unit = {
    stubFor(put(urlEqualTo(s"/application/${appStatus.key}"))
      .withRequestBody(equalToJson(requestBody))
      .willReturn(aResponse()
        .withStatus(responseStatus)))
  }
}
