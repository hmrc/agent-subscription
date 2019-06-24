/*
 * Copyright 2016 HM Revenue & Customs
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

import play.api.libs.json.JsValue
import play.api.libs.ws.WSClient
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscription.stubs.{ AuthStub, DesStubs, TaxEnrolmentsStubs }
import uk.gov.hmrc.agentsubscription.support.{ BaseISpec, Resource }

import scala.language.postfixOps

class RegistrationControllerISpec extends BaseISpec with DesStubs with TaxEnrolmentsStubs with AuthStub {

  implicit val ws = app.injector.instanceOf[WSClient]

  "GET of /registration/:utr/postcode/:postcode" should {
    "return a 401 when the user is not authenticated" in {
      requestIsNotAuthenticated()
      val response = await(new Resource("/agent-subscription/registration/7000000002/postcode/AA1%201AA", port).get)
      response.status shouldBe 401
    }

    "return a 401 when auth returns unexpected response code in the headers" in {
      requestIsNotAuthenticated(header = "some strange response from auth")
      val response = await(new Resource("/agent-subscription/registration/7000000002/postcode/AA1%201AA", port).get)
      response.status shouldBe 401
    }

    "return 404 when no match is found in des" in {
      requestIsAuthenticatedWithNoEnrolments()

      registrationDoesNotExist(Utr("8000000007"))
      val response = await(new Resource("/agent-subscription/registration/8000000007/postcode/AA1%201AA", port).get)
      response.status shouldBe 404
    }

    "return 400 when the UTR is invalid" in {
      requestIsAuthenticatedWithNoEnrolments()
      utrIsInvalid()
      val response = await(new Resource("/agent-subscription/registration/xyz/postcode/AA1%201AA", port).get)
      response.status shouldBe 400
      (response.json \ "code").as[String] shouldBe "INVALID_UTR"
    }

    "return 500 when agent-subscription considers a UTR valid but DES unexpectedly reports it as invalid" in {
      requestIsAuthenticatedWithNoEnrolments()
      utrIsUnexpectedlyInvalid(Utr("7000000002"))
      val response = await(new Resource("/agent-subscription/registration/7000000002/postcode/AA1%201AA", port).get)
      response.status shouldBe 500
    }

    "return 404 when des returns a match for the utr but the post codes do not match" in {
      requestIsAuthenticatedWithNoEnrolments()
      organisationRegistrationExists(Utr("7000000002"))
      val response = await(new Resource("/agent-subscription/registration/7000000002/postcode/BB11BB", port).get)
      response.status shouldBe 404
    }

    "return 404 when des returns a response with no postcode" in {
      requestIsAuthenticatedWithNoEnrolments()
      registrationExistsWithNoPostcode(Utr("7000000002"))
      val response = await(new Resource("/agent-subscription/registration/7000000002/postcode/AA1%201AA", port).get)
      response.status shouldBe 404
    }

    "return 400 when the post code is invalid" in {
      requestIsAuthenticatedWithNoEnrolments()
      val response = await(new Resource("/agent-subscription/registration/7000000002/postcode/1A1%201AA", port).get)
      response.status shouldBe 400
    }

    "return 404 when DES response does not contain addressline1 mandatory field" in {
      requestIsAuthenticatedWithNoEnrolments()
      registrationExistsWithNoAddress(Utr("7000000002"))
      val response = await(new Resource("/agent-subscription/registration/7000000002/postcode/AA1%201AA", port).get)
      response.status shouldBe 404
    }

    "return 500 when DES response does not contain isAnASAgent mandatory field" in {
      requestIsAuthenticatedWithNoEnrolments()
      registrationExistsWithNoIsAnASAgent(Utr("7000000002"))
      val response = await(new Resource("/agent-subscription/registration/7000000002/postcode/AA1%201AA", port).get)
      response.status shouldBe 500
    }

    "return 200 when des returns an AS Agent for the utr and the postcodes match" when {
      "there is a group already allocated the HMRC-AS-AGENT enrolment with their AgentReferenceNumber" in {
        requestIsAuthenticatedWithNoEnrolments()
        organisationRegistrationExists(Utr("7000000002"), true)
        allocatedPrincipalEnrolmentExists("TARN0000001", "SomeAllocatedGroupId")
        val response = await(new Resource("/agent-subscription/registration/7000000002/postcode/AA1%201AA", port).get)
        response.status shouldBe 200
        (response.json \ "isSubscribedToAgentServices").as[Boolean] shouldBe true
        (response.json \ "isSubscribedToETMP").as[Boolean] shouldBe true
        (response.json \ "taxpayerName").as[String] shouldBe "My Agency"
        testBusinessAddress(response.json)
        (response.json \ "emailAddress").as[String] shouldBe "agency@example.com"
      }

      "there is no group already allocated the HMRC-AS-AGENT enrolment with their AgentReferenceNumber" in {
        requestIsAuthenticatedWithNoEnrolments()
        organisationRegistrationExists(Utr("7000000002"), true)
        allocatedPrincipalEnrolmentNotExists("TARN0000001")
        val response = await(new Resource("/agent-subscription/registration/7000000002/postcode/AA1%201AA", port).get)
        response.status shouldBe 200
        (response.json \ "isSubscribedToAgentServices").as[Boolean] shouldBe false
        (response.json \ "isSubscribedToETMP").as[Boolean] shouldBe true
        (response.json \ "taxpayerName").as[String] shouldBe "My Agency"
        testBusinessAddress(response.json)
        (response.json \ "emailAddress").as[String] shouldBe "agency@example.com"
      }
    }

    "return 200 when des returns a non-AS Agent for the utr and the postcodes match" in {
      requestIsAuthenticatedWithNoEnrolments()
      organisationRegistrationExists(Utr("7000000002"), false)
      val response = await(new Resource("/agent-subscription/registration/7000000002/postcode/AA1%201AA", port).get)
      response.status shouldBe 200
      (response.json \ "isSubscribedToAgentServices").as[Boolean] shouldBe false
      (response.json \ "isSubscribedToETMP").as[Boolean] shouldBe false
      (response.json \ "taxpayerName").as[String] shouldBe "My Agency"
      testBusinessAddress(response.json)
      (response.json \ "emailAddress").as[String] shouldBe "agency@example.com"
    }

    "return 200 when des returns an individual for the utr and the postcodes match" in {
      requestIsAuthenticatedWithNoEnrolments()
      individualRegistrationExists(Utr("7000000002"), false)
      val response = await(new Resource("/agent-subscription/registration/7000000002/postcode/AA1%201AA", port).get)
      response.status shouldBe 200
      (response.json \ "isSubscribedToAgentServices").as[Boolean] shouldBe false
      (response.json \ "isSubscribedToETMP").as[Boolean] shouldBe false
      (response.json \ "taxpayerName").as[String] shouldBe "First Last"
      testBusinessAddress(response.json)
      (response.json \ "emailAddress").as[String] shouldBe "individual@example.com"
    }

    "return 200 when des returns no organisation name" in {
      requestIsAuthenticatedWithNoEnrolments()
      registrationExistsWithNoOrganisationName(Utr("7000000002"), false)
      val response = await(new Resource("/agent-subscription/registration/7000000002/postcode/AA1%201AA", port).get)
      response.status shouldBe 200
      (response.json \ "isSubscribedToAgentServices").as[Boolean] shouldBe false
      (response.json \ "isSubscribedToETMP").as[Boolean] shouldBe false
      testBusinessAddress(response.json)
    }

    "return 200 when des returns no email address" in {
      requestIsAuthenticatedWithNoEnrolments()
      registrationExistsWithNoEmail(Utr("7000000002"))
      val response = await(new Resource("/agent-subscription/registration/7000000002/postcode/AA1%201AA", port).get)
      response.status shouldBe 200
      (response.json \ "isSubscribedToAgentServices").as[Boolean] shouldBe false
      (response.json \ "isSubscribedToETMP").as[Boolean] shouldBe false
      (response.json \ "emailAddress").asOpt[String] shouldBe None
    }
  }

  private def testBusinessAddress(json: JsValue): Unit = {
    (json \ "address" \ "addressLine1").as[String] shouldBe "AddressLine1 A"
    (json \ "address" \ "addressLine2").as[String] shouldBe "AddressLine2 A"
    (json \ "address" \ "addressLine3").as[String] shouldBe "AddressLine3 A"
    (json \ "address" \ "addressLine4").as[String] shouldBe "AddressLine4 A"
    (json \ "address" \ "postalCode").as[String] shouldBe "AA1 1AA"
    (json \ "address" \ "countryCode").as[String] shouldBe "GB"
  }
}
