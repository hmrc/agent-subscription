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

import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.libs.json.Json
import uk.gov.hmrc.agentmtdidentifiers.model.Utr

import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.scalatest.concurrent.Eventually.eventually
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.time.Seconds
import org.scalatest.time.Span
import play.api.http.Status.CREATED
import play.api.http.Status.UNPROCESSABLE_ENTITY
import uk.gov.hmrc.agentmtdidentifiers.model.Arn

trait HipStubs {

  def hipRegistrationReturns(
    status: Int,
    utr: Utr,
    responseJson: String
  ): Unit = stubFor(
    post(urlEqualTo(s"/etmp/RESTAdapter/registration/UTR/${utr.value}"))
      .withRequestBody(equalToJson(
        """{"requiresNameMatch":false,"regime":"ITSA","isAnAgent":false}"""
      ))
      .willReturn(
        aResponse()
          .withStatus(status)
          .withBody(responseJson)
      )
  )

  def hipSubscriptionSucceeds(
    safeId: String,
    requestJson: String
  ): Unit = stubFor(
    subscriptionRequest(safeId, requestJson)
      .willReturn(
        aResponse()
          .withStatus(201)
          .withBody(Json.obj(
            "success" -> Json.obj(
              "processingDate" -> "2026-01-01T12:00:00Z",
              "arn" -> "TARN0000001"
            )
          ).toString())
      )
  )

  def hipSubscriptionFails(
    safeId: String,
    requestJson: String,
    status: Int,
    body: Option[String] = None
  ): Unit = stubFor(
    subscriptionRequest(safeId, requestJson)
      .willReturn(
        aResponse()
          .withStatus(status)
          .withBody(body.orNull)
      )
  )

  private def subscriptionRequest(
    safeId: String,
    json: String
  ) = post(urlEqualTo(s"/etmp/RESTAdapter/generic/agent/subscription/$safeId"))
    .withRequestBody(equalToJson(json))

  def givenHIPGetAgentRecordSuspendedAgent(
    arn: Arn,
    utr: String = "123456"
  ): StubMapping = stubFor(
    get(urlEqualTo(s"/etmp/RESTAdapter/generic/agent/subscription/${arn.value}"))
      .willReturn(
        okJson(
          s"""
              {
                "success": {
                  "processingDate": "2025-02-25",
                  "utr": $utr,
                  "name": "ABC Accountants",
                  "addr1": "Matheson House",
                  "addr2": "Grange Central",
                  "addr3": "Town Centre",
                  "addr4": "Telford",
                  "postcode": "TF3 4ER",
                  "country": "GB",
                  "phone": "07345678901",
                  "email": "abc@xyz.com",
                  "suspensionStatus": "T",
                  "supervisoryBody": "HMRC",
                  "membershipNumber": "AMLS123",
                  "evidenceObjectReference": "evidence-ref-001"
                }
              }
            """
        )
      )
  )

  def givenHIPGetAgentRecordSuspendedAgentWithStringRegime(
    arn: Arn,
    utr: String = "123456",
    regime: String = "ALL"
  ): StubMapping = stubFor(
    get(urlEqualTo(s"/etmp/RESTAdapter/generic/agent/subscription/${arn.value}"))
      .willReturn(
        okJson(
          s"""
              {
                "success": {
                  "processingDate": "2025-02-25",
                  "utr": $utr,
                  "name": "ABC Accountants",
                  "addr1": "Matheson House",
                  "addr2": "Grange Central",
                  "addr3": "Town Centre",
                  "addr4": "Telford",
                  "postcode": "TF3 4ER",
                  "country": "GB",
                  "phone": "07345678901",
                  "email": "abc@xyz.com",
                  "suspensionStatus": "T",
                  "regime": "$regime",
                  "supervisoryBody": "HMRC",
                  "membershipNumber": "AMLS123",
                  "evidenceObjectReference": "evidence-ref-001"
                }
              }
            """
        )
      )
  )

  def givenHipGetAgentRecord(
    arn: String,
    utr: Option[Utr],
    overseas: Boolean = false
  ): StubMapping = stubFor(
    get(urlEqualTo(s"/etmp/RESTAdapter/generic/agent/subscription/$arn"))
      .willReturn(
        okJson(
          s"""
              {
                "success": {
                  "processingDate": "2025-02-25",
                  "utr": "${utr.map(_.value).getOrElse("")}",
                  "name": "ABC Accountants",
                  "addr1": "Matheson House",
                  "addr2": "Grange Central",
                  "addr3": "Town Centre",
                  "addr4": "Telford",
                  "postcode": "TF3 4ER",
                  "country": "${if (overseas)
              "NZ"
            else
              "GB"}",
                  "phone": "07345678901",
                  "email": "abc@xyz.com",
                  "suspensionStatus": "F",
                  "supervisoryBody": "HMRC",
                  "membershipNumber": "AMLS123",
                  "evidenceObjectReference": "evidence-ref-001"
                }
              }
            """
        )
      )
  )

  def verifyHipGetAgentRecord(
    arn: Arn,
    count: Int = 1
  ): Unit =
    eventually(Timeout(Span(5, Seconds))) {
      verify(
        count,
        getRequestedFor(urlMatching(s"/etmp/RESTAdapter/generic/agent/subscription/${arn.value}"))
      )
    }

  def givenHipAgentIsUnknown404(arn: Arn): StubMapping = {
    stubFor(
      get(urlMatching(s"/etmp/RESTAdapter/generic/agent/subscription/${arn.value}"))
        .willReturn(
          aResponse()
            .withStatus(404)
        )
    )
  }

  def givenHipReturnsServerError(arn: Arn): StubMapping = {
    stubFor(
      get(urlMatching(s"/etmp/RESTAdapter/generic/agent/subscription/${arn.value}"))
        .willReturn(serverError())
    )
  }

  def givenHipAmendAgentRecordSuccess(arn: Arn): StubMapping = stubFor(
    put(urlEqualTo(s"/etmp/RESTAdapter/generic/agent/subscription/${arn.value}"))
      .willReturn(
        okJson("""{"success":{"processingDate":"2024-07-15T09:30:47Z"}}""")
      )
  )

  def givenHipAmendAgentRecordError(
    arn: Arn,
    status: Int
  ): StubMapping = stubFor(
    put(urlEqualTo(s"/etmp/RESTAdapter/generic/agent/subscription/${arn.value}"))
      .willReturn(
        aResponse().withStatus(status)
      )
  )

  def verifyHipAmendAgentRecord(
    arn: Arn,
    count: Int = 1
  ): Unit =
    eventually(Timeout(Span(5, Seconds))) {
      verify(
        count,
        putRequestedFor(urlMatching(s"/etmp/RESTAdapter/generic/agent/subscription/${arn.value}"))
      )
    }

  def givenHipGetRegistrationData(
    utr: Utr,
    isIndividual: Boolean
  ): StubMapping = {
    val registrationDataForOrganisation =
      s"""
         |{
         |  "success": {
         |    "contactDetails" : {},
         |    "organisation" : {
         |        "organisationName" : "My Agency",
         |       "organisationType" : "Not Specified",
         |        "isAGroup" : false
         |     },
         |     "address" : {
         |        "addressLine1" : "Matheson House 165",
         |        "countryCode" : "GB",
         |        "addressLine2" : "Grange Central 165",
         |       "addressLine4" : "Shropshire 165",
         |        "addressLine3" : "Telford 165",
         |        "postalCode" : "TF3 4ER"
         |     },
         |     "isEditable" : false,
         |    "isAnAgent" : true,
         |    "safeId" : "XH0000100100761",
         |    "agentReferenceNumber" : "SARN0001028",
         |    "isAnASAgent" : true,
         |     "isAnIndividual" : false,
         |     "sapNumber" : "0100100761"
         |  }
         |}
       """.stripMargin

    val registrationDataForIndividual =
      s"""
         |{
         |  "success": {
         |     "isAnIndividual" : true,
         |     "isAnASAgent" : true,
         |     "isEditable" : false,
         |     "isAnAgent" : true,
         |     "contactDetails" : {},
         |    "safeId" : "XR0000100115180",
         |    "agentReferenceNumber" : "PARN0002156",
         |    "individual" : {
         |        "firstName" : "First Name QM",
         |        "dateOfBirth" : "1992-05-10",
         |        "lastName" : "Last Name QM"
         |     },
         |    "address" : {
         |        "postalCode" : "TF3 4ER",
         |       "addressLine4" : "AddressFour 190",
         |       "addressLine2" : "AddressTwo 190",
         |       "addressLine1" : "AddressOne 190",
         |       "addressLine3" : "AddressThree 190",
         |       "countryCode" : "GB"
         |     },
         |     "sapNumber" : "0100115180"
         |  }
         |}
       """.stripMargin
    def registrationData(isIndividual: Boolean) =
      if (isIndividual)
        registrationDataForIndividual
      else
        registrationDataForOrganisation
    stubFor(
      post(urlEqualTo(s"/etmp/RESTAdapter/registration/UTR/${utr.value}"))
        .willReturn(
          aResponse()
            .withStatus(CREATED)
            .withBody(registrationData(isIndividual))
        )
    )
  }

  def givenHipGetRegistrationNotFound(utr: Utr): StubMapping = {
    val failureResponseBody422: String = {
      """
        |{
        |  "errors": {
        |    "code": "002",
        |    "processingDate": "2022-01-31T09:26:17Z",
        |    "text": "No Match"
        |  }
        |}
         """.stripMargin
    }
    stubFor(
      post(urlEqualTo(s"/etmp/RESTAdapter/registration/UTR/${utr.value}"))
        .willReturn(
          aResponse()
            .withStatus(UNPROCESSABLE_ENTITY)
            .withBody(failureResponseBody422)
        )
    )
  }

  def verifyHipGetRegistrationData(
    utr: Utr,
    count: Int = 1
  ): Unit =
    eventually(Timeout(Span(5, Seconds))) {
      verify(
        count,
        postRequestedFor(
          urlEqualTo(s"/etmp/RESTAdapter/registration/UTR/${utr.value}")
        )
      )
    }

}
