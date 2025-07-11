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

import com.github.tomakehurst.wiremock.client.WireMock.verify
import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.libs.ws.WSClient
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentsubscription.model.ApplicationStatus.AttemptingRegistration
import uk.gov.hmrc.agentsubscription.model.ApplicationStatus.Complete
import uk.gov.hmrc.agentsubscription.model.ApplicationStatus.Registered
import uk.gov.hmrc.agentsubscription.model._
import uk.gov.hmrc.agentsubscription.stubs._
import uk.gov.hmrc.agentsubscription.support.BaseISpec
import uk.gov.hmrc.agentsubscription.support.Resource

class SubscriptionControllerForOverseasISpec
extends BaseISpec
with OverseasDesStubs
with AuthStub
with AgentOverseasApplicationStubs
with AgentAssuranceStub
with TaxEnrolmentsStubs
with EmailStub {

  private val arn = "TARN0000001"
  private val stubbedGroupId = "groupId"
  private val safeId = SafeId("XE0001234567890")
  implicit val ws: WSClient = app.injector.instanceOf[WSClient]
  private val safeIdJson = s"""{ "safeId": "${safeId.value}"}"""
  private val eacdRetryCount = 3
  private val amlsDetails = OverseasAmlsDetails("supervisoryName", Some("supervisoryId"))
  val emailInfo = EmailInformation(
    Seq("agencyemail@domain.com"),
    "agent_services_account_created",
    Map("agencyName" -> "Agency name", "arn" -> "TARN0000001")
  )

  "creating a subscription" should {
    "return a successful response containing the ARN" when {
      "all fields are populated" in {
        requestIsAuthenticatedWithNoEnrolments()
        givenValidApplication("accepted")
        givenUpdateApplicationStatus(AttemptingRegistration, 204)
        organisationRegistrationSucceeds()
        givenUpdateApplicationStatus(
          Registered,
          204,
          safeIdJson
        )
        subscriptionSucceeds(safeId.value, agencyDetailsJson)
        allocatedPrincipalEnrolmentNotExists(arn)
        deleteKnownFactsSucceeds(arn)
        createKnownFactsSucceeds(arn)
        enrolmentSucceeds(stubbedGroupId, arn)
        createOverseasAmlsSucceeds(Arn(arn), amlsDetails)
        givenUpdateApplicationStatus(
          Complete,
          204,
          s"""{"arn" : "$arn"}"""
        )
        givenEmailSent(emailInfo)

        val result = doSubscriptionRequest

        result.status shouldBe 201
        (result.json \ "arn").as[String] shouldBe arn

        verifyApiCalls(
          attemptingRegistration = 1,
          etmpRegistration = 1,
          registered = 1,
          subscription = 1,
          allocatedPrincipalEnrolment = 1,
          deleteKnownFact = 1,
          createKnownFact = 1,
          enrol = 1,
          amls = 1,
          complete = 1
        )
      }

      "there are no amls details" in {
        requestIsAuthenticatedWithNoEnrolments()
        givenValidApplication("accepted", hasAmls = false)
        givenUpdateApplicationStatus(AttemptingRegistration, 204)
        organisationRegistrationSucceeds()
        givenUpdateApplicationStatus(
          Registered,
          204,
          safeIdJson
        )
        subscriptionSucceeds(safeId.value, agencyDetailsJson)
        allocatedPrincipalEnrolmentNotExists(arn)
        deleteKnownFactsSucceeds(arn)
        createKnownFactsSucceeds(arn)
        enrolmentSucceeds(stubbedGroupId, arn)
        givenUpdateApplicationStatus(
          Complete,
          204,
          s"""{"arn" : "$arn"}"""
        )
        givenEmailSent(emailInfo)

        val result = doSubscriptionRequest

        result.status shouldBe 201
        (result.json \ "arn").as[String] shouldBe arn

        verifyApiCalls(
          attemptingRegistration = 1,
          etmpRegistration = 1,
          registered = 1,
          subscription = 1,
          allocatedPrincipalEnrolment = 1,
          deleteKnownFact = 1,
          createKnownFact = 1,
          enrol = 1,
          complete = 1
        )
      }

      "the application is in the 'registered' state then the DES registration API is not called but the subscription/enrolment is re-attempted" in {
        aSuccessfulSubscriptionForAlreadyRegisteredAcceptedApplication("registered")
      }

      "the application is in the 'complete' state and there is no enrolment currently allocated to any group (i.e. user de-enrolled) then the subscription/enrolment is re-attempted" in {
        aSuccessfulSubscriptionForAlreadyRegisteredAcceptedApplication("complete")
      }

      def aSuccessfulSubscriptionForAlreadyRegisteredAcceptedApplication(applicationStatus: String) = {
        requestIsAuthenticatedWithNoEnrolments()
        givenValidApplication(applicationStatus, Some(safeId.value))
        subscriptionSucceeds(safeId.value, agencyDetailsJson)
        allocatedPrincipalEnrolmentNotExists(arn)
        deleteKnownFactsSucceeds(arn)
        createKnownFactsSucceeds(arn)
        enrolmentSucceeds(stubbedGroupId, arn)
        createOverseasAmlsSucceeds(Arn(arn), amlsDetails)
        givenUpdateApplicationStatus(
          Complete,
          204,
          s"""{"arn" : "$arn"}"""
        )
        givenEmailSent(emailInfo)

        val result = doSubscriptionRequest

        result.status shouldBe 201
        (result.json \ "arn").as[String] shouldBe arn

        verifyApiCalls(
          attemptingRegistration = 0,
          etmpRegistration = 0,
          registered = 0,
          subscription = 1,
          allocatedPrincipalEnrolment = 1,
          deleteKnownFact = 1,
          createKnownFact = 1,
          enrol = 1,
          amls = 1,
          complete = 1
        )
      }

      "creating amls record fails with 409 Conflict because a record already exists" in {
        requestIsAuthenticatedWithNoEnrolments()
        givenValidApplication("registered", Some(safeId.value))
        subscriptionSucceeds(safeId.value, agencyDetailsJson)
        allocatedPrincipalEnrolmentNotExists(arn)
        deleteKnownFactsSucceeds(arn)
        createKnownFactsSucceeds(arn)
        enrolmentSucceeds(stubbedGroupId, arn)
        createOverseasAmlsFailsWithStatus(409)
        givenUpdateApplicationStatus(
          Complete,
          204,
          s"""{"arn" : "$arn"}"""
        )
        givenEmailSent(emailInfo)

        val result = doSubscriptionRequest

        result.status shouldBe 201
        (result.json \ "arn").as[String] shouldBe arn

        verifyApiCalls(
          attemptingRegistration = 0,
          etmpRegistration = 0,
          registered = 0,
          subscription = 1,
          allocatedPrincipalEnrolment = 1,
          deleteKnownFact = 1,
          createKnownFact = 1,
          enrol = 1,
          amls = 1,
          complete = 1
        )
      }
    }

    "return Conflict if there is an existing HMRC-AS-AGENT enrolment for their Arn already allocated to some group" in {
      requestIsAuthenticatedWithNoEnrolments()
      givenValidApplication("registered", Some(safeId.value))
      subscriptionSucceeds(safeId.value, agencyDetailsJson)
      allocatedPrincipalEnrolmentExists(arn, "someOtherGroupId")

      val result = doSubscriptionRequest

      result.status shouldBe 409

      verifyApiCalls(
        attemptingRegistration = 0,
        etmpRegistration = 0,
        registered = 0,
        subscription = 1,
        allocatedPrincipalEnrolment = 1,
        deleteKnownFact = 0,
        createKnownFact = 0,
        enrol = 0,
        amls = 0,
        complete = 0
      )

    }

    "return Forbidden" when {
      "current application status is attempting_registration" in {
        requestIsAuthenticatedWithNoEnrolments()
        givenValidApplication("attempting_registration")

        val result = doSubscriptionRequest

        result.status shouldBe 403
        verifyApiCalls(0, 0, 0, 0, 0)
      }

      "the user does not have Agent affinity" in {
        requestIsAuthenticatedWithNoEnrolments(affinityGroup = "Individual")
        doSubscriptionRequest.status shouldBe 403

        verify(0, getRequestedFor(urlEqualTo(getApplicationUrl.toString)))
      }
    }

    "return a 500 error if " when {
      "the current application was subjected to validation and was found to have an invalid value (for example, an Agency Name that contains a disallowed character)" in {
        requestIsAuthenticatedWithNoEnrolments()

        val invalidAgencyName = "Acme & Sons" // Ampersands are not allowed for the agency name
        givenValidApplication("accepted", agencyName = invalidAgencyName)

        val result = doSubscriptionRequest

        result.status shouldBe 500
        (result.json \ "statusCode").as[Int] shouldBe 500
        (result.json \ "message")
          .as[String] shouldBe "JsResultException(errors:List((/agencyName,List(JsonValidationError(List(error.name.invalid),List())))))"

        verifyApiCalls(
          attemptingRegistration = 0,
          etmpRegistration = 0,
          registered = 0,
          subscription = 0,
          allocatedPrincipalEnrolment = 0,
          deleteKnownFact = 0,
          createKnownFact = 0,
          enrol = 0,
          amls = 0,
          complete = 0
        )
      }

      "etmp registration fails" in {
        requestIsAuthenticatedWithNoEnrolments()
        givenValidApplication("accepted")
        givenUpdateApplicationStatus(AttemptingRegistration, 204)
        organisationRegistrationFailsWithNotFound()

        val result = doSubscriptionRequest

        result.status shouldBe 500

        verifyApiCalls(1, 1, 0, 0, 0)
      }

      "updating AttemptingRegistration overseas application status fails with 409" in {
        requestIsAuthenticatedWithNoEnrolments()
        givenValidApplication("accepted")
        givenUpdateApplicationStatus(AttemptingRegistration, 409)

        val result = doSubscriptionRequest

        result.status shouldBe 500

        verifyApiCalls(1, 0, 0, 0, 0)
      }

      "updating Registered overseas application status fails with 409" in {
        requestIsAuthenticatedWithNoEnrolments()
        givenValidApplication("accepted")
        givenUpdateApplicationStatus(AttemptingRegistration, 204)
        organisationRegistrationSucceeds()
        givenUpdateApplicationStatus(
          Registered,
          409,
          safeIdJson
        )

        val result = doSubscriptionRequest

        result.status shouldBe 500

        verifyApiCalls(1, 1, 1, 0, 0)
      }

      "subscribe to etmp fails" in {
        requestIsAuthenticatedWithNoEnrolments()
        givenValidApplication("accepted")
        givenUpdateApplicationStatus(AttemptingRegistration, 204)
        organisationRegistrationSucceeds()
        givenUpdateApplicationStatus(
          Registered,
          204,
          safeIdJson
        )
        subscriptionAlreadyExists(safeId.value, agencyDetailsJson)

        val result = doSubscriptionRequest

        result.status shouldBe 500

        verifyApiCalls(1, 1, 1, 1, 0)
      }

      "query via EACD for the ARN already being allocated fails" in {
        requestIsAuthenticatedWithNoEnrolments()
        givenValidApplication("accepted")
        givenUpdateApplicationStatus(AttemptingRegistration, 204)
        organisationRegistrationSucceeds()
        givenUpdateApplicationStatus(
          Registered,
          204,
          safeIdJson
        )
        subscriptionSucceeds(safeId.value, agencyDetailsJson)
        allocatedPrincipalEnrolmentFails(arn)

        val result = doSubscriptionRequest

        result.status shouldBe 500

        verifyApiCalls(
          attemptingRegistration = 1,
          etmpRegistration = 1,
          registered = 1,
          subscription = 1,
          allocatedPrincipalEnrolment = eacdRetryCount
        )
      }

      "delete known facts via EACD fails" in {
        requestIsAuthenticatedWithNoEnrolments()
        givenValidApplication("accepted")
        givenUpdateApplicationStatus(AttemptingRegistration, 204)
        organisationRegistrationSucceeds()
        givenUpdateApplicationStatus(
          Registered,
          204,
          safeIdJson
        )
        subscriptionSucceeds(safeId.value, agencyDetailsJson)
        allocatedPrincipalEnrolmentNotExists(arn)
        deleteKnownFactsFails(arn)

        val result = doSubscriptionRequest

        result.status shouldBe 500

        verifyApiCalls(
          attemptingRegistration = 1,
          etmpRegistration = 1,
          registered = 1,
          subscription = 1,
          allocatedPrincipalEnrolment = eacdRetryCount,
          deleteKnownFact = eacdRetryCount
        )
      }

      "create known facts via EACD fails" in {
        requestIsAuthenticatedWithNoEnrolments()
        givenValidApplication("accepted")
        givenUpdateApplicationStatus(AttemptingRegistration, 204)
        organisationRegistrationSucceeds()
        givenUpdateApplicationStatus(
          Registered,
          204,
          safeIdJson
        )
        subscriptionSucceeds(safeId.value, agencyDetailsJson)
        allocatedPrincipalEnrolmentNotExists(arn)
        deleteKnownFactsSucceeds(arn)
        createKnownFactsFails(arn)

        val result = doSubscriptionRequest

        result.status shouldBe 500

        verifyApiCalls(
          attemptingRegistration = 1,
          etmpRegistration = 1,
          registered = 1,
          subscription = 1,
          allocatedPrincipalEnrolment = eacdRetryCount,
          deleteKnownFact = eacdRetryCount,
          createKnownFact = eacdRetryCount
        )
      }

      "enrolment via EACD fails" in {
        requestIsAuthenticatedWithNoEnrolments()
        givenValidApplication("accepted")
        givenUpdateApplicationStatus(AttemptingRegistration, 204)
        organisationRegistrationSucceeds()
        givenUpdateApplicationStatus(
          Registered,
          204,
          safeIdJson
        )
        subscriptionSucceeds(safeId.value, agencyDetailsJson)
        allocatedPrincipalEnrolmentNotExists(arn)
        deleteKnownFactsSucceeds(arn)
        createKnownFactsSucceeds(arn)
        enrolmentFails(stubbedGroupId, arn)

        val result = doSubscriptionRequest

        result.status shouldBe 500

        verifyApiCalls(
          attemptingRegistration = 1,
          etmpRegistration = 1,
          registered = 1,
          subscription = 1,
          allocatedPrincipalEnrolment = eacdRetryCount,
          deleteKnownFact = eacdRetryCount,
          createKnownFact = eacdRetryCount,
          enrol = eacdRetryCount
        )
      }

      "creating amls record fails with 500" in {
        requestIsAuthenticatedWithNoEnrolments()
        givenValidApplication("accepted")
        givenUpdateApplicationStatus(AttemptingRegistration, 204)
        organisationRegistrationSucceeds()
        givenUpdateApplicationStatus(
          Registered,
          204,
          safeIdJson
        )
        subscriptionSucceeds(safeId.value, agencyDetailsJson)
        allocatedPrincipalEnrolmentNotExists(arn)
        deleteKnownFactsSucceeds(arn)
        createKnownFactsSucceeds(arn)
        enrolmentSucceeds(stubbedGroupId, arn)
        createOverseasAmlsFailsWithStatus(500)

        val result = doSubscriptionRequest

        result.status shouldBe 500

        verifyApiCalls(
          attemptingRegistration = 1,
          etmpRegistration = 1,
          registered = 1,
          subscription = 1,
          allocatedPrincipalEnrolment = 1,
          deleteKnownFact = 1,
          createKnownFact = 1,
          enrol = 1,
          amls = 1,
          complete = 0
        )
      }

      "updating Complete overseas application status fails with 409" in {
        requestIsAuthenticatedWithNoEnrolments()
        givenValidApplication("accepted")
        givenUpdateApplicationStatus(AttemptingRegistration, 204)
        organisationRegistrationSucceeds()
        givenUpdateApplicationStatus(
          Registered,
          204,
          safeIdJson
        )
        subscriptionSucceeds(safeId.value, agencyDetailsJson)
        allocatedPrincipalEnrolmentNotExists(arn)
        deleteKnownFactsSucceeds(arn)
        createKnownFactsSucceeds(arn)
        enrolmentSucceeds(stubbedGroupId, arn)
        createOverseasAmlsSucceeds(Arn(arn), amlsDetails)
        givenUpdateApplicationStatus(Complete, 409)

        val result = doSubscriptionRequest

        result.status shouldBe 500

        verifyApiCalls(
          attemptingRegistration = 1,
          etmpRegistration = 1,
          registered = 1,
          subscription = 1,
          allocatedPrincipalEnrolment = 1,
          deleteKnownFact = 1,
          createKnownFact = 1,
          enrol = 1,
          amls = 1,
          complete = 1
        )
      }
    }
  }

  private def doSubscriptionRequest = new Resource(s"/agent-subscription/overseas-subscription", port).putAsJson("")

  private val agencyDetailsJson =
    s"""
       |{
       |  "agencyName": "Agency name",
       |  "agencyEmail": "agencyemail@domain.com",
       |  "agencyAddress": {
       |    "addressLine1": "Mandatory Address Line 1",
       |    "addressLine2": "Mandatory Address Line 2",
       |    "countryCode": "IE"
       |  }
       |}
     """.stripMargin

  private def verifyApiCalls(
    attemptingRegistration: Int,
    etmpRegistration: Int,
    registered: Int,
    subscription: Int,
    allocatedPrincipalEnrolment: Int,
    deleteKnownFact: Int = 0,
    createKnownFact: Int = 0,
    enrol: Int = 0,
    amls: Int = 0,
    complete: Int = 0
  ) = {
    verify(1, getRequestedFor(urlEqualTo(getApplicationUrl.toString)))
    verify(
      attemptingRegistration,
      putRequestedFor(urlEqualTo(s"/agent-overseas-application/application/attempting_registration"))
    )
    verify(etmpRegistration, postRequestedFor(urlEqualTo(s"/registration/02.00.00/organisation")))
    verify(registered, putRequestedFor(urlEqualTo(s"/agent-overseas-application/application/registered")))
    verify(subscription, postRequestedFor(urlEqualTo(s"/registration/agents/safeId/${safeId.value}")))
    verifyAllocatedPrincipalEnrolmentCalled(allocatedPrincipalEnrolment)
    verifyDeleteKnownFactsCalled(deleteKnownFact)
    verifyCreateKnownFactsCalled(createKnownFact)
    verifyEnrolmentCalled(enrol)
    verifyCreateOverseasAmlsCall(amls)
    verify(complete, putRequestedFor(urlEqualTo(s"/agent-overseas-application/application/complete")))
  }

}
