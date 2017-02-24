package uk.gov.hmrc.agentsubscription.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import uk.gov.hmrc.agentsubscription.connectors.DesSubscriptionRequest

object DesStubs {
  val matchingUtrForASAgentResponse =
    """
      {
        "isAnASAgent": true,
        "addressDetails" : {
         "postalCode" : "BN12 4SE"
        }
      }
    """

  val matchingUtrForNonASAgentResponse =
    """
      {
        "isAnASAgent": false,
        "addressDetails" : {
         "postalCode" : "BN12 4SE"
        }
      }
    """

  private val notFoundResponse = errorResponse("NOT_FOUND", "The remote endpoint has indicated that no data can be found.")

  private val invalidUtrResponse = errorResponse("INVALID_UTR", "Submission has not passed validation. Invalid parameter UTR.")

  def findMatchForUtrForASAgent(): Unit = {
    stubFor(get(urlEqualTo("/registration/personal-details/utr/0123456789"))
      .willReturn(
        aResponse()
          .withStatus(200)
          .withBody(matchingUtrForASAgentResponse)
      )
    )
  }

  def findMatchForUtrForNonASAgent(): Unit = {
    stubFor(get(urlEqualTo("/registration/personal-details/utr/0123456789"))
      .willReturn(
        aResponse()
          .withStatus(200)
          .withBody(matchingUtrForNonASAgentResponse)
      )
    )
  }

  def noMatchForUtr(): Unit = {
    stubFor(get(urlEqualTo("/registration/personal-details/utr/0000000000"))
      .willReturn(
        aResponse()
          .withStatus(404)
          .withBody(notFoundResponse)
      )
    )
  }

  def utrIsInvalid(): Unit = {
    stubFor(get(urlEqualTo("/registration/personal-details/utr/xyz"))
      .willReturn(
        aResponse()
          .withStatus(400)
          .withBody(invalidUtrResponse)
      )
    )
  }

  def subscriptionSucceeds(utr: String, request: DesSubscriptionRequest): Unit = {
    stubFor(post(urlEqualTo(s"/registration/agents/utr/$utr"))
           .withRequestBody(equalToJson(
             s"""
                |{
                |  "regime": "ITSA",
                |  "agencyName": "${request.agencyName}",
                |  "agencyAddress": {
                |    "addressLine1": "${request.agencyAddress.addressLine1}",
                |    "addressLine2": "${request.agencyAddress.addressLine2}",
                |    "postalCode": "${request.agencyAddress.postalCode}",
                |    "countryCode": "${request.agencyAddress.countryCode}"
                |  },
                |  "telephoneNumber": "${request.telephoneNumber}",
                |  "agencyEmail": "${request.agencyEmail}"
                |}
              """.stripMargin))
        .willReturn(aResponse()
          .withStatus(200)
            .withBody(
              s"""
                 |{
                 |  "agentReferenceNumber": "ARN0001"
                 |}
               """.stripMargin)))
  }

  def subscriptionAlreadyExists(utr: String): Unit = {
    stubFor(post(urlEqualTo(s"/registration/agents/utr/$utr"))
      .willReturn(aResponse()
        .withStatus(409)
        .withBody(errorResponse("CONFLICT", "Duplicate submission"))))
  }

  def agencyNotRegistered(utr: String): Unit = {
    stubFor(post(urlEqualTo(s"/registration/agents/utr/$utr"))
      .willReturn(aResponse()
        .withStatus(404)
        .withBody(
          errorResponse("NOT_FOUND", "The remote endpoint has indicated that no data can be found"))))
  }

  private def errorResponse(code: String, reason: String) =
    s"""
       |{
       |  "code": "$code",
       |  "reason": "$reason"
       |}
     """.stripMargin


  private def registrationRequest(utr: String, isAnAgent: Boolean) =
    post(urlEqualTo(s"/registration/individual/utr/$utr"))
      .withRequestBody(equalToJson(
        s"""
           |{
           |  "requiresNameMatch": false,
           |  "regime": "ITSA",
           |  "isAnAgent": $isAnAgent
           |}
              """.stripMargin))

  def agentWithSafeId(utr: String): Unit = {
    stubFor(registrationRequest(utr, isAnAgent = true).willReturn(aResponse()
        .withStatus(200)
        .withBody(
          s"""
             |{
             |  "safeId": "SAFE00001"
             |}
               """.stripMargin)))
  }

  def agentWithPostcode(utr: String): Unit = {
    stubFor(registrationRequest(utr, isAnAgent = true).willReturn(aResponse()
      .withStatus(200)
      .withBody(
        s"""
           |{
           |  "address":
           |  {
           |    "postalCode": "AA11AA"
           |  }
           |}
               """.stripMargin)))
  }

  def nonAgentWithSafeId(utr: String): Unit = {
    stubFor(registrationRequest(utr, isAnAgent = true)
      .willReturn(aResponse()
        .withStatus(404)
        .withBody(notFoundResponse)))
    stubFor(registrationRequest(utr, isAnAgent = false)
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(
          s"""
             |{
             |  "safeId": "SAFE00002"
             |}
               """.stripMargin)))
  }

  def nonAgentWithPostcode(utr: String): Unit = {
    stubFor(registrationRequest(utr, isAnAgent = true)
      .willReturn(aResponse()
        .withStatus(404)
        .withBody(notFoundResponse)))
    stubFor(registrationRequest(utr, isAnAgent = false)
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(
          s"""
             |{
             |  "address":
             |  {
             |    "postalCode": "AA11AA"
             |  }
             |}
             |""".stripMargin)))
  }

  def agentWithNoRegistration(utr: String): Unit = {
    stubFor(post(urlEqualTo(s"/registration/individual/utr/$utr"))
      .withRequestBody(equalToJson(
        s"""
           |{
           |  "regime": "ITSA",
           |  "requiresNameMatch": "false"
           |}
              """.stripMargin, true, true))
      .willReturn(aResponse()
        .withStatus(404)
        .withBody(notFoundResponse)))
  }
}
