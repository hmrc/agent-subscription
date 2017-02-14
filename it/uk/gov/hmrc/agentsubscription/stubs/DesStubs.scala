package uk.gov.hmrc.agentsubscription.stubs

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, stubFor, urlEqualTo}

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
    stubFor(get(urlEqualTo("/registration/personal-details/0123456789"))
      .willReturn(
        aResponse()
          .withStatus(200)
          .withBody(matchingUtrForASAgentResponse)
      )
    )
  }

  def findMatchForUtrForNonASAgent(): Unit = {
    stubFor(get(urlEqualTo("/registration/personal-details/0123456789"))
      .willReturn(
        aResponse()
          .withStatus(200)
          .withBody(matchingUtrForNonASAgentResponse)
      )
    )
  }

  def noMatchForUtr(): Unit = {
    stubFor(get(urlEqualTo("/registration/personal-details/0000000000"))
      .willReturn(
        aResponse()
          .withStatus(404)
          .withBody(notFoundResponse)
      )
    )
  }

  def utrIsInvalid(): Unit = {
    stubFor(get(urlEqualTo("/registration/personal-details/xyz"))
      .willReturn(
        aResponse()
          .withStatus(400)
          .withBody(invalidUtrResponse)
      )
    )
  }
}
