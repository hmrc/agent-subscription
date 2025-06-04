/*
 * Copyright 2024 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.agentsubscription.stubs

import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscription.connectors.DesSubscriptionRequest
import uk.gov.hmrc.agentsubscription.model.Crn
import uk.gov.hmrc.agentsubscription.model.SubscriptionRequest
import uk.gov.hmrc.domain.Vrn

trait DesStubs {

  protected def expectedEnvironment: Option[String] = None
  protected def expectedBearerToken: Option[String] = None

  val matchingUtrForASAgentResponse = """
      {
        "isAnASAgent": true,
        "addressDetails" : {
         "postalCode" : "BN12 4SE"
        }
      }
    """

  val matchingUtrForNonASAgentResponse = """
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

  def utrIsUnexpectedlyInvalid(utr: Utr) = utrIsInvalid(utr)

  def utrIsInvalid(utr: Utr = Utr("xyz")): StubMapping = stubFor(
    maybeWithDesHeaderCheck(post(urlEqualTo(s"/registration/individual/utr/${utr.value}")))
      .willReturn(
        aResponse()
          .withStatus(400)
          .withBody(invalidUtrResponse)
      )
  )

  def subscriptionSucceeds(
    utr: Utr,
    request: SubscriptionRequest
  ): StubMapping = stubFor(
    post(urlEqualTo(s"/registration/agents/utr/${utr.value}"))
      .withRequestBody(
        equalToJson(s"""
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
              """.stripMargin)
      )
      .willReturn(
        aResponse()
          .withStatus(200)
          .withBody(s"""
                       |{
                       |  "agentRegistrationNumber": "TARN0000001"
                       |}
               """.stripMargin)
      )
  )

  def subscriptionFails(
    utr: Utr,
    request: SubscriptionRequest,
    status: Int
  ): StubMapping = stubFor(
    post(urlEqualTo(s"/registration/agents/utr/${utr.value}"))
      .withRequestBody(
        equalToJson(s"""
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
              """.stripMargin)
      )
      .willReturn(
        aResponse()
          .withStatus(status)
      )
  )

  def subscriptionSucceedsWithoutTelephoneNo(
    utr: Utr,
    request: SubscriptionRequest
  ): StubMapping = stubFor(
    post(urlEqualTo(s"/registration/agents/utr/${utr.value}"))
      .withRequestBody(
        equalToJson(s"""
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
              """.stripMargin)
      )
      .willReturn(
        aResponse()
          .withStatus(200)
          .withBody(s"""
                       |{
                       |  "agentRegistrationNumber": "TARN0000001"
                       |}
               """.stripMargin)
      )
  )

  def subscriptionSucceeds(
    utr: Utr,
    request: DesSubscriptionRequest
  ) = stubFor(
    maybeWithDesHeaderCheck(post(urlEqualTo(s"/registration/agents/utr/${utr.value}")))
      .withRequestBody(equalToJson(s"""
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
      .willReturn(
        aResponse()
          .withStatus(200)
          .withBody(s"""
                       |{
                       |  "agentRegistrationNumber": "TARN0000001"
                       |}
               """.stripMargin)
      )
  )

  def subscriptionSucceedsWithoutTelephoneNo(
    utr: Utr,
    request: DesSubscriptionRequest
  ) = stubFor(
    maybeWithDesHeaderCheck(post(urlEqualTo(s"/registration/agents/utr/${utr.value}")))
      .withRequestBody(equalToJson(s"""
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
      .willReturn(
        aResponse()
          .withStatus(200)
          .withBody(s"""
                       |{
                       |  "agentRegistrationNumber": "TARN0000001"
                       |}
               """.stripMargin)
      )
  )

  def subscriptionAlreadyExists(utr: Utr) = stubFor(
    maybeWithDesHeaderCheck(post(urlEqualTo(s"/registration/agents/utr/${utr.value}")))
      .willReturn(
        aResponse()
          .withStatus(409)
          .withBody(errorResponse("CONFLICT", "Duplicate submission"))
      )
  )

  def agencyNotRegistered(utr: Utr) = stubFor(
    maybeWithDesHeaderCheck(post(urlEqualTo(s"/registration/agents/utr/${utr.value}")))
      .willReturn(
        aResponse()
          .withStatus(404)
          .withBody(errorResponse("NOT_FOUND", "The remote endpoint has indicated that no data can be found"))
      )
  )

  private def errorResponse(
    code: String,
    reason: String
  ) =
    s"""
       |{
       |  "code": "$code",
       |  "reason": "$reason"
       |}
     """.stripMargin

  def agentRecordExists(
    utr: Utr,
    isAnASAgent: Boolean = true,
    arn: String = "TARN0000001"
  ) = stubFor(
    maybeWithDesHeaderCheck(get(urlEqualTo(s"/registration/personal-details/utr/${utr.value}"))).willReturn(
      aResponse()
        .withStatus(200)
        .withBody(s"""
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
        """.stripMargin)
    )
  )

  def agentRecordExistsWithoutContactDetails(
    utr: Utr,
    isAnASAgent: Boolean = true,
    arn: String = "TARN0000001"
  ) = stubFor(
    maybeWithDesHeaderCheck(get(urlEqualTo(s"/registration/personal-details/utr/${utr.value}"))).willReturn(
      aResponse()
        .withStatus(200)
        .withBody(s"""
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
        """.stripMargin)
    )
  )

  def agentRecordExistsWithoutPhoneNumber(
    utr: Utr,
    isAnASAgent: Boolean = true,
    arn: String = "TARN0000001"
  ) = stubFor(
    maybeWithDesHeaderCheck(get(urlEqualTo(s"/registration/personal-details/utr/${utr.value}"))).willReturn(
      aResponse()
        .withStatus(200)
        .withBody(s"""
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
        """.stripMargin)
    )
  )

  def agentRecordDoesNotExist(utr: Utr) = stubFor(
    maybeWithDesHeaderCheck(get(urlEqualTo(s"/registration/personal-details/utr/${utr.value}")))
      .willReturn(
        aResponse()
          .withStatus(404)
          .withBody(notFoundResponse)
      )
  )

  def agentRecordFails() = stubFor(
    maybeWithDesHeaderCheck(get(urlPathMatching(s"/registration/personal-details/utr/.*")))
      .willReturn(
        aResponse()
          .withStatus(500)
      )
  )

  def ctUtrRecordExists(crn: Crn) = stubFor(
    maybeWithDesHeaderCheck(get(urlEqualTo(s"/corporation-tax/identifiers/crn/${crn.value}"))).willReturn(
      aResponse()
        .withStatus(200)
        .withBody(s"""
                     |{
                     |    "CTUTR": "1234567890"
                     |}
        """.stripMargin)
    )
  )

  def ctUtrRecordDoesNotExist(crn: Crn) = stubFor(
    maybeWithDesHeaderCheck(get(urlEqualTo(s"/corporation-tax/identifiers/crn/${crn.value}")))
      .willReturn(
        aResponse()
          .withStatus(404)
          .withBody(ctUtrNotFoundResponse)
      )
  )

  def ctUtrRecordFails() = stubFor(
    maybeWithDesHeaderCheck(get(urlPathMatching(s"/corporation-tax/identifiers/crn/.*")))
      .willReturn(
        aResponse()
          .withStatus(500)
      )
  )

  def crnIsInvalid(crn: Crn) = stubFor(
    maybeWithDesHeaderCheck(get(urlEqualTo(s"/corporation-tax/identifiers/crn/${crn.value}")))
      .willReturn(
        aResponse()
          .withStatus(400)
          .withBody(invalidCrnResponse)
      )
  )

  def vatKnownfactsRecordExists(vrn: Vrn) = stubFor(
    maybeWithDesHeaderCheck(get(urlEqualTo(s"/vat/known-facts/control-list/${vrn.value}"))).willReturn(
      aResponse()
        .withStatus(200)
        .withBody(s"""
                     |{
                     |    "dateOfReg": "2010-03-31"
                     |}
        """.stripMargin)
    )
  )

  def vatKnownfactsRecordDoesNotExist(vrn: Vrn) = stubFor(
    maybeWithDesHeaderCheck(get(urlEqualTo(s"/vat/known-facts/control-list/${vrn.value}")))
      .willReturn(
        aResponse()
          .withStatus(404)
          .withBody(vatRecordNotFoundResponse)
      )
  )

  def vatKnownfactsRecordFails() = stubFor(
    maybeWithDesHeaderCheck(get(urlPathMatching(s"/vat/known-facts/control-list/.*")))
      .willReturn(
        aResponse()
          .withStatus(500)
      )
  )

  def vrnIsInvalid(vrn: Vrn) = stubFor(
    maybeWithDesHeaderCheck(get(urlEqualTo(s"/vat/known-facts/control-list/${vrn.value}")))
      .willReturn(
        aResponse()
          .withStatus(400)
          .withBody(invalidVrnResponse)
      )
  )

  private def registrationRequest(
    utr: Utr,
    isAnAgent: Boolean
  ) = post(urlEqualTo(s"/registration/individual/utr/${utr.value}"))
    .withRequestBody(equalToJson(s"""
                                    |{
                                    |  "requiresNameMatch": false,
                                    |  "regime": "ITSA",
                                    |  "isAnAgent": $isAnAgent
                                    |}
              """.stripMargin))

  def organisationRegistrationExists(
    utr: Utr,
    isAnASAgent: Boolean = true,
    arn: String = "TARN0000001"
  ) = stubFor(
    maybeWithDesHeaderCheck(registrationRequest(utr, isAnAgent = false))
      .willReturn(
        aResponse()
          .withStatus(200)
          .withBody(s"""
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
                       |        "emailAddress": "agent1@example.com",
                       |        "primaryPhoneNumber": "01273111111"
                       |    },
                       |  "agencyDetails": {
                       |      "agencyName": "My Agency",
                       |      "agencyAddress": {
                       |          "addressLine1": "Flat 1",
                       |          "addressLine2": "1 Some Street",
                       |          "addressLine3": "Anytown",
                       |          "addressLine4": "County",
                       |          "postalCode": "AA1 2AA",
                       |          "countryCode": "GB"
                       |      },
                       |      "agencyEmail": "agency@example.com"
                       |  },
                       |  "safeId": "safeId"
                       |}
               """.stripMargin)
      )
  )

  def individualRegistrationExists(
    utr: Utr,
    isAnASAgent: Boolean = true
  ) = stubFor(
    maybeWithDesHeaderCheck(registrationRequest(utr, isAnAgent = false))
      .willReturn(
        aResponse()
          .withStatus(200)
          .withBody(s"""
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
                       |        "emailAddress": "individual@example.com",
                       |        "primaryPhoneNumber": "01273111111"
                       |    },
                       |    "safeId": "safeId"
                       |}
               """.stripMargin)
      )
  )

  def registrationExistsWithNoOrganisationName(
    utr: Utr,
    isAnASAgent: Boolean = true
  ) = stubFor(
    maybeWithDesHeaderCheck(registrationRequest(utr, isAnAgent = false))
      .willReturn(
        aResponse()
          .withStatus(200)
          .withBody(s"""
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
                       |        "emailAddress": "agent1@example.com",
                       |        "primaryPhoneNumber": "01273111111"
                       |    }
                       |}
               """.stripMargin)
      )
  )

  def registrationExistsWithNoPostcode(utr: Utr) = stubFor(
    maybeWithDesHeaderCheck(registrationRequest(utr, isAnAgent = false))
      .willReturn(
        aResponse()
          .withStatus(200)
          .withBody(s"""
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
               """.stripMargin)
      )
  )

  def registrationExistsWithNoAddress(utr: Utr) = stubFor(
    maybeWithDesHeaderCheck(registrationRequest(utr, isAnAgent = false))
      .willReturn(
        aResponse()
          .withStatus(200)
          .withBody(s"""
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
               """.stripMargin)
      )
  )

  def registrationExistsWithNoIsAnASAgent(utr: Utr) = stubFor(
    maybeWithDesHeaderCheck(registrationRequest(utr, isAnAgent = false))
      .willReturn(
        aResponse()
          .withStatus(200)
          .withBody(s"""
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
               """.stripMargin)
      )
  )

  def registrationExistsWithNoEmail(utr: Utr) = stubFor(
    maybeWithDesHeaderCheck(registrationRequest(utr, isAnAgent = false))
      .willReturn(
        aResponse()
          .withStatus(200)
          .withBody(s"""
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
               """.stripMargin)
      )
  )

  def registrationDoesNotExist(utr: Utr) = stubFor(
    maybeWithDesHeaderCheck(registrationRequest(utr, isAnAgent = false))
      .willReturn(
        aResponse()
          .withStatus(404)
          .withBody(notFoundResponse)
      )
  )

  def registrationRequestFails() = stubFor(
    maybeWithDesHeaderCheck(post(urlPathMatching(s"/registration/(individual|organisation)/utr/.*")))
      .willReturn(
        aResponse()
          .withStatus(500)
      )
  )

  def amlsSubscriptionRecordExists(amlsRegNumber: String) = stubFor(
    maybeWithDesHeaderCheck(get(urlEqualTo(s"/anti-money-laundering/subscription/$amlsRegNumber/status")))
      .willReturn(
        aResponse()
          .withStatus(200)
          .withBody(s"""{
                       |"formBundleStatus": "Approved",
                       |"safeId": "xyz",
                       |"currentRegYearStartDate": "2021-01-01",
                       |"currentRegYearEndDate": "2021-12-31",
                       |"suspended": false
                       |}""".stripMargin)
      )
  )

  def amlsSubscriptionRecordFails(
    amlsRegNumber: String,
    status: Int
  ) = stubFor(
    maybeWithDesHeaderCheck(get(urlEqualTo(s"/anti-money-laundering/subscription/$amlsRegNumber/status")))
      .willReturn(
        aResponse()
          .withStatus(status)
          .withBody(notFoundResponse)
      )
  )

  private def maybeWithDesHeaderCheck(mappingBuilder: MappingBuilder): MappingBuilder = maybeWithOptionalAuthorizationHeaderCheck(
    maybeWithEnvironmentHeaderCheck(mappingBuilder)
  )

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
