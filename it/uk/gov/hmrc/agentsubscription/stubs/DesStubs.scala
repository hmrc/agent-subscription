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

  val notFoundResponse =
    """
      {
        "code" : "NOT_FOUND"
        "reasons" : "The remote endpoint has indicated that no data can be found."
      }
    """

  val invalidUtrResponse =
    """
       {
        "code": "INVALID_UTR",
         "reason" : "Submission has not passed validation. Invalid parameter UTR."
       }
    """

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
  /*

   */

  def subscriptionSucceeds(utr: String, request: DesSubscriptionRequest): Unit = {
    stubFor(post(urlEqualTo(s"/income-tax-self-assessment/agents/utr/$utr"))
           .withRequestBody(equalToJson(
             s"""
                |{
                |  "regime": "ITSA",
                |  "safeId": "${request.safeId}",
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
    stubFor(post(urlEqualTo(s"/income-tax-self-assessment/agents/utr/$utr"))
      .willReturn(aResponse()
        .withStatus(409)
        .withBody(
          s"""
             |{
             |  "code": "CONFLICT",
             |  "reason": "Duplicate submission"
             |}
               """.stripMargin)))
  }

  def agencyNotRegistered(utr: String): Unit = {
    stubFor(post(urlEqualTo(s"/income-tax-self-assessment/agents/utr/$utr"))
      .willReturn(aResponse()
        .withStatus(404)
        .withBody(
          s"""
             |{
             |  "code": "NOT_FOUND",
             |  "reason": "The remote endpoint has indicated that no data can be found"
             |}
               """.stripMargin)))
  }
}
