package uk.gov.hmrc.agentsubscription.controllers

import com.github.tomakehurst.wiremock.client.WireMock.{ verify, _ }
import play.api.libs.json.Json.stringify
import play.api.libs.json._
import play.api.libs.ws.WSClient
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

  "creating a subscription" should {
    val address = __ \ "agencyAddress"
    val invalidAddress = "Invalid road %@"

    "return a response containing the ARN" when {
      "all fields are populated" in {
        behave like aSuccessfulRegSubscribeAndEnrol(subscriptionRequestJson)
      }

      "addressLine3 and addressLine4 are missing" in {
        val fields = Seq(address \ "addressLine3", address \ "addressLine4")

        behave like aSuccessfulRegSubscribeAndEnrol(removeFields(fields))
      }

      def aSuccessfulRegSubscribeAndEnrol(subscriptionRequestJson: String) = {
        requestIsAuthenticated().andIsAnAgent().andHasNoEnrolments()
        givenValidApplication("accepted")
        givenUpdateApplicationStatus(AttemptingRegistration, 204)
        organisationRegistrationSucceeds()
        givenUpdateApplicationStatus(Registered, 204, safeIdJson)
        subscriptionSucceeds(safeId.value, subscriptionRequestJson)
        allocatedPrincipalEnrolmentNotExists(arn)
        deleteKnownFactsSucceeds(arn)
        createKnownFactsSucceeds(arn)
        enrolmentSucceeds(stubbedGroupId, arn)
        givenUpdateApplicationStatus(Complete, 204)

        val result = await(doSubscriptionRequest(subscriptionRequestJson))

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
          complete = 1)
      }
    }

    "return Conflict if there is an existing HMRC-AS-AGENT enrolment for their Arn already allocated to some group" in {
      requestIsAuthenticated().andIsAnAgent().andHasNoEnrolments()
      givenValidApplication("registered", "XE0001234567890")
      subscriptionSucceeds(safeId.value, subscriptionRequestJson)
      allocatedPrincipalEnrolmentExists(arn, "someOtherGroupId")

      val result = await(doSubscriptionRequest(subscriptionRequestJson))

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
        complete = 0)

    }

    "return Forbidden" when {
      "current application status is attempting_registration" in {
        requestIsAuthenticated().andIsAnAgent().andHasNoEnrolments()
        givenValidApplication("attempting_registration")

        val result = await(doSubscriptionRequest(subscriptionRequestJson))

        result.status shouldBe 403
        verifyApiCalls(0, 0, 0, 0, 0)
      }

      "the user does not have Agent affinity" in {
        requestIsAuthenticatedWithNoEnrolments(affinityGroup = "Individual").andIsAnNotAgent().andHasNoEnrolments()
        await(doSubscriptionRequest(subscriptionRequestJson)).status shouldBe 403

        verify(0, getRequestedFor(urlEqualTo(getApplicationUrl)))
      }

      "the user already has enrolments" in {
        pending // TODO when working on the retries/re-attempts story

        requestIsAuthenticated().andIsAnAgent().andHasEnrolments()
        givenValidApplication("registered", "XE0001234567890")
        subscriptionSucceeds(safeId.value, subscriptionRequestJson)
        allocatedPrincipalEnrolmentNotExists(arn)

        await(doSubscriptionRequest(subscriptionRequestJson)).status shouldBe 403

        verifyApiCalls(
          attemptingRegistration = 0,
          etmpRegistration = 0,
          registered = 0,
          subscription = 1,
          allocatedPrincipalEnrolment = 1,
          deleteKnownFact = 0,
          createKnownFact = 0,
          enrol = 0,
          complete = 0)
      }

      "do not call DES registration API when the current status is registered" in {
        requestIsAuthenticated().andIsAnAgent().andHasNoEnrolments()
        givenValidApplication("registered", "XE0001234567890")
        subscriptionSucceeds(safeId.value, subscriptionRequestJson)
        allocatedPrincipalEnrolmentNotExists(arn)
        deleteKnownFactsSucceeds(arn)
        createKnownFactsSucceeds(arn)
        enrolmentSucceeds(stubbedGroupId, arn)
        givenUpdateApplicationStatus(Complete, 204)

        val result = await(doSubscriptionRequest(subscriptionRequestJson))

        result.status shouldBe 201
        (result.json \ "arn").as[String] shouldBe "TARN0000001"

        verifyApiCalls(
          attemptingRegistration = 0,
          etmpRegistration = 0,
          registered = 0,
          subscription = 1,
          allocatedPrincipalEnrolment = 1,
          deleteKnownFact = 1,
          createKnownFact = 1,
          enrol = 1,
          complete = 1)
      }
    }

    "return Bad Request " when {

      "name contains invalid characters" in {
        requestIsAuthenticated()
        val result = await(doSubscriptionRequest(replaceFields(Seq((__, "agencyName", "InvalidAgencyName!@")))))

        result.status shouldBe 400
      }

      "address is missing" in {
        requestIsAuthenticated()
        val result = await(doSubscriptionRequest(removeFields(Seq(address))))

        result.status shouldBe 400
      }

      "address line 1 contains invalid characters" in {
        requestIsAuthenticated()
        val result = await(doSubscriptionRequest(replaceFields(Seq((address, "addressLine1", invalidAddress)))))

        result.status shouldBe 400
      }

      "address line 2 contains invalid characters" in {
        requestIsAuthenticated()
        val result = await(doSubscriptionRequest(replaceFields(Seq((address, "addressLine2", invalidAddress)))))
        result.status shouldBe 400
      }

      "address line 3 contains invalid characters" in {
        requestIsAuthenticated()
        val result = await(doSubscriptionRequest(replaceFields(Seq((address, "addressLine3", invalidAddress)))))
        result.status shouldBe 400
      }

      "address line 4 contains invalid characters" in {
        requestIsAuthenticated()
        val result = await(doSubscriptionRequest(replaceFields(Seq((address, "addressLine4", invalidAddress)))))
        result.status shouldBe 400
      }

      "email is missing" in {
        requestIsAuthenticated()
        val result = await(doSubscriptionRequest(removeFields(Seq(__ \ "agencyEmail"))))

        result.status shouldBe 400
      }
      "email has no local part" in {
        requestIsAuthenticated()
        val result = await(doSubscriptionRequest(replaceFields(Seq((__, "agencyEmail", "@domain")))))

        result.status shouldBe 400
      }
      "email has no domain part" in {
        requestIsAuthenticated()
        val result = await(doSubscriptionRequest(replaceFields(Seq((__, "agencyEmail", "local@")))))

        result.status shouldBe 400
      }
      "email has no @" in {
        requestIsAuthenticated()
        val result = await(doSubscriptionRequest(replaceFields(Seq((__, "agencyEmail", "local")))))

        result.status shouldBe 400
      }

      "telephone number contains words" in {
        requestIsAuthenticated()
        val result = await(doSubscriptionRequest(replaceFields(Seq((__, "telephoneNumber", "0123 456 78aa")))))

        result.status shouldBe 400
      }

      "telephone number is provided but empty" in {
        requestIsAuthenticated()
        val result = await(doSubscriptionRequest(replaceFields(Seq((__, "telephoneNumber", "")))))

        result.status shouldBe 400
      }

      "countryCode is missing" in {
        requestIsAuthenticated()
        val result = await(doSubscriptionRequest(removeFields(Seq(address \ "countryCode"))))

        result.status shouldBe 400
      }

      "countryCode is GB" in {
        requestIsAuthenticated()
        val result = await(doSubscriptionRequest(replaceFields(Seq((address, "countryCode", "GB")))))

        result.status shouldBe 400
      }
    }

    "return a 500 error if " when {
      "etmp registration fails" in {
        requestIsAuthenticated()
        givenValidApplication("accepted")
        givenUpdateApplicationStatus(AttemptingRegistration, 204)
        organisationRegistrationFailsWithNotFound("{}")

        val result = await(doSubscriptionRequest(subscriptionRequestJson))

        result.status shouldBe 500

        verifyApiCalls(1, 1, 0, 0, 0)
      }

      "updating AttemptingRegistration overseas application status fails with 409" in {
        requestIsAuthenticated()
        givenValidApplication("accepted")
        givenUpdateApplicationStatus(AttemptingRegistration, 409)

        val result = await(doSubscriptionRequest(subscriptionRequestJson))

        result.status shouldBe 500

        verifyApiCalls(1, 0, 0, 0, 0)
      }

      "updating Registered overseas application status fails with 409" in {
        requestIsAuthenticated().andIsAnAgent().andHasNoEnrolments()
        givenValidApplication("accepted")
        givenUpdateApplicationStatus(AttemptingRegistration, 204)
        organisationRegistrationSucceeds()
        givenUpdateApplicationStatus(Registered, 409, safeIdJson)

        val result = await(doSubscriptionRequest(subscriptionRequestJson))

        result.status shouldBe 500

        verifyApiCalls(1, 1, 1, 0, 0)
      }

      "subscribe to etmp fails" in {
        requestIsAuthenticated().andIsAnAgent().andHasNoEnrolments()
        givenValidApplication("accepted")
        givenUpdateApplicationStatus(AttemptingRegistration, 204)
        organisationRegistrationSucceeds()
        givenUpdateApplicationStatus(Registered, 204, safeIdJson)
        subscriptionAlreadyExists(safeId.value, subscriptionRequestJson)

        val result = await(doSubscriptionRequest(subscriptionRequestJson))

        result.status shouldBe 500

        verifyApiCalls(1, 1, 1, 1, 0)
      }

      "query via EACD for the ARN already being allocated fails" in {
        requestIsAuthenticated().andIsAnAgent().andHasNoEnrolments()
        givenValidApplication("accepted")
        givenUpdateApplicationStatus(AttemptingRegistration, 204)
        organisationRegistrationSucceeds()
        givenUpdateApplicationStatus(Registered, 204, safeIdJson)
        subscriptionSucceeds(safeId.value, subscriptionRequestJson)
        allocatedPrincipalEnrolmentFails(arn)

        val result = await(doSubscriptionRequest(subscriptionRequestJson))

        result.status shouldBe 500

        verifyApiCalls(
          attemptingRegistration = 1,
          etmpRegistration = 1,
          registered = 1,
          subscription = 1,
          allocatedPrincipalEnrolment = eacdRetryCount)
      }

      "delete known facts via EACD fails" in {
        requestIsAuthenticated().andIsAnAgent().andHasNoEnrolments()
        givenValidApplication("accepted")
        givenUpdateApplicationStatus(AttemptingRegistration, 204)
        organisationRegistrationSucceeds()
        givenUpdateApplicationStatus(Registered, 204, safeIdJson)
        subscriptionSucceeds(safeId.value, subscriptionRequestJson)
        allocatedPrincipalEnrolmentNotExists(arn)
        deleteKnownFactsFails(arn)

        val result = await(doSubscriptionRequest(subscriptionRequestJson))

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
        requestIsAuthenticated().andIsAnAgent().andHasNoEnrolments()
        givenValidApplication("accepted")
        givenUpdateApplicationStatus(AttemptingRegistration, 204)
        organisationRegistrationSucceeds()
        givenUpdateApplicationStatus(Registered, 204, safeIdJson)
        subscriptionSucceeds(safeId.value, subscriptionRequestJson)
        allocatedPrincipalEnrolmentNotExists(arn)
        deleteKnownFactsSucceeds(arn)
        createKnownFactsFails(arn)

        val result = await(doSubscriptionRequest(subscriptionRequestJson))

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
        requestIsAuthenticated().andIsAnAgent().andHasNoEnrolments()
        givenValidApplication("accepted")
        givenUpdateApplicationStatus(AttemptingRegistration, 204)
        organisationRegistrationSucceeds()
        givenUpdateApplicationStatus(Registered, 204, safeIdJson)
        subscriptionSucceeds(safeId.value, subscriptionRequestJson)
        allocatedPrincipalEnrolmentNotExists(arn)
        deleteKnownFactsSucceeds(arn)
        createKnownFactsSucceeds(arn)
        enrolmentFails(stubbedGroupId, arn)

        val result = await(doSubscriptionRequest(subscriptionRequestJson))

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

      "updating Complete overseas application status fails with 409" in {
        requestIsAuthenticated().andIsAnAgent().andHasNoEnrolments()
        givenValidApplication("accepted")
        givenUpdateApplicationStatus(AttemptingRegistration, 204)
        organisationRegistrationSucceeds()
        givenUpdateApplicationStatus(Registered, 204, safeIdJson)
        subscriptionSucceeds(safeId.value, subscriptionRequestJson)
        allocatedPrincipalEnrolmentNotExists(arn)
        deleteKnownFactsSucceeds(arn)
        createKnownFactsSucceeds(arn)
        enrolmentSucceeds(stubbedGroupId, arn)
        givenUpdateApplicationStatus(Complete, 409)

        val result = await(doSubscriptionRequest(subscriptionRequestJson))

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
          complete = 1)
      }
    }
  }

  private def doSubscriptionRequest(request: String) = new Resource(s"/agent-subscription/overseas-subscription", port).postAsJson(request)

  private val subscriptionRequestJson =
    s"""
       |{
       |  "agencyName": "Test Organisation Name",
       |  "agencyEmail": "test@test.example",
       |  "telephoneNumber": "00491234567890",
       |  "agencyAddress": {
       |    "addressLine1": "Mandatory Address Line 1",
       |    "addressLine2": "Mandatory Address Line 2",
       |    "addressLine3": "Optional Address Line 3",
       |    "addressLine4": "Optional Address Line 4",
       |    "countryCode": "IE"
       |  }
       |}
     """.stripMargin

  private def removeFields(fields: Seq[JsPath]): String = {
    val request = Json.parse(subscriptionRequestJson).as[JsObject]
    val filtered: JsObject = removeFields(request, fields)

    stringify(filtered)
  }

  private def removeFields(jsObject: JsObject, fields: Seq[JsPath]): JsObject = {
    val transformer = fields.map(field => field.json.prune).reduce((a, b) => a andThen b)
    jsObject.transform(transformer).get
  }

  private def replaceFields(fields: Seq[(JsPath, String, String)]): String = {
    val request = Json.parse(subscriptionRequestJson).as[JsObject]
    val filtered: JsObject = replaceFields(request, fields)

    stringify(filtered)
  }

  private def replaceFields(jsObject: JsObject, fields: Seq[(JsPath, String, String)]): JsObject = {
    val transformer = fields.map(field => field._1.json.update(
      __.read[JsObject].map(o => o ++ Json.obj(field._2 -> field._3)))).reduce((a, b) => a andThen b)
    jsObject.transform(transformer) match {
      case s: JsSuccess[JsObject] => s.get
      case e: JsError =>
        println(e)
        throw new RuntimeException(s"Unable to transform JSON: $e")
    }
  }

  private def verifyApiCalls(
    attemptingRegistration: Int = 0,
    etmpRegistration: Int = 0,
    registered: Int = 0,
    subscription: Int = 0,
    allocatedPrincipalEnrolment: Int = 0,
    deleteKnownFact: Int = 0,
    createKnownFact: Int = 0,
    enrol: Int = 0,
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
    verify(complete, putRequestedFor(urlEqualTo(s"/agent-overseas-application/application/complete")))
  }

}
