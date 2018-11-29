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

  def requestIsAuthenticated(): AuthStub = {
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
                       |  "affinityGroup": "Agent",
                       |  "allEnrolments": [
                       |  {
                       |    "key": "HMRC-AS-AGENT",
                       |    "identifiers": [
                       |      {
                       |        "key": "AgentReferenceNumber",
                       |        "value": "JARN1234567"
                       |      }
                       |    ],
                       |    "state": "Activated"
                       |  },
                       |  {
                       |    "key": "IR-PAYE-AGENT",
                       |    "identifiers": [
                       |      {
                       |        "key": "IrAgentReference",
                       |        "value": "HZ1234"
                       |      }
                       |    ],
                       |    "state": "Activated"
                       |  },
                       |  {
                       |    "key": "HMRC-AS-AGENT",
                       |    "identifiers": [
                       |      {
                       |        "key": "AnotherIdentifier",
                       |        "value": "not the ARN"
                       |      },
                       |      {
                       |        "key": "AgentReferenceNumber",
                       |        "value": "JARN1234567"
                       |      }
                       |    ],
                       |    "state": "Activated"
                       |  }
                       | ]
                       |}
       """.stripMargin)))
    this
  }

  def requestIsAuthenticatedWithNoEnrolments(): AuthStub = {
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
                       |  "affinityGroup": "Agent",
                       |  "allEnrolments": []
                       |}
       """.stripMargin)))
    this
  }

  def andIsAnAgent(): AuthStub = {
    stubFor(get(urlPathEqualTo(s"/user-details/id/$oid"))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(s"""
                     |{
                     |  "authProviderId": "12345-credId",
                     |  "authProviderType": "GovernmentGateway",
                     |  "affinityGroup": "Agent"
                     |}
         """.stripMargin)))
    this
  }

  // authProviderId and authProviderType are Options in the UserDetails class in the user-details service, so presumably we shouldn't assume they will always be present
  def andIsAnAgentWithoutAuthProvider(): AuthStub = {
    stubFor(get(urlPathEqualTo(s"/user-details/id/$oid"))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(s"""
                     |{
                     |  "affinityGroup": "Agent"
                     |}
         """.stripMargin)))
    this
  }

  def andIsAnNotAgent(): AuthStub = {
    stubFor(get(urlPathEqualTo(s"/user-details/id/$oid"))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(s"""
                     |{
                     |  "authProviderId": "12345-credId",
                     |  "authProviderType": "GovernmentGateway",
                     |  "affinityGroup": "Organisation"
                     |}
         """.stripMargin)))
    this
  }

  def andHasNoEnrolments(): AuthStub = {
    stubFor(get(urlPathEqualTo(s"/auth/oid/$oid/enrolments"))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(s"""
                   |[]
        """.stripMargin)))
    this
  }

  def andHasEnrolments(): AuthStub = {
    stubFor(get(urlPathEqualTo(s"/auth/oid/$oid/enrolments"))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(s"""
                     |[{"key": "IR-SA-AGENT"}]
        """.stripMargin)))
    this
  }
}
