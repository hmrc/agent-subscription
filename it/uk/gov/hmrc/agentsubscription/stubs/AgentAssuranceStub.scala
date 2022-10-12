package uk.gov.hmrc.agentsubscription.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import play.api.libs.json.Json
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr}
import uk.gov.hmrc.agentsubscription.connectors.AgentAssuranceConnector.{CreateAmlsRequest, CreateOverseasAmlsRequest}
import uk.gov.hmrc.agentsubscription.model.{AmlsDetails, OverseasAmlsDetails}
import uk.gov.hmrc.http.HeaderCarrier

trait AgentAssuranceStub {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  private val createUrl: String = s"/agent-assurance/amls"

  private val createOverseasAmlsUrl: String = s"/agent-assurance/overseas-agents/amls"

  private def updateUrl(utr: Utr): String = s"/agent-assurance/amls/utr/${utr.value}"

  def createAmlsSucceeds(utr: Utr, amlsDetails: AmlsDetails): StubMapping =
    stubFor(
      post(urlEqualTo(createUrl))
        .withRequestBody(equalToJson(Json.toJson(CreateAmlsRequest(utr, amlsDetails)).toString()))
        .willReturn(
          aResponse()
            .withStatus(201)
        )
    )

  def createAmlsFailsWithStatus(status: Int): StubMapping =
    stubFor(
      post(urlEqualTo(createUrl))
        .willReturn(aResponse().withStatus(status))
    )

  def updateAmlsSucceeds(utr: Utr, arn: Arn, amlsDetails: AmlsDetails): StubMapping =
    stubFor(
      put(urlEqualTo(updateUrl(utr)))
        .withRequestBody(equalToJson(s"""{"value": "${arn.value}"}"""))
        .willReturn(
          aResponse()
            .withBody(Json.toJson(amlsDetails).toString())
            .withStatus(200)
        )
    )

  def updateAmlsFailsWithStatus(utr: Utr, arn: Arn, status: Int): StubMapping =
    stubFor(
      put(urlEqualTo(updateUrl(utr)))
        .withRequestBody(equalToJson(s"""{"value": "${arn.value}"}"""))
        .willReturn(aResponse().withStatus(status))
    )

  def createOverseasAmlsSucceeds(arn: Arn, amlsDetails: OverseasAmlsDetails): StubMapping =
    stubFor(
      post(urlEqualTo(createOverseasAmlsUrl))
        .withRequestBody(equalToJson(Json.toJson(CreateOverseasAmlsRequest(arn, amlsDetails)).toString()))
        .willReturn(
          aResponse()
            .withStatus(201)
        )
    )

  def createOverseasAmlsFailsWithStatus(status: Int): StubMapping =
    stubFor(
      post(urlEqualTo(createOverseasAmlsUrl))
        .willReturn(aResponse().withStatus(status))
    )

  def verifyCreateOverseasAmlsCall(times: Int = 0) =
    verify(times, postRequestedFor(urlEqualTo(createOverseasAmlsUrl)))
}
