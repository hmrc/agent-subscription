package uk.gov.hmrc.agentsubscription.stubs

import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.WireMock._
import uk.gov.hmrc.agentsubscription.connectors.DesSubscriptionRequest
import uk.gov.hmrc.agentsubscription.model.SubscriptionRequest

trait DesStubs {

  protected def expectedEnvironment: Option[String] = None
  protected def expectedBearerToken: Option[String] = None

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

  def utrIsUnexpectedlyInvalid(utr: String = "xyz"): Unit = utrIsInvalid("0123456789")

  def utrIsInvalid(utr: String = "xyz"): Unit = {
    stubFor(maybeWithDesHeaderCheck(post(urlEqualTo(s"/registration/individual/utr/$utr")))
      .willReturn(
        aResponse()
          .withStatus(400)
          .withBody(invalidUtrResponse)
      )
    )
  }

  def subscriptionSucceeds(utr: String, request: SubscriptionRequest): Unit = {
    stubFor(post(urlEqualTo(s"/registration/agents/utr/$utr"))
      .withRequestBody(equalToJson(
        s"""
           |{
           |  "regime": "ITSA",
           |  "agencyName": "${request.agency.name}",
           |  "agencyAddress": {
           |    "addressLine1": "${request.agency.address.addressLine1}",
           |    ${request.agency.address.addressLine2.map (l => s""""addressLine2":"$l",""") getOrElse ""}
           |    ${request.agency.address.addressLine3.map (l => s""""addressLine3":"$l",""") getOrElse ""}
           |    ${request.agency.address.addressLine4.map (l => s""""addressLine4":"$l",""") getOrElse ""}
           |    "postalCode": "${request.agency.address.postcode}",
           |    "countryCode": "${request.agency.address.countryCode}"
           |  },
           |  "telephoneNumber": "${request.agency.telephone}",
           |  "agencyEmail": "${request.agency.email}"
           |}
              """.stripMargin))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(
          s"""
             |{
             |  "agentRegistrationNumber": "ARN0001"
             |}
               """.stripMargin)))
  }

  def subscriptionSucceeds(utr: String, request: DesSubscriptionRequest): Unit = {
    stubFor(maybeWithDesHeaderCheck(post(urlEqualTo(s"/registration/agents/utr/$utr")))
      .withRequestBody(equalToJson(
        s"""
           |{
           |  "regime": "ITSA",
           |  "agencyName": "${request.agencyName}",
           |  "agencyAddress": {
           |    "addressLine1": "${request.agencyAddress.addressLine1}",
           |    "addressLine2": "${request.agencyAddress.addressLine2.get}",
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
             |  "agentRegistrationNumber": "ARN0001"
             |}
               """.stripMargin)))
  }

  def subscriptionAlreadyExists(utr: String): Unit = {
    stubFor(maybeWithDesHeaderCheck(post(urlEqualTo(s"/registration/agents/utr/$utr")))
      .willReturn(aResponse()
        .withStatus(409)
        .withBody(errorResponse("CONFLICT", "Duplicate submission"))))
  }

  def agencyNotRegistered(utr: String): Unit = {
    stubFor(maybeWithDesHeaderCheck(post(urlEqualTo(s"/registration/agents/utr/$utr")))
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

  def organisationRegistrationExists(utr: String, isAnASAgent: Boolean = true): Unit = {
    stubFor(maybeWithDesHeaderCheck(registrationRequest(utr, isAnAgent = false))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(
          s"""
             |{
             |  "address":
             |  {
             |    "postalCode": "AA1 1AA"
             |  },
             |  "isAnASAgent": $isAnASAgent,
             |  "organisation":
             |  {
             |    "organisationName": "My Agency"
             |  },
             |  "agentReferenceNumber": "TARN0000001"
             |}
               """.stripMargin)))
  }

  def individualRegistrationExists(utr: String, isAnASAgent: Boolean = true): Unit = {
    stubFor(maybeWithDesHeaderCheck(registrationRequest(utr, isAnAgent = false))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(
          s"""
             |{
             |  "address":
             |  {
             |    "postalCode": "AA1 1AA"
             |  },
             |  "isAnASAgent": $isAnASAgent,
             |  "individual":
             |  {
             |    "firstName": "First",
             |    "lastName": "Last"
             |  },
             |  "agentReferenceNumber": "AARN0000002"
             |}
               """.stripMargin)))
  }

  def registrationExistsWithNoOrganisationName(utr: String, isAnASAgent: Boolean = true): Unit = {
    stubFor(maybeWithDesHeaderCheck(registrationRequest(utr, isAnAgent = false))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(
          s"""
             |{
             |  "address":
             |  {
             |    "postalCode": "AA1 1AA"
             |  },
             |  "isAnASAgent": $isAnASAgent
             |}
               """.stripMargin)))
  }

  def registrationExistsWithNoPostcode(utr: String): Unit = {
    stubFor(maybeWithDesHeaderCheck(registrationRequest(utr, isAnAgent = false))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(
          s"""
             |{
             |  "address":
             |  {
             |  },
             |  "isAnASAgent": true
             |}
               """.stripMargin)))
  }

  def registrationDoesNotExist(utr: String): Unit = {
    stubFor(maybeWithDesHeaderCheck(post(urlEqualTo(s"/registration/individual/utr/$utr")))
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
