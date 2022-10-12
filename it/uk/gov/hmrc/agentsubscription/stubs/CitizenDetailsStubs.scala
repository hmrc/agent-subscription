package uk.gov.hmrc.agentsubscription.stubs

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, stubFor, urlEqualTo}

trait CitizenDetailsStubs {

  def givencitizenDetailsFoundForNino(nino: String, dob: String, lastName: Option[String] = None) =
    stubFor(
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

  def givenCitizenDetailsNotFoundForNino(nino: String) =
    stubFor(
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
