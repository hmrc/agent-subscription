package uk.gov.hmrc.agentsubscription.stubs

import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.WireMock._

trait OverseasDesStubs {

  protected def expectedBearerToken: Option[String] = None

  protected def expectedEnvironment: Option[String] = None

  def organisationRegistrationSucceeds(requestJson: String): Unit = {
    stubFor(maybeWithDesHeaderCheck(registrationRequest(requestJson))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(
          s"""
             |{
             |  "processingDate": "2001-12-17T09:30:47Z",
             |  "sapNumber": "1234567890",
             |  "safeId": "XE0001234567890"
             |}
           """.stripMargin)))
    ()
  }

  def organisationRegistrationSucceeds(): Unit = {
    stubFor(maybeWithDesHeaderCheck(post(urlEqualTo(s"/registration/02.00.00/organisation")))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(
          s"""
             |{
             |  "processingDate": "2001-12-17T09:30:47Z",
             |  "sapNumber": "1234567890",
             |  "safeId": "XE0001234567890"
             |}
           """.stripMargin)))
    ()
  }
  def organisationRegistrationFailsWithNotFound(): Unit = {
    stubFor(maybeWithDesHeaderCheck(post(urlEqualTo(s"/registration/02.00.00/organisation")))
      .willReturn(aResponse()
        .withStatus(404)
        .withBody(notFoundResponse)))
    ()
  }

  def organisationRegistrationFailsWithInvalidPayload(): Unit = {
    stubFor(maybeWithDesHeaderCheck(post(urlEqualTo(s"/registration/02.00.00/organisation")))
      .willReturn(aResponse()
        .withStatus(400)
        .withBody(invalidPayloadResponse)))
    ()
  }

  def organisationRegistrationFailsWithInvalidPostCode(): Unit = {
    stubFor(maybeWithDesHeaderCheck(post(urlEqualTo(s"/registration/02.00.00/organisation")))
      .willReturn(aResponse()
        .withStatus(400)
        .withBody(invalidPayloadResponse)))
    ()
  }

  def subscriptionSucceeds(safeId: String, requestJson: String): Unit = {
    stubFor(maybeWithDesHeaderCheck(subscriptionRequest(safeId, requestJson))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(
          s"""
             |{
             |  "agentRegistrationNumber": "TARN0000001"
             |}
               """.stripMargin)))
    ()
  }

  def subscriptionAlreadyExists(safeId: String, requestJson: String): Unit = {
    stubFor(maybeWithDesHeaderCheck(subscriptionRequest(safeId, requestJson))
      .willReturn(aResponse()
        .withStatus(409)
        .withBody(conflictResponse)))
    ()
  }

  def agencyNotRegistered(safeId: String, requestJson: String): Unit = {
    stubFor(maybeWithDesHeaderCheck(subscriptionRequest(safeId, requestJson))
      .willReturn(aResponse()
        .withStatus(404)
        .withBody(notFoundResponse)))
    ()
  }

  private def registrationRequest(json: String) =
    post(urlEqualTo(s"/registration/02.00.00/organisation"))
      .withRequestBody(equalToJson(json))

  private def subscriptionRequest(safeId: String, json: String) =
    post(urlEqualTo(s"/registration/agents/safeId/$safeId"))
      .withRequestBody(equalToJson(json))

  private val notFoundResponse = errorResponse("NOT_FOUND", "The remote endpoint has indicated that no data can be found.")
  private val invalidPayloadResponse = errorResponse("INVALID_PAYLOAD", "Submission has not passed validation. Invalid Payload.")
  private val conflictResponse = errorResponse("CONFLICT", "Duplicate submission")

  private def errorResponse(code: String, reason: String) =
    s"""
       |{
       |  "code": "$code",
       |  "reason": "$reason"
       |}
     """.stripMargin

  private def maybeWithDesHeaderCheck(mappingBuilder: MappingBuilder): MappingBuilder =
    maybeWithOptionalAuthorizationHeaderCheck(maybeWithEnvironmentHeaderCheck(mappingBuilder))

  private def maybeWithOptionalAuthorizationHeaderCheck(mappingBuilder: MappingBuilder): MappingBuilder =
    expectedBearerToken match {
      case Some(token) => mappingBuilder.withHeader("Authorization", equalTo(s"Bearer $token"))
      case None => mappingBuilder
    }

  private def maybeWithEnvironmentHeaderCheck(mappingBuilder: MappingBuilder): MappingBuilder =
    expectedEnvironment match {
      case Some(environment) => mappingBuilder.withHeader("Environment", equalTo(environment))
      case None => mappingBuilder
    }
}
