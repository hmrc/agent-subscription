package uk.gov.hmrc.agentsubscription.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.libs.json.Json
import uk.gov.hmrc.agentsubscription.model.EmailInformation
import uk.gov.hmrc.agentsubscription.support.WireMockSupport

trait EmailStub {

  me: WireMockSupport =>

  def givenEmailSent(emailInformation: EmailInformation) = {
    val emailInformationJson = Json.toJson(emailInformation).toString()

    stubFor(
      post(urlEqualTo("/hmrc/email"))
        .withRequestBody(similarToJson(emailInformationJson))
        .willReturn(aResponse().withStatus(202)))

  }

  def givenEmailReturns500 = {
    stubFor(
      post(urlEqualTo("/hmrc/email"))
        .willReturn(aResponse().withStatus(500)))
  }

  private def similarToJson(value: String) = equalToJson(value.stripMargin, true, true)

}
