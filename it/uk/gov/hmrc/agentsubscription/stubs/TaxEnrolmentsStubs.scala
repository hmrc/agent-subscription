package uk.gov.hmrc.agentsubscription.stubs

import com.github.tomakehurst.wiremock.client.WireMock._

trait TaxEnrolmentsStubs {

  val createKnownFactsUrl = "/tax-enrolments/enrolments/HMRC-AS-AGENT~AgentReferenceNumber~"
  val deleteKnownFactsUrl = "/enrolment-store-proxy/enrolment-store/enrolments/HMRC-AS-AGENT~AgentReferenceNumber~"
  def enrolmentUrl(groupId: String, arn: String) = s"/tax-enrolments/groups/$groupId/enrolments/HMRC-AS-AGENT~AgentReferenceNumber~$arn"
  def es1Url(arn: String) = s"/enrolment-store-proxy/enrolment-store/enrolments/HMRC-AS-AGENT~AgentReferenceNumber~$arn/groups?type=principal"

  def createKnownFactsSucceeds(arn: String): Unit = {
    stubFor(put(urlEqualTo(s"$createKnownFactsUrl$arn")).willReturn(aResponse().withStatus(200)))
  }

  def createKnownFactsFails(arn: String): Unit = {
    stubFor(put(urlEqualTo(s"$createKnownFactsUrl$arn")).willReturn(aResponse().withStatus(500)))
  }

  def deleteKnownFactsSucceeds(arn: String): Unit = {
    stubFor(delete(urlEqualTo(s"$deleteKnownFactsUrl$arn")).willReturn(aResponse().withStatus(204)))
  }

  def deleteKnownFactsFails(arn: String): Unit = {
    stubFor(delete(urlEqualTo(s"$deleteKnownFactsUrl$arn")).willReturn(aResponse().withStatus(500)))
  }

  def enrolmentSucceeds(groupId: String, arn: String): Unit = {
    stubFor(post(urlEqualTo(enrolmentUrl(groupId, arn))).willReturn(aResponse().withStatus(200)))
  }

  def enrolmentFails(groupId: String, arn: String): Unit = {
    stubFor(post(urlEqualTo(enrolmentUrl(groupId, arn))).willReturn(aResponse().withStatus(500)))
  }

  def allocatedEnrolmentExists(groupId: String, arn: String): Unit = {
    stubFor(post(urlEqualTo(enrolmentUrl(groupId, arn))).willReturn(aResponse().withStatus(500)))
  }

  def allocatedPrincipalEnrolmentExists(arn: String, groupId: String): Unit = {
    stubFor(get(urlEqualTo(es1Url(arn)))
      .willReturn(aResponse()
        .withBody(
          s"""
             |{
             |    "principalGroupIds": [ "$groupId" ],
             |    "delegatedGroupIds": []
             |}
          """.stripMargin)
        .withStatus(200)))
  }

  def allocatedPrincipalEnrolmentNotExists(arn: String): Unit = {
    stubFor(get(urlEqualTo(es1Url(arn)))
      .willReturn(aResponse()
        .withBody(
          s"""
             |{
             |    "principalGroupIds": [],
             |    "delegatedGroupIds": []
             |}
          """.stripMargin)
        .withStatus(200)))
  }

  def allocatedPrincipalEnrolmentFails(arn: String): Unit = {
    stubFor(get(urlEqualTo(es1Url(arn))).willReturn(aResponse().withStatus(500)))
  }
}
