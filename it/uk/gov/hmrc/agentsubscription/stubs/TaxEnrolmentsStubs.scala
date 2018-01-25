package uk.gov.hmrc.agentsubscription.stubs

import com.github.tomakehurst.wiremock.client.WireMock._

trait TaxEnrolmentsStubs {

  val serviceUrl = "/enrolment-store/enrolments/HMRC-AS-AGENT"

  def createKnownFactsSucceeds(): Unit = {
    stubFor(put(urlEqualTo(serviceUrl)).willReturn(aResponse().withStatus(200)))
  }

  def createKnownFactsFails(): Unit = {
    stubFor(put(urlEqualTo(serviceUrl)).willReturn(aResponse().withStatus(500)))
  }

  def enrolmentSucceeds(): Unit = {
    stubFor(post(urlEqualTo("/enrol")).willReturn(aResponse().withStatus(200)))
  }

  def enrolmentFails(): Unit = {
    stubFor(post(urlEqualTo("/enrol")).willReturn(aResponse().withStatus(500)))
  }

}
