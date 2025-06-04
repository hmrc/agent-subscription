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
import uk.gov.hmrc.agentsubscription.model.ApplicationStatus

trait AgentOverseasApplicationStubs {

  val getApplicationUrl =
    s"/agent-overseas-application/application?statusIdentifier=pending&statusIdentifier=accepted&statusIdentifier=attempting_registration&statusIdentifier=registered&statusIdentifier=complete"

  def givenUpdateApplicationStatus(
    appStatus: ApplicationStatus,
    responseStatus: Int,
    requestBody: String = "{}"
  ): Unit = {
    stubFor(
      put(urlEqualTo(s"/agent-overseas-application/application/${appStatus.key}"))
        .withRequestBody(equalToJson(requestBody))
        .willReturn(
          aResponse()
            .withStatus(responseStatus)
        )
    )
    ()
  }

  def givenValidApplication(
    status: String,
    safeId: Option[String] = None,
    businessTradingName: String = "tradingName",
    agencyName: String = "Agency name",
    supervisoryBody: String = "supervisoryName",
    hasAmls: Boolean = true
  ) = stubFor(
    get(urlEqualTo(getApplicationUrl))
      .willReturn(
        aResponse()
          .withStatus(200)
          .withBody(s"""
             [
                       |  {
                       |    "applicationReference": "someValidAppReference",
                       |    ${if (hasAmls)
                        """"amls": {
             |    "supervisoryBody": "supervisoryName",
             |    "membershipNumber": "supervisoryId"},"""
                      else
                        ""}
                       |    "contactDetails": {
                       |      "firstName": "firstName",
                       |      "lastName": "lastName",
                       |      "jobTitle": "jobTitle",
                       |      "businessTelephone": "BUSINESS PHONE 123456789",
                       |      "businessEmail": "email@domain.com"
                       |    },
                       |    "tradingDetails": {
                       |      "tradingName": "$businessTradingName",
                       |      "tradingAddress": {
                       |        "addressLine1": "addressLine1",
                       |        "addressLine2": "addressLine2",
                       |        "countryCode": "CC"
                       |      },
                       |      "isUkRegisteredTaxOrNino": "no",
                       |      "isHmrcAgentRegistered": "no"
                       |    },
                       |    "agencyDetails": {
                       |      "agencyName": "$agencyName",
                       |      "agencyEmail": "agencyemail@domain.com",
                       |      "agencyAddress": {
                       |        "addressLine1": "Mandatory Address Line 1",
                       |        "addressLine2": "Mandatory Address Line 2",
                       |        "countryCode": "IE"
                       |        }
                       |       },
                       |       "status": "$status",
                       |       "authProviderIds": [
                       |        "agentAuthProviderId"
                       |        ],
                       |        "maintainerDetails": {
                       |          "reviewedDate": "2019-02-20T10:35:21.65",
                       |          "reviewerPid": "PID",
                       |          "rejectReasons": [
                       |          "rejected reason"
                       |          ]
                       |         }
                       |   ${safeId.map(id => s""", "safeId" : "$id" """).getOrElse("")}
                       |  }
                       |]
           """.stripMargin)
      )
  )

  def givenInvalidApplication = stubFor(
    get(urlEqualTo(getApplicationUrl))
      .willReturn(
        aResponse()
          .withBody(s"""{}""")
      )
  )

}
