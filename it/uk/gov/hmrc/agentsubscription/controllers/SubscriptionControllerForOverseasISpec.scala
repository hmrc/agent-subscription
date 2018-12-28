package uk.gov.hmrc.agentsubscription.controllers

import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.libs.json.Json.stringify
import play.api.libs.json._
import play.api.libs.ws.WSClient
import uk.gov.hmrc.agentsubscription.model.ApplicationStatus.{AttemptingRegistration, Complete, Registered}
import uk.gov.hmrc.agentsubscription.model._
import uk.gov.hmrc.agentsubscription.stubs._
import uk.gov.hmrc.agentsubscription.support.{BaseISpec, Resource}

class SubscriptionControllerForOverseasISpec extends BaseISpec with OverseasDesStubs with AuthStub with AgentOverseasApplicationStubs with AgentAssuranceStub {
  private val arn = "TARN0000001"
  private val safeId = SafeId("XE0001234567890")
  implicit val ws = app.injector.instanceOf[WSClient]

  "creating a subscription" should {
    val address = __ \ "agencyAddress"
    val invalidAddress = "Invalid road %@"

    "return a response containing the ARN" when {
      "all fields are populated" in {
        requestIsAuthenticated().andIsAnAgent().andHasNoEnrolments()
        givenGetUpdateApplicationStatus(AttemptingRegistration, 204)
        organisationRegistrationSucceeds
        givenGetUpdateApplicationStatus(Registered, 204)
        subscriptionSucceeds(safeId.value, subscriptionRequestJson)
        givenGetUpdateApplicationStatus(Complete, 204)

        val result = await(doSubscriptionRequest(subscriptionRequestJson))

        result.status shouldBe 201
        (result.json \ "arn").as[String] shouldBe "TARN0000001"

        verify(1, getRequestedFor(urlEqualTo(s"/application/attempting_registration")))
        verify(1, postRequestedFor(urlEqualTo(s"/registration/02.00.00/organisation")))
        verify(1, getRequestedFor(urlEqualTo(s"/application/registered")))
        verify(1, postRequestedFor(urlEqualTo(s"/registration/agents/safeId/${safeId.value}")))
        verify(1, getRequestedFor(urlEqualTo(s"/application/complete")))
      }

       "addressLine3 and addressLine4 are missing" in {
        requestIsAuthenticated().andIsAnAgent().andHasNoEnrolments()
        val fields = Seq(address \ "addressLine3", address \ "addressLine4")
         givenGetUpdateApplicationStatus(AttemptingRegistration, 204)
         organisationRegistrationSucceeds
         givenGetUpdateApplicationStatus(Registered, 204)
         subscriptionSucceeds(safeId.value, removeFields(fields))
         givenGetUpdateApplicationStatus(Complete, 204)

        val result = await(doSubscriptionRequest(removeFields(fields)))

        result.status shouldBe 201
        (result.json \ "arn").as[String] shouldBe "TARN0000001"

         verify(1, getRequestedFor(urlEqualTo(s"/application/attempting_registration")))
         verify(1, postRequestedFor(urlEqualTo(s"/registration/02.00.00/organisation")))
         verify(1, getRequestedFor(urlEqualTo(s"/application/registered")))
         verify(1, postRequestedFor(urlEqualTo(s"/registration/agents/safeId/${safeId.value}")))
         verify(1, getRequestedFor(urlEqualTo(s"/application/complete")))
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

    "throw a 500 error if " when {
      "DES API #1173 Subscribe to Agent Services fails" in {
        requestIsAuthenticated()
        givenGetUpdateApplicationStatus(AttemptingRegistration, 204)
        organisationRegistrationFailsWithNotFound("{}")

        val result = await(doSubscriptionRequest(subscriptionRequestJson))

        result.status shouldBe 500

        verify(1, getRequestedFor(urlEqualTo(s"/application/attempting_registration")))
        verify(1, postRequestedFor(urlEqualTo(s"/registration/02.00.00/organisation")))
        verify(0, getRequestedFor(urlEqualTo(s"/application/registered")))
        verify(0, postRequestedFor(urlEqualTo(s"/registration/agents/safeId/${safeId.value}")))
        verify(0, getRequestedFor(urlEqualTo(s"/application/complete")))
      }

      "updating AttemptingRegistration overseas application status fails with 409" in {
        requestIsAuthenticated()
        givenGetUpdateApplicationStatus(AttemptingRegistration, 409)

        val result = await(doSubscriptionRequest(subscriptionRequestJson))

        result.status shouldBe 500

        verify(1, getRequestedFor(urlEqualTo(s"/application/attempting_registration")))
        verify(0, postRequestedFor(urlEqualTo(s"/registration/02.00.00/organisation")))
        verify(0, getRequestedFor(urlEqualTo(s"/application/registered")))
        verify(0, postRequestedFor(urlEqualTo(s"/registration/agents/safeId/${safeId.value}")))
        verify(0, getRequestedFor(urlEqualTo(s"/application/complete")))
      }

      "updating Registered overseas application status fails with 409" in {
        requestIsAuthenticated().andIsAnAgent().andHasNoEnrolments()
        givenGetUpdateApplicationStatus(AttemptingRegistration, 204)
        organisationRegistrationSucceeds
        givenGetUpdateApplicationStatus(Registered, 409)

        val result = await(doSubscriptionRequest(subscriptionRequestJson))

        result.status shouldBe 500

        verify(1, getRequestedFor(urlEqualTo(s"/application/attempting_registration")))
        verify(1, postRequestedFor(urlEqualTo(s"/registration/02.00.00/organisation")))
        verify(1, getRequestedFor(urlEqualTo(s"/application/registered")))
        verify(0, postRequestedFor(urlEqualTo(s"/registration/agents/safeId/${safeId.value}")))
        verify(0, getRequestedFor(urlEqualTo(s"/application/complete")))
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

}
