package uk.gov.hmrc.agentsubscription.stubs

import com.github.tomakehurst.wiremock.client.WireMock.{ aResponse, get, stubFor, urlEqualTo }
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import uk.gov.hmrc.agentsubscription.model.Crn

trait CompaniesHouseStub {

  def givenSuccessfulCompaniesHouseResponseMultipleMatches(crn: Crn, surname: String): StubMapping =
    stubFor(
      get(urlEqualTo(s"/companies-house-api-proxy/company/${crn.value}/officers?surname=$surname"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(
              s"""{
               |  "active_count": 928,
               |  "etag": "a7449ea0bc927f42bde410ae5e363175d86b25ba",
               |  "inactive_count": 0,
               |  "items": [
               |    {
               |      "address": {
               |        "address_line_1": "1 Embankment Place",
               |        "address_line_2": "London",
               |        "postal_code": "WC2N 6RH"
               |      },
               |      "appointed_on": "2017-07-01",
               |      "country_of_residence": "United Kingdom",
               |      "date_of_birth": {
               |        "day": 4,
               |        "month": 8,
               |        "year": 1967
               |      },
               |      "links": {
               |        "officer": {
               |          "appointments": "/officers/N8XUMrDCaazalBOxadslhYiSEFg/appointments"
               |        }
               |      },
               |      "name": "$surname, David",
               |      "officer_role": "llp-member"
               |    },
               |    {
               |      "address": {
               |        "address_line_1": "1 Embankment Place",
               |        "locality": "London",
               |        "postal_code": "WC2N 6RH",
               |        "premises": "Pricewaterhousecoopers Llp"
               |      },
               |      "appointed_on": "2009-07-01",
               |      "date_of_birth": {
               |        "month": 4,
               |        "year": 1974
               |      },
               |      "links": {
               |        "officer": {
               |          "appointments": "/officers/0CvZu-XZW8dJwwvmePvJCitAx60/appointments"
               |        }
               |      },
               |      "name": "$surname, Hamish",
               |      "officer_role": "llp-member"
               |    },
               |    {
               |      "address": {
               |        "address_line_1": "1 Embankment Place",
               |        "address_line_2": "London",
               |        "postal_code": "WC2N 6RH"
               |      },
               |      "appointed_on": "2016-07-01",
               |      "country_of_residence": "United Kingdom",
               |      "date_of_birth": {
               |        "month": 2,
               |        "year": 1973
               |      },
               |      "links": {
               |        "officer": {
               |          "appointments": "/officers/9u8qKtX7eIkrZ9cPr6Ucw1Xusw4/appointments"
               |        }
               |      },
               |      "name": "$surname, Iain Blair",
               |      "officer_role": "llp-member"
               |    },
               |    {
               |      "address": {
               |        "address_line_1": "1 Embankment Place",
               |        "locality": "London",
               |        "postal_code": "WC2N 6RH",
               |        "premises": "Pricewaterhousecoopers Llp"
               |      },
               |      "appointed_on": "2012-07-01",
               |      "country_of_residence": "United Kingdom",
               |      "date_of_birth": {
               |        "month": 10,
               |        "year": 1972
               |      },
               |      "links": {
               |        "officer": {
               |          "appointments": "/officers/_Gr_WQ8xjsi8Iar3vKAkbpmtpn0/appointments"
               |        }
               |      },
               |      "name": "$surname, Mark Richard",
               |      "officer_role": "llp-member"
               |    }
               |  ],
               |  "items_per_page": 100,
               |  "kind": "officer-list",
               |  "links": {
               |    "self": "/company/${crn.value}/officers"
               |  },
               |  "resigned_count": 1070,
               |  "start_index": 0,
               |  "total_results": 1998
               |}""".stripMargin)))

  def givenCompaniesHouseOfficersListWithStatus(crn: String, surname: String, status: Int): StubMapping =
    stubFor(
      get(urlEqualTo(s"/companies-house-api-proxy/company/$crn/officers?surname=$surname"))
        .willReturn(
          aResponse()
            .withStatus(status)))

}
