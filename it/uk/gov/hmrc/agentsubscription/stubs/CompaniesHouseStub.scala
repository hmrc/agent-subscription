package uk.gov.hmrc.agentsubscription.stubs

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, stubFor, urlEqualTo}
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import uk.gov.hmrc.agentsubscription.model.Crn

trait CompaniesHouseStub {

  def givenSuccessfulCompaniesHouseResponseMultipleMatches(crn: Crn, surname: String): StubMapping =
    stubFor(
      get(urlEqualTo(s"/companies-house-api-proxy/company/${crn.value}/officers?surname=$surname"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(s"""{
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
                         |}""".stripMargin)
        )
    )

  def givenCompaniesHouseOfficersListWithStatus(crn: String, surname: String, status: Int): StubMapping =
    stubFor(
      get(urlEqualTo(s"/companies-house-api-proxy/company/$crn/officers?surname=$surname"))
        .willReturn(
          aResponse()
            .withStatus(status)
        )
    )

  def givenUnsuccessfulGetCompanyHouseResponse(crn: Crn, statusResponse: Int): StubMapping =
    stubFor(
      get(urlEqualTo(s"/companies-house-api-proxy/company/${crn.value}"))
        .willReturn(
          aResponse()
            .withStatus(statusResponse)
        )
    )

  def givenSuccessfulGetCompanyHouseResponse(crn: Crn, companyStatus: String): StubMapping =
    stubFor(
      get(urlEqualTo(s"/companies-house-api-proxy/company/${crn.value}"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(s"""{
                         |  "accounts" : {
                         |    "accounting_reference_date" : {
                         |      "day" : "integer",
                         |      "month" : "integer"
                         |    },
                         |    "last_accounts" : {
                         |      "made_up_to" : "date",
                         |      "type" : "string"
                         |    },
                         |    "next_due" : "date",
                         |    "next_made_up_to" : "date",
                         |    "overdue" : "boolean"
                         |  },
                         |  "annual_return" : {
                         |    "last_made_up_to" : "date",
                         |    "next_due" : "date",
                         |    "next_made_up_to" : "date",
                         |    "overdue" : "boolean"
                         |  },
                         |  "branch_company_details" : {
                         |    "business_activity" : "string",
                         |    "parent_company_name" : "string",
                         |    "parent_company_number" : "string"
                         |  },
                         |  "can_file" : "boolean",
                         |  "company_name" : "Watford Microbreweries",
                         |  "company_number" : "${crn.value}",
                         |  "company_status" : "$companyStatus",
                         |  "company_status_detail" : "string",
                         |  "confirmation_statement" : {
                         |    "last_made_up_to" : "date",
                         |    "next_due" : "date",
                         |    "next_made_up_to" : "date",
                         |    "overdue" : "boolean"
                         |  },
                         |  "date_of_creation" : "date",
                         |  "date_of_dissolution" : "date",
                         |  "etag" : "string",
                         |  "foreign_company_details" : {
                         |    "accounting_requirement" : {
                         |      "foreign_account_type" : "string",
                         |      "terms_of_account_publication" : "string"
                         |    },
                         |    "accounts" : {
                         |      "account_period_from" : {
                         |        "day" : "integer",
                         |        "month" : "integer"
                         |      },
                         |      "account_period_to" : {
                         |        "day" : "integer",
                         |        "month" : "integer"
                         |      },
                         |      "must_file_within" : {
                         |        "months" : "integer"
                         |      }
                         |    },
                         |    "business_activity" : "string",
                         |    "company_type" : "string",
                         |    "governed_by" : "string",
                         |    "is_a_credit_finance_institution" : "boolean",
                         |    "originating_registry" : {
                         |      "country" : "string",
                         |      "name" : "string"
                         |    },
                         |    "registration_number" : "string"
                         |  },
                         |  "has_been_liquidated" : "boolean",
                         |  "has_charges" : "boolean",
                         |  "has_insolvency_history" : "boolean",
                         |  "is_community_interest_company" : "boolean",
                         |  "jurisdiction" : "string",
                         |  "last_full_members_list_date" : "date",
                         |  "links" : {
                         |    "persons_with_significant_control_list" : "string",
                         |    "persons_with_significant_control_statements_list" : "string",
                         |    "self" : "string"
                         |  },
                         |  "officer_summary" : {
                         |    "active_count" : "integer",
                         |    "officers" : [
                         |      {
                         |        "appointed_on" : "date",
                         |        "date_of_birth" : {
                         |          "day" : 23,
                         |          "month" : 4,
                         |          "year" : 1948
                         |        },
                         |        "name" : "Jim Ferguson",
                         |        "officer_role" : "director"
                         |      }
                         |    ],
                         |    "resigned_count" : "integer"
                         |  },
                         |  "registered_office_address" : {
                         |    "address_line_1" : "string",
                         |    "address_line_2" : "string",
                         |    "care_of" : "string",
                         |    "country" : "string",
                         |    "locality" : "string",
                         |    "po_box" : "string",
                         |    "postal_code" : "string",
                         |    "premises" : "string",
                         |    "region" : "string"
                         |  },
                         |  "registered_office_is_in_dispute" : "boolean",
                         |  "sic_codes" : [
                         |    "string"
                         |  ],
                         |  "type" : "string",
                         |  "undeliverable_registered_office_address" : "boolean"
                         |}""".stripMargin)
        )
    )

}
