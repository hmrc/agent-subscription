package uk.gov.hmrc.agentsubscription.stubs

import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentsubscription.support.WireMockSupport
import com.github.tomakehurst.wiremock.client.WireMock._

trait MappingStubs {

  me: WireMockSupport =>

  def givenMappingCreationWithStatus(arn: Arn, status: Int) =
    stubFor(
      put(urlEqualTo(s"/agent-mapping/mappings/task-list/arn/${arn.value}"))
        .willReturn(aResponse().withStatus(status))
    )

  def givenMappingDetailsCreatedWithStatus(arn: Arn, status: Int) =
    stubFor(
      put(urlEqualTo(s"/agent-mapping/mappings/task-list/details/arn/${arn.value}"))
        .willReturn(aResponse().withStatus(status))
    )
}
