package uk.gov.hmrc.agentsubscription.stubs

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, put, stubFor, urlEqualTo}

trait EnrolmentStoreStubs {

  val serviceUrl = "/enrolment-store/enrolments/HMRC-AS-AGENT"

  def createKnownFactsSucceeds(): Unit = {
    stubFor(put(urlEqualTo(serviceUrl)).willReturn(aResponse().withStatus(200)))
  }

  def createKnownFactsFails(): Unit = {
    stubFor(put(urlEqualTo(serviceUrl)).willReturn(aResponse().withStatus(500)))
  }

}
