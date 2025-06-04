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

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo

trait CitizenDetailsStubs {

  def givencitizenDetailsFoundForNino(
    nino: String,
    dob: String,
    lastName: Option[String] = None
  ) = stubFor(
    get(urlEqualTo(s"/citizen-details/$nino/designatory-details"))
      .willReturn(
        aResponse()
          .withStatus(200)
          .withBody(s"""{
                       |       "etag" : "115",
                       |       "person" : {
                       |         "firstName" : "HIPPY",
                       |         "middleName" : "T",
                       |         ${lastName.map(name => s""" "lastName" : "$name", """).getOrElse("")}
                       |         "title" : "Mr",
                       |         "honours": "BSC",
                       |         "sex" : "M",
                       |         "dateOfBirth" : "$dob",
                       |         "nino" : "TW189213B",
                       |         "deceased" : false
                       |       },
                       |       "address" : {
                       |         "line1" : "26 FARADAY DRIVE",
                       |         "line2" : "PO BOX 45",
                       |         "line3" : "LONDON",
                       |         "postcode" : "CT1 1RQ",
                       |         "startDate": "2009-08-29",
                       |         "country" : "GREAT BRITAIN",
                       |         "type" : "Residential"
                       |       }
                       |}""".stripMargin)
      )
  )

  def givenCitizenDetailsNotFoundForNino(nino: String) = stubFor(
    get(urlEqualTo(s"/citizen-details/$nino/designatory-details"))
      .willReturn(
        aResponse()
          .withStatus(404)
          .withBody(s"""{
                       |  "code" : "INVALID_NINO",
                       |  "message" : "Provided NINO $nino is not valid"
                       |}""".stripMargin)
      )
  )

}
