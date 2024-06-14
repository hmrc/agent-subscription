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

package uk.gov.hmrc.agentsubscription.controllers

import play.api.libs.ws.WSClient
import uk.gov.hmrc.agentsubscription.model.Crn
import uk.gov.hmrc.agentsubscription.stubs.{AuthStub, CompaniesHouseStub}
import uk.gov.hmrc.agentsubscription.support.{BaseISpec, Resource}

class CompaniesHouseControllerISpec extends BaseISpec with CompaniesHouseStub with AuthStub {

  val crn = Crn("SC123456")
  val name = "lucas"

  implicit val ws = app.injector.instanceOf[WSClient]

  "GET of /companies-house-api-proxy/company/:crn/officers/:name" should {
    "return a 401 when the user is not authenticated" in {
      requestIsNotAuthenticated()
      val response =
        new Resource(s"/agent-subscription/companies-house-api-proxy/company/$crn/officers/$name", port).get()
      response.status shouldBe 401
    }

    "return a 401 when auth returns unexpected response code in the headers" in {
      requestIsNotAuthenticated(header = "some strange response from auth")
      val response =
        new Resource(s"/agent-subscription/companies-house-api-proxy/company/$crn/officers/$name", port).get()
      response.status shouldBe 401
    }

    "return NotFound if the name does not appear on the officers list" in {
      requestIsAuthenticatedWithNoEnrolments()
      givenCompaniesHouseOfficersListWithStatus(crn.value, "BROWN", 404)
      val response =
        new Resource(s"/agent-subscription/companies-house-api-proxy/company/${crn.value}/officers/BROWN", port).get()
      response.status shouldBe 404
    }

    "return BadRequest if there is an invalid identifier" in {
      requestIsAuthenticatedWithNoEnrolments()
      givenCompaniesHouseOfficersListWithStatus("NOT-VALID", "FERGUSON", 400)
      val response =
        new Resource(s"/agent-subscription/companies-house-api-proxy/company/NOT-VALID/officers/FERGUSON", port).get()
      response.status shouldBe 400
    }

    "return NotFound if the Companies House API token has expired" in {
      requestIsAuthenticatedWithNoEnrolments()
      givenCompaniesHouseOfficersListWithStatus("SC123456", "FERGUSON", 401)
      val response =
        new Resource(s"/agent-subscription/companies-house-api-proxy/company/SC123456/officers/FERGUSON", port).get()
      response.status shouldBe 404
    }

    "return OK if a match was found" in {
      requestIsAuthenticatedWithNoEnrolments()
      givenSuccessfulCompaniesHouseResponseMultipleMatches(Crn("SC123456"), "FERGUSON")
      givenSuccessfulGetCompanyHouseResponse(Crn("SC123456"), companyStatus = "active")
      val response =
        new Resource(s"/agent-subscription/companies-house-api-proxy/company/SC123456/officers/FERGUSON", port).get()
      response.status shouldBe 200
    }

  }

  "GET of /companies-house-api-proxy/company/:crn/status" should {
    "return a 401 when the user is not authenticated" in {
      requestIsNotAuthenticated()
      val response =
        new Resource(s"/agent-subscription/companies-house-api-proxy/company/${crn.value}/status", port).get()
      response.status shouldBe 401
    }

    "return a 401 when auth returns unexpected response code in the headers" in {
      requestIsNotAuthenticated(header = "some strange response from auth")
      val response =
        new Resource(s"/agent-subscription/companies-house-api-proxy/company/${crn.value}/status", port).get()
      response.status shouldBe 401
    }

    "return NotFound if the company does not exist" in {
      requestIsAuthenticatedWithNoEnrolments()
      givenUnsuccessfulGetCompanyHouseResponse(crn, 404)
      val response =
        new Resource(s"/agent-subscription/companies-house-api-proxy/company/${crn.value}/status", port).get()
      response.status shouldBe 404
    }

    "return NotFound if the Companies House API token has expired" in {
      requestIsAuthenticatedWithNoEnrolments()
      givenUnsuccessfulGetCompanyHouseResponse(Crn("NOT-VALID"), 401)
      val response =
        new Resource(s"/agent-subscription/companies-house-api-proxy/company/${crn.value}/status", port).get()
      response.status shouldBe 404
    }

    "return OK if a match was found" in {
      requestIsAuthenticatedWithNoEnrolments()
      givenSuccessfulGetCompanyHouseResponse(crn, "active")
      val response =
        new Resource(s"/agent-subscription/companies-house-api-proxy/company/${crn.value}/status", port).get()
      response.status shouldBe 200
    }

  }

}
