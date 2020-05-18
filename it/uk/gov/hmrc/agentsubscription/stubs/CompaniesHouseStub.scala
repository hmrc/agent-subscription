package uk.gov.hmrc.agentsubscription.stubs

import com.github.tomakehurst.wiremock.client.WireMock.{ aResponse, get, stubFor, urlEqualTo }
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import uk.gov.hmrc.agentsubscription.model.Crn

trait CompaniesHouseStub {

  def givenCompaniesHouseOfficersListFoundForCrn(crn: Crn): StubMapping =
    stubFor(
      get(urlEqualTo(s"/companies-house-api-proxy/company/${crn.value}/officers"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(
              s"""{
                 |  "total_results": 3,
                 |  "items_per_page": 35,
                 |  "etag": "0905d15615b770cd4fcc27fdc1c959474ae4c03e",
                 |  "items": [
                 |    {
                 |      "appointed_on": "2015-04-10",
                 |      "occupation": "Director",
                 |      "country_of_residence": "United Kingdom",
                 |      "date_of_birth": {
                 |        "year": 1948,
                 |        "month": 4
                 |      },
                 |      "officer_role": "director",
                 |      "address": {
                 |        "locality": "London",
                 |        "country": "England",
                 |        "address_line_1": "9 Charcot Road",
                 |        "premises": "Flat 6, Osler Court",
                 |        "postal_code": "NW9 5XW"
                 |      },
                 |      "name": "FERGUSON, Jim",
                 |      "resigned_on": "2019-12-27",
                 |      "links": {
                 |        "officer": {
                 |          "appointments": "/officers/mlvDFJq0QpFX1hTw93U7MJNh_ko/appointments"
                 |        }
                 |      },
                 |      "nationality": "Italian"
                 |    },
                 |    {
                 |      "appointed_on": "2015-04-10",
                 |      "occupation": "Director",
                 |      "country_of_residence": "United Kingdom",
                 |      "date_of_birth": {
                 |        "year": 1948,
                 |        "month": 4
                 |      },
                 |      "officer_role": "director",
                 |      "address": {
                 |        "locality": "London",
                 |        "country": "England",
                 |        "address_line_1": "9 Charcot Road",
                 |        "premises": "Flat 6, Osler Court",
                 |        "postal_code": "NW9 5XW"
                 |      },
                 |      "name": "LUCAS, George",
                 |      "links": {
                 |        "officer": {
                 |          "appointments": "/officers/mlvDFJq0QpFX1hTw93U7MJNh_ko/appointments"
                 |        }
                 |      },
                 |      "nationality": "Italian"
                 |    }
                 |  ],
                 |  "links": {
                 |    "self": "/company/01234567/appointments"
                 |  },
                 |  "active_count": 2,
                 |  "kind": "officer-list",
                 |  "start_index": 0,
                 |  "resigned_count": 1
                 |}""".stripMargin)))

  def givenCompaniesHouseOfficersListWithStatus(crn: String, status: Int): StubMapping =
    stubFor(
      get(urlEqualTo(s"/companies-house-api-proxy/company/$crn/officers"))
        .willReturn(
          aResponse()
            .withStatus(status)))

}
