package uk.gov.hmrc.agentsubscription.controllers

import com.github.tomakehurst.wiremock.client.WireMock.{ verify, _ }
import play.api.libs.ws.WSClient
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentsubscription.model.ApplicationStatus.{ AttemptingRegistration, Complete, Registered }
import uk.gov.hmrc.agentsubscription.model._
import uk.gov.hmrc.agentsubscription.stubs._
import uk.gov.hmrc.agentsubscription.support.{ BaseISpec, Resource }

class SubscriptionControllerForOverseasISpec extends BaseISpec with OverseasDesStubs with AuthStub with AgentOverseasApplicationStubs with AgentAssuranceStub with TaxEnrolmentsStubs {
  private val arn = "TARN0000001"
  private val stubbedGroupId = "groupId"
  private val safeId = SafeId("XE0001234567890")
  implicit val ws = app.injector.instanceOf[WSClient]
  private val safeIdJson = s"""{ "safeId": "${safeId.value}"}"""
  private val eacdRetryCount = 3
  private val amlsDetails = OverseasAmlsDetails("supervisoryName", Some("supervisoryId"))

  "creating a subscription" should {
    "return a successful response containing the ARN" when {
      "all fields are populated" in {
        requestIsAuthenticatedWithNoEnrolments()
        givenValidApplication("accepted")
        givenUpdateApplicationStatus(AttemptingRegistration, 204)
        organisationRegistrationSucceeds()
        givenUpdateApplicationStatus(Registered, 204, safeIdJson)
        subscriptionSucceeds(safeId.value, agencyDetailsJson)
        allocatedPrincipalEnrolmentNotExists(arn)
        deleteKnownFactsSucceeds(arn)
        createKnownFactsSucceeds(arn)
        enrolmentSucceeds(stubbedGroupId, arn)
        createOverseasAmlsSucceeds(Arn(arn), amlsDetails)
        givenUpdateApplicationStatus(Complete, 204)

        val result = await(doSubscriptionRequest)

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
          complete = 1)
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
        givenUpdateApplicationStatus(Complete, 204)

        val result = await(doSubscriptionRequest)

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
          complete = 1)
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
        givenUpdateApplicationStatus(Complete, 204)

        val result = await(doSubscriptionRequest)

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
          complete = 1)
      }
    }

    "return Conflict if there is an existing HMRC-AS-AGENT enrolment for their Arn already allocated to some group" in {
      requestIsAuthenticatedWithNoEnrolments()
      givenValidApplication("registered", Some(safeId.value))
      subscriptionSucceeds(safeId.value, agencyDetailsJson)
      allocatedPrincipalEnrolmentExists(arn, "someOtherGroupId")

      val result = await(doSubscriptionRequest)

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
        complete = 0)

    }

    "return Forbidden" when {
      "current application status is attempting_registration" in {
        requestIsAuthenticatedWithNoEnrolments()
        givenValidApplication("attempting_registration")

        val result = await(doSubscriptionRequest)

        result.status shouldBe 403
        verifyApiCalls(0, 0, 0, 0, 0)
      }

      "the user does not have Agent affinity" in {
        requestIsAuthenticatedWithNoEnrolments(affinityGroup = "Individual")
        await(doSubscriptionRequest).status shouldBe 403

        verify(0, getRequestedFor(urlEqualTo(getApplicationUrl)))
      }
    }

    "return a 500 error if " when {
      "the current application was subjected to validation and was found to have an invalid value (for example, an Agency Name that contains a disallowed character)" in {
        requestIsAuthenticatedWithNoEnrolments()

        val invalidAgencyName = "Acme & Sons" // Ampersands are not allowed for the agency name
        givenValidApplication("accepted", agencyName = invalidAgencyName)

        val result = await(doSubscriptionRequest)

        result.status shouldBe 500
        (result.json \ "statusCode").as[Int] shouldBe 500
        (result.json \ "message").as[String] shouldBe "The retrieved current application is invalid"

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
          complete = 0)
      }

      "etmp registration fails" in {
        requestIsAuthenticatedWithNoEnrolments()
        givenValidApplication("accepted")
        givenUpdateApplicationStatus(AttemptingRegistration, 204)
        organisationRegistrationFailsWithNotFound()

        val result = await(doSubscriptionRequest)

        result.status shouldBe 500

        verifyApiCalls(1, 1, 0, 0, 0)
      }

      "updating AttemptingRegistration overseas application status fails with 409" in {
        requestIsAuthenticatedWithNoEnrolments()
        givenValidApplication("accepted")
        givenUpdateApplicationStatus(AttemptingRegistration, 409)

        val result = await(doSubscriptionRequest)

        result.status shouldBe 500

        verifyApiCalls(1, 0, 0, 0, 0)
      }

      "updating Registered overseas application status fails with 409" in {
        requestIsAuthenticatedWithNoEnrolments()
        givenValidApplication("accepted")
        givenUpdateApplicationStatus(AttemptingRegistration, 204)
        organisationRegistrationSucceeds()
        givenUpdateApplicationStatus(Registered, 409, safeIdJson)

        val result = await(doSubscriptionRequest)

        result.status shouldBe 500

        verifyApiCalls(1, 1, 1, 0, 0)
      }

      "subscribe to etmp fails" in {
        requestIsAuthenticatedWithNoEnrolments()
        givenValidApplication("accepted")
        givenUpdateApplicationStatus(AttemptingRegistration, 204)
        organisationRegistrationSucceeds()
        givenUpdateApplicationStatus(Registered, 204, safeIdJson)
        subscriptionAlreadyExists(safeId.value, agencyDetailsJson)

        val result = await(doSubscriptionRequest)

        result.status shouldBe 500

        verifyApiCalls(1, 1, 1, 1, 0)
      }

      "query via EACD for the ARN already being allocated fails" in {
        requestIsAuthenticatedWithNoEnrolments()
        givenValidApplication("accepted")
        givenUpdateApplicationStatus(AttemptingRegistration, 204)
        organisationRegistrationSucceeds()
        givenUpdateApplicationStatus(Registered, 204, safeIdJson)
        subscriptionSucceeds(safeId.value, agencyDetailsJson)
        allocatedPrincipalEnrolmentFails(arn)

        val result = await(doSubscriptionRequest)

        result.status shouldBe 500

        verifyApiCalls(
          attemptingRegistration = 1,
          etmpRegistration = 1,
          registered = 1,
          subscription = 1,
          allocatedPrincipalEnrolment = eacdRetryCount)
      }

      "delete known facts via EACD fails" in {
        requestIsAuthenticatedWithNoEnrolments()
        givenValidApplication("accepted")
        givenUpdateApplicationStatus(AttemptingRegistration, 204)
        organisationRegistrationSucceeds()
        givenUpdateApplicationStatus(Registered, 204, safeIdJson)
        subscriptionSucceeds(safeId.value, agencyDetailsJson)
        allocatedPrincipalEnrolmentNotExists(arn)
        deleteKnownFactsFails(arn)

        val result = await(doSubscriptionRequest)

        result.status shouldBe 500

        verifyApiCalls(
          attemptingRegistration = 1,
          etmpRegistration = 1,
          registered = 1,
          subscription = 1,
          allocatedPrincipalEnrolment = eacdRetryCount,
          deleteKnownFact = eacdRetryCount)
      }

      "create known facts via EACD fails" in {
        requestIsAuthenticatedWithNoEnrolments()
        givenValidApplication("accepted")
        givenUpdateApplicationStatus(AttemptingRegistration, 204)
        organisationRegistrationSucceeds()
        givenUpdateApplicationStatus(Registered, 204, safeIdJson)
        subscriptionSucceeds(safeId.value, agencyDetailsJson)
        allocatedPrincipalEnrolmentNotExists(arn)
        deleteKnownFactsSucceeds(arn)
        createKnownFactsFails(arn)

        val result = await(doSubscriptionRequest)

        result.status shouldBe 500

        verifyApiCalls(
          attemptingRegistration = 1,
          etmpRegistration = 1,
          registered = 1,
          subscription = 1,
          allocatedPrincipalEnrolment = eacdRetryCount,
          deleteKnownFact = eacdRetryCount,
          createKnownFact = eacdRetryCount)
      }

      "enrolment via EACD fails" in {
        requestIsAuthenticatedWithNoEnrolments()
        givenValidApplication("accepted")
        givenUpdateApplicationStatus(AttemptingRegistration, 204)
        organisationRegistrationSucceeds()
        givenUpdateApplicationStatus(Registered, 204, safeIdJson)
        subscriptionSucceeds(safeId.value, agencyDetailsJson)
        allocatedPrincipalEnrolmentNotExists(arn)
        deleteKnownFactsSucceeds(arn)
        createKnownFactsSucceeds(arn)
        enrolmentFails(stubbedGroupId, arn)

        val result = await(doSubscriptionRequest)

        result.status shouldBe 500

        verifyApiCalls(
          attemptingRegistration = 1,
          etmpRegistration = 1,
          registered = 1,
          subscription = 1,
          allocatedPrincipalEnrolment = eacdRetryCount,
          deleteKnownFact = eacdRetryCount,
          createKnownFact = eacdRetryCount,
          enrol = eacdRetryCount)
      }

      "creating amls record fails with 500" in {
        requestIsAuthenticatedWithNoEnrolments()
        givenValidApplication("accepted")
        givenUpdateApplicationStatus(AttemptingRegistration, 204)
        organisationRegistrationSucceeds()
        givenUpdateApplicationStatus(Registered, 204, safeIdJson)
        subscriptionSucceeds(safeId.value, agencyDetailsJson)
        allocatedPrincipalEnrolmentNotExists(arn)
        deleteKnownFactsSucceeds(arn)
        createKnownFactsSucceeds(arn)
        enrolmentSucceeds(stubbedGroupId, arn)
        createOverseasAmlsFailsWithStatus(500)

        val result = await(doSubscriptionRequest)

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
          complete = 0)
      }

      "updating Complete overseas application status fails with 409" in {
        requestIsAuthenticatedWithNoEnrolments()
        givenValidApplication("accepted")
        givenUpdateApplicationStatus(AttemptingRegistration, 204)
        organisationRegistrationSucceeds()
        givenUpdateApplicationStatus(Registered, 204, safeIdJson)
        subscriptionSucceeds(safeId.value, agencyDetailsJson)
        allocatedPrincipalEnrolmentNotExists(arn)
        deleteKnownFactsSucceeds(arn)
        createKnownFactsSucceeds(arn)
        enrolmentSucceeds(stubbedGroupId, arn)
        createOverseasAmlsSucceeds(Arn(arn), amlsDetails)
        givenUpdateApplicationStatus(Complete, 409)

        val result = await(doSubscriptionRequest)

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
          complete = 1)
      }
    }
  }

  private def doSubscriptionRequest = new Resource(s"/agent-subscription/overseas-subscription", port).putEmpty()

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
    attemptingRegistration: Int = 0,
    etmpRegistration: Int = 0,
    registered: Int = 0,
    subscription: Int = 0,
    allocatedPrincipalEnrolment: Int = 0,
    deleteKnownFact: Int = 0,
    createKnownFact: Int = 0,
    enrol: Int = 0,
    amls: Int = 0,
    complete: Int = 0) = {
    verify(1, getRequestedFor(urlEqualTo(getApplicationUrl)))
    verify(attemptingRegistration, putRequestedFor(urlEqualTo(s"/agent-overseas-application/application/attempting_registration")))
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
