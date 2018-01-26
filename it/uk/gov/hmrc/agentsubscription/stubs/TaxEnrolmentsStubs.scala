package uk.gov.hmrc.agentsubscription.stubs

import com.github.tomakehurst.wiremock.client.WireMock._

trait TaxEnrolmentsStubs {

  val knownFactsUrl = "/tax-enrolments/enrolments/HMRC-AS-AGENT~AgentReferenceNumber~"
  def enrolmentUrl(groupId: String, arn: String) = s"/tax-enrolments/groups/$groupId/enrolments/HMRC-AS-AGENT~AgentReferenceNumber~$arn"

  def createKnownFactsSucceeds(arn: String): Unit = {
    stubFor(put(urlEqualTo(s"$knownFactsUrl$arn")).willReturn(aResponse().withStatus(200)))
  }

  def createKnownFactsFails(arn: String): Unit = {
    stubFor(put(urlEqualTo(s"$knownFactsUrl$arn")).willReturn(aResponse().withStatus(500)))
  }

  def enrolmentSucceeds(groupId: String, arn: String): Unit = {
    stubFor(post(urlEqualTo(enrolmentUrl(groupId, arn))).willReturn(aResponse().withStatus(200)))
  }

  def enrolmentFails(groupId: String, arn: String): Unit = {
    stubFor(post(urlEqualTo(enrolmentUrl(groupId, arn))).willReturn(aResponse().withStatus(500)))
  }

}
