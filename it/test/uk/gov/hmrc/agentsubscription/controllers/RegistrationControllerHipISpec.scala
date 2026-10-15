/*
 * Copyright 2026 HM Revenue & Customs
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
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscription.stubs.AuthStub
import uk.gov.hmrc.agentsubscription.stubs.HipStubs
import uk.gov.hmrc.agentsubscription.stubs.TaxEnrolmentsStubs
import uk.gov.hmrc.agentsubscription.support.BaseISpec
import uk.gov.hmrc.agentsubscription.support.Resource

class RegistrationControllerHipISpec
extends BaseISpec
with HipStubs
with TaxEnrolmentsStubs
with AuthStub {

  implicit val ws: WSClient = app.injector.instanceOf[WSClient]

  private def hipRegistrationExists(
    utr: Utr,
    isAnASAgent: Option[Boolean] = Some(true),
    isIndividual: Boolean = false,
    postcode: Option[String] = Some("AA1 1AA"),
    addressLine1: Option[String] = Some("AddressLine1 A"),
    name: Option[String] = Some("My Agency"),
    emailAddress: Option[String] = Some("agency@example.com")
  ): Unit = {
    val address =
      Json.obj(
        "addressLine2" -> "AddressLine2 A",
        "addressLine3" -> "AddressLine3 A",
        "addressLine4" -> "AddressLine4 A",
        "countryCode" -> "GB"
      ) ++
        addressLine1.map(value => Json.obj("addressLine1" -> value)).getOrElse(Json.obj()) ++
        postcode.map(value => Json.obj("postalCode" -> value)).getOrElse(Json.obj())

    val party =
      if (isIndividual)
        Json.obj("individual" -> Json.obj("firstName" -> "First", "lastName" -> "Last"))
      else
        name.map(value => Json.obj("organisation" -> Json.obj("organisationName" -> value))).getOrElse(Json.obj())

    val agencyDetails = emailAddress.map(value => Json.obj("agencyDetails" -> Json.obj("agencyEmail" -> value))).getOrElse(Json.obj())

    val response =
      Json.obj(
        "address" -> address,
        "safeId" -> "safeId",
        "agentReferenceNumber" -> "TARN0000001"
      ) ++
        isAnASAgent.map(value => Json.obj("isAnASAgent" -> value)).getOrElse(Json.obj()) ++
        party ++
        agencyDetails

    hipRegistrationReturns(
      201,
      utr,
      Json.obj("success" -> response).toString
    )
  }

  "GET of /registration/:utr/postcode/:postcode" should {
    "return a 401 when the user is not authenticated" in {
      requestIsNotAuthenticated()
      val response = new Resource("/agent-subscription/registration/7000000002/postcode/AA1%201AA", port).get()
      response.status shouldBe 401
    }

    "return a 401 when auth returns unexpected response code in the headers" in {
      requestIsNotAuthenticated(header = "some strange response from auth")
      val response = new Resource("/agent-subscription/registration/7000000002/postcode/AA1%201AA", port).get()
      response.status shouldBe 401
    }

    "return 404 when no match is found in HIP" in {
      requestIsAuthenticatedWithNoEnrolments()

      givenHipGetRegistrationNotFound(Utr("8000000007"))
      val response = new Resource("/agent-subscription/registration/8000000007/postcode/AA1%201AA", port).get()
      response.status shouldBe 404
    }

    "return 400 when the UTR is invalid" in {
      requestIsAuthenticatedWithNoEnrolments()
      val response = new Resource("/agent-subscription/registration/xyz/postcode/AA1%201AA", port).get()
      response.status shouldBe 400
      (response.json \ "code").as[String] shouldBe "INVALID_UTR"
    }

    "return 500 when agent-subscription considers a UTR valid but HIP unexpectedly reports it as invalid" in {
      requestIsAuthenticatedWithNoEnrolments()
      hipRegistrationReturns(
        400,
        Utr("7000000002"),
        "{}"
      )
      val response = new Resource("/agent-subscription/registration/7000000002/postcode/AA1%201AA", port).get()
      response.status shouldBe 500
    }

    "return 404 when HIP returns a match for the utr but the post codes do not match" in {
      requestIsAuthenticatedWithNoEnrolments()
      hipRegistrationExists(Utr("7000000002"))
      val response = new Resource("/agent-subscription/registration/7000000002/postcode/BB11BB", port).get()
      response.status shouldBe 404
    }

    "return 404 when HIP returns a response with no postcode" in {
      requestIsAuthenticatedWithNoEnrolments()
      hipRegistrationExists(Utr("7000000002"), postcode = None)
      val response = new Resource("/agent-subscription/registration/7000000002/postcode/AA1%201AA", port).get()
      response.status shouldBe 404
    }

    "return 400 when the post code is invalid" in {
      requestIsAuthenticatedWithNoEnrolments()
      val response = new Resource("/agent-subscription/registration/7000000002/postcode/1A1%201AA", port).get()
      response.status shouldBe 400
    }

    "return 404 when HIP response does not contain addressline1 mandatory field" in {
      requestIsAuthenticatedWithNoEnrolments()
      hipRegistrationExists(Utr("7000000002"), addressLine1 = None)
      val response = new Resource("/agent-subscription/registration/7000000002/postcode/AA1%201AA", port).get()
      response.status shouldBe 404
    }

    "return 404 when HIP response does not contain isAnASAgent mandatory field" in {
      requestIsAuthenticatedWithNoEnrolments()
      hipRegistrationExists(Utr("7000000002"), isAnASAgent = None)
      val response = new Resource("/agent-subscription/registration/7000000002/postcode/AA1%201AA", port).get()
      response.status shouldBe 404
    }

    "return 200 when HIP returns an AS Agent for the utr and the postcodes match" when {
      "there is a group already allocated the HMRC-AS-AGENT enrolment with their AgentReferenceNumber" in {
        requestIsAuthenticatedWithNoEnrolments()
        hipRegistrationExists(Utr("7000000002"), isAnASAgent = Some(true))
        allocatedPrincipalEnrolmentExists("TARN0000001", "SomeAllocatedGroupId")
        val response = new Resource("/agent-subscription/registration/7000000002/postcode/AA1%201AA", port).get()
        response.status shouldBe 200
        (response.json \ "isSubscribedToAgentServices").as[Boolean] shouldBe true
        (response.json \ "isSubscribedToETMP").as[Boolean] shouldBe true
        (response.json \ "taxpayerName").as[String] shouldBe "My Agency"
        testBusinessAddress(response.json)
        (response.json \ "emailAddress").as[String] shouldBe "agency@example.com"
      }

      "there is no group already allocated the HMRC-AS-AGENT enrolment with their AgentReferenceNumber" in {
        requestIsAuthenticatedWithNoEnrolments()
        hipRegistrationExists(Utr("7000000002"), isAnASAgent = Some(true))
        allocatedPrincipalEnrolmentNotExists("TARN0000001")
        val response = new Resource("/agent-subscription/registration/7000000002/postcode/AA1%201AA", port).get()
        response.status shouldBe 200
        (response.json \ "isSubscribedToAgentServices").as[Boolean] shouldBe false
        (response.json \ "isSubscribedToETMP").as[Boolean] shouldBe true
        (response.json \ "taxpayerName").as[String] shouldBe "My Agency"
        testBusinessAddress(response.json)
        (response.json \ "emailAddress").as[String] shouldBe "agency@example.com"
      }
    }

    "return 200 when HIP returns a non-AS Agent for the utr and the postcodes match" in {
      requestIsAuthenticatedWithNoEnrolments()
      hipRegistrationExists(Utr("7000000002"), isAnASAgent = Some(false))
      val response = new Resource("/agent-subscription/registration/7000000002/postcode/AA1%201AA", port).get()
      response.status shouldBe 200
      (response.json \ "isSubscribedToAgentServices").as[Boolean] shouldBe false
      (response.json \ "isSubscribedToETMP").as[Boolean] shouldBe false
      (response.json \ "taxpayerName").as[String] shouldBe "My Agency"
      testBusinessAddress(response.json)
      (response.json \ "emailAddress").as[String] shouldBe "agency@example.com"
    }

    "return 200 when HIP returns an individual for the utr and the postcodes match" in {
      requestIsAuthenticatedWithNoEnrolments()
      hipRegistrationExists(
        Utr("7000000002"),
        isAnASAgent = Some(false),
        isIndividual = true,
        emailAddress = Some("individual@example.com")
      )
      val response = new Resource("/agent-subscription/registration/7000000002/postcode/AA1%201AA", port).get()
      response.status shouldBe 200
      (response.json \ "isSubscribedToAgentServices").as[Boolean] shouldBe false
      (response.json \ "isSubscribedToETMP").as[Boolean] shouldBe false
      (response.json \ "taxpayerName").as[String] shouldBe "First Last"
      testBusinessAddress(response.json)
      (response.json \ "emailAddress").as[String] shouldBe "individual@example.com"
    }

    "return 200 when HIP returns no organisation name" in {
      requestIsAuthenticatedWithNoEnrolments()
      hipRegistrationExists(
        Utr("7000000002"),
        isAnASAgent = Some(false),
        name = None
      )
      val response = new Resource("/agent-subscription/registration/7000000002/postcode/AA1%201AA", port).get()
      response.status shouldBe 200
      (response.json \ "isSubscribedToAgentServices").as[Boolean] shouldBe false
      (response.json \ "isSubscribedToETMP").as[Boolean] shouldBe false
      testBusinessAddress(response.json)
    }

    "return 200 when HIP returns no email address" in {
      requestIsAuthenticatedWithNoEnrolments()
      hipRegistrationExists(
        Utr("7000000002"),
        isAnASAgent = Some(false),
        emailAddress = None
      )
      val response = new Resource("/agent-subscription/registration/7000000002/postcode/AA1%201AA", port).get()
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
    ()
  }

}
