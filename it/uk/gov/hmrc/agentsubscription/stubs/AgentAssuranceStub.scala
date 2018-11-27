package uk.gov.hmrc.agentsubscription.stubs

import java.time.LocalDate

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import play.api.libs.json.Json
import uk.gov.hmrc.agentmtdidentifiers.model.{ Arn, Utr }
import uk.gov.hmrc.agentsubscription.model.AmlsDetails
import uk.gov.hmrc.http.HeaderCarrier

trait AgentAssuranceStub {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  private val createUrl: String = s"/agent-assurance/amls"

  private def updateUrl(utr: Utr): String = s"/agent-assurance/amls/utr/${utr.value}"

  val amlsDetails: AmlsDetails = AmlsDetails(Utr("12345"), "supervisory", "12345", LocalDate.now(), Some(Arn("ARN12345")))

  def createAmlsSucceeds(): StubMapping =
    stubFor(post(urlEqualTo(createUrl))
      .withRequestBody(equalToJson(Json.toJson(amlsDetails).toString()))
      .willReturn(aResponse()
        .withStatus(201)))

  def createAmlsFailsWithStatus(status: Int): StubMapping =
    stubFor(post(urlEqualTo(createUrl))
      .withRequestBody(equalToJson(Json.toJson(amlsDetails).toString()))
      .willReturn(aResponse().withStatus(status)))

  def updateAmlsSucceeds(utr: Utr, arn: Arn): StubMapping =
    stubFor(put(urlEqualTo(updateUrl(utr)))
      .withRequestBody(equalToJson(s"""{"value": "${arn.value}"}"""))
      .willReturn(aResponse()
        .withBody(Json.toJson(amlsDetails.copy(utr = utr, arn = Some(arn))).toString())
        .withStatus(200)))

  def updateAmlsFailsWithStatus(utr: Utr, arn: Arn, status: Int): StubMapping =
    stubFor(put(urlEqualTo(updateUrl(utr)))
      .withRequestBody(equalToJson(s"""{"value": "${arn.value}"}"""))
      .willReturn(aResponse().withStatus(status)))
}
