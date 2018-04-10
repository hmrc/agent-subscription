package uk.gov.hmrc.agentsubscription.controllers

import play.api.libs.json.Json.{ stringify, toJson }
import play.api.libs.json._
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscription.model.{ KnownFacts, SubscriptionRequest }
import uk.gov.hmrc.agentsubscription.stubs.{ AuthStub, DesStubs, TaxEnrolmentsStubs }
import uk.gov.hmrc.agentsubscription.support.{ BaseISpec, Resource }
import com.github.tomakehurst.wiremock.client.WireMock._

class SubscriptionControllerISpec extends BaseISpec with DesStubs with AuthStub with TaxEnrolmentsStubs {
  private val utr = Utr("7000000002")

  val arn = "ARN0001"
  val groupId = "groupId"

  "creating a subscription" should {
    val agency = __ \ "agency"
    val address = agency \ "address"
    val invalidAddress = "Invalid road %@"

    "return a response containing the ARN" when {
      "all fields are populated" in {
        requestIsAuthenticated().andIsAnAgent().andHasNoEnrolments()
        organisationRegistrationExists(utr, isAnASAgent = false, arn = arn)
        subscriptionSucceeds(utr, Json.parse(subscriptionRequest).as[SubscriptionRequest])
        allocatedPrincipalEnrolmentNotExists(arn)
        deleteKnownFactsSucceeds(arn)
        createKnownFactsSucceeds(arn)
        enrolmentSucceeds(groupId, arn)

        val result = await(doSubscriptionRequest())

        result.status shouldBe 201
        (result.json \ "arn").as[String] shouldBe "ARN0001"

        verify(1, postRequestedFor(urlEqualTo(s"/registration/agents/utr/${utr.value}")))
        verify(1, postRequestedFor(urlEqualTo(enrolmentUrl(groupId, arn))))
      }

      "addressLine2, addressLine3 and addressLine4 are missing" in {
        requestIsAuthenticated().andIsAnAgent().andHasNoEnrolments()
        val fields = Seq(address \ "addressLine2", address \ "addressLine3", address \ "addressLine4")
        organisationRegistrationExists(utr, isAnASAgent = false, arn = arn)
        subscriptionSucceeds(utr, Json.parse(removeFields(fields)).as[SubscriptionRequest])
        allocatedPrincipalEnrolmentNotExists(arn)
        deleteKnownFactsSucceeds(arn)
        createKnownFactsSucceeds(arn)
        enrolmentSucceeds(groupId, arn)

        val result = await(doSubscriptionRequest(removeFields(fields)))

        result.status shouldBe 201
        (result.json \ "arn").as[String] shouldBe "ARN0001"

        verify(1, postRequestedFor(urlEqualTo(s"/registration/agents/utr/${utr.value}")))
        verify(1, postRequestedFor(urlEqualTo(enrolmentUrl(groupId, arn))))
      }

      "BPR has isAnAsAgent=true and there is no previous allocation for HMRC-AS-AGENT for the arn" in {
        requestIsAuthenticated().andIsAnAgent().andHasNoEnrolments()
        organisationRegistrationExists(utr, isAnASAgent = true, arn = arn)
        deleteKnownFactsSucceeds(arn)
        createKnownFactsSucceeds(arn)
        allocatedPrincipalEnrolmentNotExists(arn)
        enrolmentSucceeds(groupId, arn)

        val result = await(doSubscriptionRequest())

        result.status shouldBe 201
        (result.json \ "arn").as[String] shouldBe "ARN0001"

        verify(0, postRequestedFor(urlEqualTo(s"/registration/agents/utr/${utr.value}")))
        verify(1, postRequestedFor(urlEqualTo(enrolmentUrl(groupId, arn))))
      }
    }

    "return Conflict if already subscribed (both ETMP has isAsAgent=true and there is an existing HMRC-AS-AGENT enrolment for their Arn)" in {
      requestIsAuthenticated().andIsAnAgent().andHasNoEnrolments()
      organisationRegistrationExists(utr, isAnASAgent = true, arn = arn)
      allocatedPrincipalEnrolmentExists(arn, "someGroupId")

      val result = await(doSubscriptionRequest())

      result.status shouldBe 409

      verify(0, deleteRequestedFor(urlEqualTo(s"$deleteKnownFactsUrl$arn")))
      verify(0, putRequestedFor(urlEqualTo(s"$createKnownFactsUrl$arn")))
      verify(0, postRequestedFor(urlEqualTo(enrolmentUrl("groupId", arn))))
    }

    "return forbidden" when {
      "no registration exists" in {
        requestIsAuthenticated().andIsAnAgent().andHasNoEnrolments()
        registrationDoesNotExist(utr)

        val result = await(doSubscriptionRequest())

        result.status shouldBe 403
      }

      "postcodes don't match" in {
        requestIsAuthenticated().andIsAnAgent().andHasNoEnrolments()
        organisationRegistrationExists(utr)
        val request = Json.parse(subscriptionRequest).as[SubscriptionRequest].copy(knownFacts = KnownFacts("AA1 2AA"))

        val result = await(doSubscriptionRequest(stringify(toJson(request))))

        result.status shouldBe 403
      }

      "the user already has enrolments" in {
        requestIsAuthenticated().andIsAnAgent().andHasEnrolments()
        val result = await(doSubscriptionRequest())

        result.status shouldBe 403
      }
    }

    "return Bad Request " when {
      "utr is missing" in {
        requestIsAuthenticated()
        val result = await(doSubscriptionRequest(removeFields(Seq(__ \ "utr"))))

        result.status shouldBe 400
      }

      "utr contains non-numeric characters" in {
        requestIsAuthenticated()
        val result = await(doSubscriptionRequest(replaceFields(Seq((__, "utr", "ABCDE12345")))))

        result.status shouldBe 400
      }

      "utr contains fewer than 10 digits" in {
        requestIsAuthenticated()
        val result = await(doSubscriptionRequest(replaceFields(Seq((__, "utr", "12345")))))

        result.status shouldBe 400
      }

      "utr contains more than 10 digits" in {
        requestIsAuthenticated()
        val result = await(doSubscriptionRequest(replaceFields(Seq((__, "utr", "12345678901")))))

        result.status shouldBe 400
      }

      "name contains invalid characters" in {
        requestIsAuthenticated()
        val result = await(doSubscriptionRequest(replaceFields(Seq((agency, "name", "InvalidAgencyName!@")))))

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
        val result = await(doSubscriptionRequest(removeFields(Seq(agency \ "email"))))

        result.status shouldBe 400
      }
      "email has no local part" in {
        requestIsAuthenticated()
        val result = await(doSubscriptionRequest(replaceFields(Seq((agency, "email", "@domain")))))

        result.status shouldBe 400
      }
      "email has no domain part" in {
        requestIsAuthenticated()
        val result = await(doSubscriptionRequest(replaceFields(Seq((agency, "email", "local@")))))

        result.status shouldBe 400
      }
      "email has no @" in {
        requestIsAuthenticated()
        val result = await(doSubscriptionRequest(replaceFields(Seq((agency, "email", "local")))))

        result.status shouldBe 400
      }

      "telephone number contains words" in {
        requestIsAuthenticated()
        val result = await(doSubscriptionRequest(replaceFields(Seq((agency, "telephone", "0123 456 78aa")))))

        result.status shouldBe 400
      }

      "postcode is missing" in {
        requestIsAuthenticated()
        val result = await(doSubscriptionRequest(removeFields(Seq(address \ "postcode"))))

        result.status shouldBe 400
      }

      "known facts postcode is not valid" in {
        requestIsAuthenticated()
        val result = await(doSubscriptionRequest(replaceFields(Seq((__ \ "knownFacts", "postcode", "1234567")))))

        result.status shouldBe 400
      }

      "countryCode is missing" in {
        requestIsAuthenticated()
        val result = await(doSubscriptionRequest(removeFields(Seq(address \ "countryCode"))))

        result.status shouldBe 400
      }
    }

    "throw a 500 error if " when {
      "query allocated enrolment fails in EMAC " in {
        requestIsAuthenticated()
        organisationRegistrationExists(utr, isAnASAgent = true, arn = arn)
        subscriptionSucceeds(utr, Json.parse(subscriptionRequest).as[SubscriptionRequest])
        allocatedPrincipalEnrolmentFails(arn)

        val result = await(doSubscriptionRequest())

        result.status shouldBe 500
      }

      "delete known facts fails in EMAC " in {
        requestIsAuthenticated()
        organisationRegistrationExists(utr, isAnASAgent = false, arn = arn)
        subscriptionSucceeds(utr, Json.parse(subscriptionRequest).as[SubscriptionRequest])
        allocatedPrincipalEnrolmentNotExists(arn)
        deleteKnownFactsFails("")

        val result = await(doSubscriptionRequest())

        result.status shouldBe 500
      }

      "create known facts fails in EMAC " in {
        requestIsAuthenticated()
        organisationRegistrationExists(utr, isAnASAgent = false, arn = arn)
        subscriptionSucceeds(utr, Json.parse(subscriptionRequest).as[SubscriptionRequest])
        allocatedPrincipalEnrolmentNotExists(arn)
        deleteKnownFactsSucceeds("")
        createKnownFactsFails("")

        val result = await(doSubscriptionRequest())

        result.status shouldBe 500
      }

      "create enrolment fails in EMAC " in {
        requestIsAuthenticatedWithNoEnrolments()
        organisationRegistrationExists(utr, isAnASAgent = false, arn = arn)
        subscriptionSucceeds(utr, Json.parse(subscriptionRequest).as[SubscriptionRequest])
        allocatedPrincipalEnrolmentNotExists(arn)
        createKnownFactsSucceeds(arn)
        enrolmentFails(groupId, arn)

        val result = await(doSubscriptionRequest())

        result.status shouldBe 500
      }
    }
  }

  private def doSubscriptionRequest(request: String = subscriptionRequest) = new Resource(s"/agent-subscription/subscription", port).postAsJson(request)

  private def removeFields(fields: Seq[JsPath]): String = {
    val request = Json.parse(subscriptionRequest).as[JsObject]
    val filtered: JsObject = removeFields(request, fields)

    stringify(filtered)
  }

  private def removeFields(jsObject: JsObject, fields: Seq[JsPath]): JsObject = {
    val transformer = fields.map(field => field.json.prune).reduce((a, b) => a andThen b)
    jsObject.transform(transformer).get
  }

  private def replaceFields(fields: Seq[(JsPath, String, String)]): String = {
    val request = Json.parse(subscriptionRequest).as[JsObject]
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

  private val subscriptionRequest: String =
    s"""
       |{
       |  "utr": "${utr.value}",
       |  "knownFacts": {
       |    "postcode": "AA1 1AA"
       |  },
       |  "agency": {
       |    "name": "My Agency",
       |    "address": {
       |      "addressLine1": "Flat 1",
       |      "addressLine2": "1 Some Street",
       |      "addressLine3": "Anytown",
       |      "addressLine4": "County",
       |      "postcode": "AA1 2AA",
       |      "countryCode": "GB"
       |    },
       |    "email": "agency@example.com",
       |    "telephone": "0123 456 7890"
       |  }
       |}
     """.stripMargin
}
