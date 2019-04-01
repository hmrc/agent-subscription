package uk.gov.hmrc.agentsubscription.stubs

import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.WireMock._
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscription.connectors.DesSubscriptionRequest
import uk.gov.hmrc.agentsubscription.model.{ Crn, SubscriptionRequest }
import uk.gov.hmrc.domain.Vrn

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

  private val ctUtrNotFoundResponse = errorResponse("NOT_FOUND", "The back end has indicated that CT UTR cannot be returned.")

  private val invalidCrnResponse = errorResponse("INVALID_CRN", "Submission has not passed validation. Invalid idType/idValue.")

  private val vatRecordNotFoundResponse = errorResponse("NOT_FOUND", "The back end has indicated that vat known facts cannot be returned")

  private val invalidVrnResponse = errorResponse("INVALID_VRN", "Request has not passed validation. Invalid vrn")

  def utrIsUnexpectedlyInvalid(utr: Utr): Unit = utrIsInvalid(utr)

  def utrIsInvalid(utr: Utr = Utr("xyz")): Unit = {
    stubFor(maybeWithDesHeaderCheck(post(urlEqualTo(s"/registration/individual/utr/${utr.value}")))
      .willReturn(
        aResponse()
          .withStatus(400)
          .withBody(invalidUtrResponse)))
  }

  def subscriptionSucceeds(utr: Utr, request: SubscriptionRequest): Unit = {
    stubFor(post(urlEqualTo(s"/registration/agents/utr/${utr.value}"))
      .withRequestBody(equalToJson(
        s"""
           |{
           |  "agencyName": "${request.agency.name}",
           |  "agencyAddress": {
           |    "addressLine1": "${request.agency.address.addressLine1}",
           |    ${request.agency.address.addressLine2.map(l => s""""addressLine2":"$l",""") getOrElse ""}
           |    ${request.agency.address.addressLine3.map(l => s""""addressLine3":"$l",""") getOrElse ""}
           |    ${request.agency.address.addressLine4.map(l => s""""addressLine4":"$l",""") getOrElse ""}
           |    "postalCode": "${request.agency.address.postcode}",
           |    "countryCode": "${request.agency.address.countryCode}"
           |  },
           |  "telephoneNumber": "${request.agency.telephone.get}",
           |  "agencyEmail": "${request.agency.email}"
           |}
              """.stripMargin))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(
          s"""
             |{
             |  "agentRegistrationNumber": "TARN0000001"
             |}
               """.stripMargin)))
  }

  def subscriptionSucceedsWithoutTelephoneNo(utr: Utr, request: SubscriptionRequest): Unit = {
    stubFor(post(urlEqualTo(s"/registration/agents/utr/${utr.value}"))
      .withRequestBody(equalToJson(
        s"""
           |{
           |  "agencyName": "${request.agency.name}",
           |  "agencyAddress": {
           |    "addressLine1": "${request.agency.address.addressLine1}",
           |    ${request.agency.address.addressLine2.map(l => s""""addressLine2":"$l",""") getOrElse ""}
           |    ${request.agency.address.addressLine3.map(l => s""""addressLine3":"$l",""") getOrElse ""}
           |    ${request.agency.address.addressLine4.map(l => s""""addressLine4":"$l",""") getOrElse ""}
           |    "postalCode": "${request.agency.address.postcode}",
           |    "countryCode": "${request.agency.address.countryCode}"
           |  },
           |  "agencyEmail": "${request.agency.email}"
           |}
              """.stripMargin))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(
          s"""
             |{
             |  "agentRegistrationNumber": "TARN0000001"
             |}
               """.stripMargin)))
  }

  def subscriptionSucceeds(utr: Utr, request: DesSubscriptionRequest): Unit = {
    stubFor(maybeWithDesHeaderCheck(post(urlEqualTo(s"/registration/agents/utr/${utr.value}")))
      .withRequestBody(equalToJson(
        s"""
           |{
           |  "agencyName": "${request.agencyName}",
           |  "agencyAddress": {
           |    "addressLine1": "${request.agencyAddress.addressLine1}",
           |    "addressLine2": "${request.agencyAddress.addressLine2.get}",
           |    "postalCode": "${request.agencyAddress.postalCode}",
           |    "countryCode": "${request.agencyAddress.countryCode}"
           |  },
           |  "telephoneNumber": "${request.telephoneNumber.get}",
           |  "agencyEmail": "${request.agencyEmail}"
           |}
          """.stripMargin))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(
          s"""
             |{
             |  "agentRegistrationNumber": "TARN0000001"
             |}
               """.stripMargin)))
  }

  def subscriptionSucceedsWithoutTelephoneNo(utr: Utr, request: DesSubscriptionRequest): Unit = {
    stubFor(maybeWithDesHeaderCheck(post(urlEqualTo(s"/registration/agents/utr/${utr.value}")))
      .withRequestBody(equalToJson(
        s"""
           |{
           |  "agencyName": "${request.agencyName}",
           |  "agencyAddress": {
           |    "addressLine1": "${request.agencyAddress.addressLine1}",
           |    "addressLine2": "${request.agencyAddress.addressLine2.get}",
           |    "postalCode": "${request.agencyAddress.postalCode}",
           |    "countryCode": "${request.agencyAddress.countryCode}"
           |  },
           |  "agencyEmail": "${request.agencyEmail}"
           |}
          """.stripMargin))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(
          s"""
             |{
             |  "agentRegistrationNumber": "TARN0000001"
             |}
               """.stripMargin)))
  }

  def subscriptionAlreadyExists(utr: Utr): Unit = {
    stubFor(maybeWithDesHeaderCheck(post(urlEqualTo(s"/registration/agents/utr/${utr.value}")))
      .willReturn(aResponse()
        .withStatus(409)
        .withBody(errorResponse("CONFLICT", "Duplicate submission"))))
  }

  def agencyNotRegistered(utr: Utr): Unit = {
    stubFor(maybeWithDesHeaderCheck(post(urlEqualTo(s"/registration/agents/utr/${utr.value}")))
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

  def agentRecordExists(utr: Utr, isAnASAgent: Boolean = true, arn: String = "TARN0000001"): Unit = {
    stubFor(maybeWithDesHeaderCheck(get(urlEqualTo(s"/registration/personal-details/utr/${utr.value}"))).willReturn(aResponse()
      .withStatus(200)
      .withBody(
        s"""
          |{
          |    "agentReferenceNumber": "$arn",
          |    "isAnASAgent": $isAnASAgent,
          |    "addressDetails": {
          |        "addressLine1": "AddressLine1 A",
          |        "addressLine2": "AddressLine2 A",
          |        "addressLine3": "AddressLine3 A",
          |        "addressLine4": "AddressLine4 A",
          |        "postalCode": "TF3 4ER",
          |        "countryCode": "GB"
          |    },
          |    "contactDetails": {
          |        "phoneNumber": "0123 456 7890"
          |    },
          |    "agencyDetails": {
          |        "agencyName": "My Agency",
          |        "agencyAddress": {
          |            "addressLine1": "Flat 1",
          |            "addressLine2": "1 Some Street",
          |            "addressLine3": "Anytown",
          |            "addressLine4": "County",
          |            "postalCode": "AA1 2AA",
          |            "countryCode": "GB"
          |        },
          |        "agencyEmail": "agency@example.com"
          |    }
          |}
        """.stripMargin)))
  }

  def agentRecordExistsWithoutContactDetails(utr: Utr, isAnASAgent: Boolean = true, arn: String = "TARN0000001"): Unit = {
    stubFor(maybeWithDesHeaderCheck(get(urlEqualTo(s"/registration/personal-details/utr/${utr.value}"))).willReturn(aResponse()
      .withStatus(200)
      .withBody(
        s"""
           |{
           |    "agentReferenceNumber": "$arn",
           |    "isAnASAgent": $isAnASAgent,
           |    "addressDetails": {
           |        "addressLine1": "AddressLine1 A",
           |        "addressLine2": "AddressLine2 A",
           |        "addressLine3": "AddressLine3 A",
           |        "addressLine4": "AddressLine4 A",
           |        "postalCode": "TF3 4ER",
           |        "countryCode": "GB"
           |    },
           |    "agencyDetails": {
           |        "agencyName": "My Agency",
           |        "agencyAddress": {
           |            "addressLine1": "Flat 1",
           |            "addressLine2": "1 Some Street",
           |            "addressLine3": "Anytown",
           |            "addressLine4": "County",
           |            "postalCode": "AA1 2AA",
           |            "countryCode": "GB"
           |        },
           |        "agencyEmail": "agency@example.com"
           |    }
           |}
        """.stripMargin)))
  }

  def agentRecordExistsWithoutPhoneNumber(utr: Utr, isAnASAgent: Boolean = true, arn: String = "TARN0000001"): Unit = {
    stubFor(maybeWithDesHeaderCheck(get(urlEqualTo(s"/registration/personal-details/utr/${utr.value}"))).willReturn(aResponse()
      .withStatus(200)
      .withBody(
        s"""
           |{
           |    "agentReferenceNumber": "$arn",
           |    "isAnASAgent": $isAnASAgent,
           |    "addressDetails": {
           |        "addressLine1": "AddressLine1 A",
           |        "addressLine2": "AddressLine2 A",
           |        "addressLine3": "AddressLine3 A",
           |        "addressLine4": "AddressLine4 A",
           |        "postalCode": "TF3 4ER",
           |        "countryCode": "GB"
           |    },
           |    "contactDetails": {
           |    },
           |    "agencyDetails": {
           |        "agencyName": "My Agency",
           |        "agencyAddress": {
           |            "addressLine1": "Flat 1",
           |            "addressLine2": "1 Some Street",
           |            "addressLine3": "Anytown",
           |            "addressLine4": "County",
           |            "postalCode": "AA1 2AA",
           |            "countryCode": "GB"
           |        },
           |        "agencyEmail": "agency@example.com"
           |    }
           |}
        """.stripMargin)))
  }

  def agentRecordDoesNotExist(utr: Utr): Unit = {
    stubFor(maybeWithDesHeaderCheck(get(urlEqualTo(s"/registration/personal-details/utr/${utr.value}")))
      .willReturn(aResponse()
        .withStatus(404)
        .withBody(notFoundResponse)))
  }

  def agentRecordFails(): Unit = {
    stubFor(maybeWithDesHeaderCheck(get(urlPathMatching(s"/registration/personal-details/utr/.*")))
      .willReturn(aResponse()
        .withStatus(500)))
  }

  def ctUtrRecordExists(crn: Crn): Unit = {
    stubFor(maybeWithDesHeaderCheck(get(urlEqualTo(s"/corporation-tax/identifiers/crn/${crn.value}"))).willReturn(aResponse()
      .withStatus(200)
      .withBody(
        s"""
           |{
           |    "CTUTR": "1234567890"
           |}
        """.stripMargin)))
  }

  def ctUtrRecordDoesNotExist(crn: Crn): Unit = {
    stubFor(maybeWithDesHeaderCheck(get(urlEqualTo(s"/corporation-tax/identifiers/crn/${crn.value}")))
      .willReturn(aResponse()
        .withStatus(404)
        .withBody(ctUtrNotFoundResponse)))
  }

  def ctUtrRecordFails(): Unit = {
    stubFor(maybeWithDesHeaderCheck(get(urlPathMatching(s"/corporation-tax/identifiers/crn/.*")))
      .willReturn(aResponse()
        .withStatus(500)))
  }

  def crnIsInvalid(crn: Crn): Unit = {
    stubFor(maybeWithDesHeaderCheck(get(urlEqualTo(s"/corporation-tax/identifiers/crn/${crn.value}")))
      .willReturn(
        aResponse()
          .withStatus(400)
          .withBody(invalidCrnResponse)))
  }

  def vatKnownfactsRecordExists(vrn: Vrn): Unit = {
    stubFor(maybeWithDesHeaderCheck(get(urlEqualTo(s"/vat/known-facts/control-list/${vrn.value}"))).willReturn(aResponse()
      .withStatus(200)
      .withBody(
        s"""
           |{
           |    "dateOfReg": "2010-03-31"
           |}
        """.stripMargin)))
  }

  def vatKnownfactsRecordDoesNotExist(vrn: Vrn): Unit = {
    stubFor(maybeWithDesHeaderCheck(get(urlEqualTo(s"/vat/known-facts/control-list/${vrn.value}")))
      .willReturn(aResponse()
        .withStatus(404)
        .withBody(vatRecordNotFoundResponse)))
  }

  def vatKnownfactsRecordFails(): Unit = {
    stubFor(maybeWithDesHeaderCheck(get(urlPathMatching(s"/vat/known-facts/control-list/.*")))
      .willReturn(aResponse()
        .withStatus(500)))
  }

  def vrnIsInvalid(vrn: Vrn): Unit = {
    stubFor(maybeWithDesHeaderCheck(get(urlEqualTo(s"/vat/known-facts/control-list/${vrn.value}")))
      .willReturn(
        aResponse()
          .withStatus(400)
          .withBody(invalidVrnResponse)))
  }

  private def registrationRequest(utr: Utr, isAnAgent: Boolean) =
    post(urlEqualTo(s"/registration/individual/utr/${utr.value}"))
      .withRequestBody(equalToJson(
        s"""
           |{
           |  "requiresNameMatch": false,
           |  "regime": "ITSA",
           |  "isAnAgent": $isAnAgent
           |}
              """.stripMargin))

  def organisationRegistrationExists(utr: Utr, isAnASAgent: Boolean = true, arn: String = "TARN0000001"): Unit = {
    stubFor(maybeWithDesHeaderCheck(registrationRequest(utr, isAnAgent = false))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(
          s"""
             |{
             |  "address":
             |  {
             |    "addressLine1": "AddressLine1 A",
             |    "addressLine2": "AddressLine2 A",
             |    "addressLine3": "AddressLine3 A",
             |    "addressLine4": "AddressLine4 A",
             |    "countryCode": "GB",
             |    "postalCode": "AA1 1AA"
             |  },
             |  "isAnASAgent": $isAnASAgent,
             |  "organisation":
             |  {
             |    "organisationName": "My Agency"
             |  },
             |  "agentReferenceNumber": "$arn",
             |  "contactDetails": {
             |        "emailAddress": "agent1@example.com"
             |    }
             |}
               """.stripMargin)))
  }

  def individualRegistrationExists(utr: Utr, isAnASAgent: Boolean = true): Unit = {
    stubFor(maybeWithDesHeaderCheck(registrationRequest(utr, isAnAgent = false))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(
          s"""
             |{
             |  "address":
             |  {
             |    "addressLine1": "AddressLine1 A",
             |    "addressLine2": "AddressLine2 A",
             |    "addressLine3": "AddressLine3 A",
             |    "addressLine4": "AddressLine4 A",
             |    "countryCode": "GB",
             |    "postalCode": "AA1 1AA"
             |  },
             |  "isAnASAgent": $isAnASAgent,
             |  "individual":
             |  {
             |    "firstName": "First",
             |    "lastName": "Last"
             |  },
             |  "agentReferenceNumber": "AARN0000002",
             |  "contactDetails": {
             |        "emailAddress": "individual@example.com"
             |    }
             |}
               """.stripMargin)))
  }

  def registrationExistsWithNoOrganisationName(utr: Utr, isAnASAgent: Boolean = true): Unit = {
    stubFor(maybeWithDesHeaderCheck(registrationRequest(utr, isAnAgent = false))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(
          s"""
             |{
             |  "address":
             |  {
             |    "addressLine1": "AddressLine1 A",
             |    "addressLine2": "AddressLine2 A",
             |    "addressLine3": "AddressLine3 A",
             |    "addressLine4": "AddressLine4 A",
             |    "countryCode": "GB",
             |    "postalCode": "AA1 1AA"
             |  },
             |  "isAnASAgent": $isAnASAgent,
             |  "contactDetails": {
             |        "emailAddress": "agent1@example.com"
             |    }
             |}
               """.stripMargin)))
  }

  def registrationExistsWithNoPostcode(utr: Utr): Unit = {
    stubFor(maybeWithDesHeaderCheck(registrationRequest(utr, isAnAgent = false))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(
          s"""
             |{
             |  "address":
             |  {
             |    "addressLine1": "AddressLine1 A",
             |    "countryCode": "GB"
             |  },
             |  "isAnASAgent": true,
             |  "contactDetails": {
             |        "emailAddress": "agent1@example.com"
             |    }
             |}
               """.stripMargin)))
  }

  def registrationExistsWithNoAddress(utr: Utr): Unit = {
    stubFor(maybeWithDesHeaderCheck(registrationRequest(utr, isAnAgent = false))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(
          s"""
             |{
             |  "address":
             |  {
             |    "countryCode": "ZZ"
             |  },
             |  "isAnASAgent": true,
             |  "contactDetails": {
             |        "emailAddress": "agent1@example.com"
             |    }
             |}
               """.stripMargin)))
  }

  def registrationExistsWithNoIsAnASAgent(utr: Utr): Unit = {
    stubFor(maybeWithDesHeaderCheck(registrationRequest(utr, isAnAgent = false))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(
          s"""
             |{
             |  "address":
             |  {
             |    "addressLine1": "AddressLine1 A",
             |    "countryCode": "GB"
             |  },
             |  "contactDetails": {
             |        "emailAddress": "agent1@example.com"
             |    }
             |}
               """.stripMargin)))
  }

  def registrationExistsWithNoEmail(utr: Utr): Unit = {
    stubFor(maybeWithDesHeaderCheck(registrationRequest(utr, isAnAgent = false))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(
          s"""
             |{
             |  "address":
             |  {
             |    "addressLine1": "AddressLine1 A",
             |    "countryCode": "GB",
             |    "postalCode": "AA1 1AA"
             |  },
             |  "isAnASAgent": false,
             |  "contactDetails": {
             |    }
             |}
               """.stripMargin)))
  }

  def registrationDoesNotExist(utr: Utr): Unit = {
    stubFor(maybeWithDesHeaderCheck(post(urlEqualTo(s"/registration/individual/utr/${utr.value}")))
      .withRequestBody(equalToJson(
        s"""
           |{
           |  "requiresNameMatch": false,
           |  "regime": "ITSA",
           |  "isAnASAgent": false
           |}
              """.stripMargin, true, true))
      .willReturn(aResponse()
        .withStatus(404)
        .withBody(notFoundResponse)))
  }

  def registrationRequestFails(): Unit = {
    stubFor(maybeWithDesHeaderCheck(post(urlPathMatching(s"/registration/(individual|organisation)/utr/.*")))
      .willReturn(aResponse()
        .withStatus(500)))
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
