package uk.gov.hmrc.agentsubscription.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import uk.gov.hmrc.agentsubscription.support.WireMockSupport

trait AuthStub {
  me: WireMockSupport =>

  val oid: String = "556737e15500005500eaf68f"

  def requestIsNotAuthenticated(header: String = "MissingBearerToken"): AuthStub = {
    stubFor(post(urlEqualTo("/auth/authorise")).willReturn(aResponse().withStatus(401).withHeader("WWW-Authenticate", s"""MDTP detail="$header"""")))
    this
  }

  def requestIsAuthenticatedWithNoEnrolments(affinityGroup: String = "Agent"): AuthStub = {
    stubFor(post(urlEqualTo("/auth/authorise"))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(s"""
                       |{
                       |  "new-session":"/auth/oid/$oid/session",
                       |  "enrolments":"/auth/oid/$oid/enrolments",
                       |  "uri":"/auth/oid/$oid",
                       |  "loggedInAt":"2016-06-20T10:44:29.634Z",
                       |  "credentials":{
                       |    "providerId": "12345",
                       |    "providerType": "GovernmentGateway"
                       |  },
                       |  "accounts":{
                       |  },
                       |  "lastUpdated":"2016-06-20T10:44:29.634Z",
                       |  "credentialStrength":"strong",
                       |  "confidenceLevel":50,
                       |  "userDetailsLink":"$wireMockBaseUrl/user-details/id/$oid",
                       |  "levelOfAssurance":"1",
                       |  "previouslyLoggedInAt":"2016-06-20T09:48:37.112Z",
                       |  "groupIdentifier": "groupId",
                       |  "affinityGroup": "$affinityGroup",
                       |  "allEnrolments": []
                       |}
       """.stripMargin)))
    this
  }
}
