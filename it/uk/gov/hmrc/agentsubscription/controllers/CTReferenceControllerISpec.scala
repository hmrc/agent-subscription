package uk.gov.hmrc.agentsubscription.controllers

import play.api.libs.ws.WSClient
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscription.model.Crn
import uk.gov.hmrc.agentsubscription.stubs.{AuthStub, DesStubs}
import uk.gov.hmrc.agentsubscription.support.{BaseISpec, Resource}

class CTReferenceControllerISpec extends BaseISpec with DesStubs with AuthStub {
  val utr = Utr("7000000002")
  val crn = Crn("SC123456")

  implicit val ws = app.injector.instanceOf[WSClient]

  "GET of /corporation-tax-utr/:utr/crn/:crn" should {
    "return a 401 when the user is not authenticated" in {
      requestIsNotAuthenticated()
      val response = await(new Resource("/agent-subscription/corporation-tax-utr/7000000002/crn/SC123456", port).get)
      response.status shouldBe 401
    }

    "return a 401 when auth returns unexpected response code in the headers" in {
      requestIsNotAuthenticated(header = "some strange response from auth")
      val response = await(new Resource("/agent-subscription/corporation-tax-utr/7000000002/crn/SC123456", port).get)
      response.status shouldBe 401
    }

    "return 404 when no match is found in des" in {
      ctUtrRecordDoesNotExist(crn)

      val response = await(new Resource("/agent-subscription/corporation-tax-utr/7000000002/crn/SC123456", port).get)
      response.status shouldBe 404
    }

    "return 400 when the CRN is invalid" in {
      requestIsAuthenticatedWithNoEnrolments()
      crnIsInvalid(crn)
      val response = await(new Resource("/agent-subscription/corporation-tax-utr/7000000002/crn/SC123456", port).get)
      response.status shouldBe 400
    }

    "return 500 when DES unexpectedly reports 5xx exception" in {
      requestIsAuthenticatedWithNoEnrolments()
      ctUtrRecordFails()
      val response = await(new Resource("/agent-subscription/corporation-tax-utr/7000000002/crn/SC123456", port).get)
      response.status shouldBe 500
    }

    "return 404 when des returns a match for the crn but the ct utr supplied do not match" in {
      ctUtrRecordDoesNotExist(crn)

      val response = await(new Resource("/agent-subscription/corporation-tax-utr/8000000007/crn/SC123456", port).get)
      response.status shouldBe 404
    }

    "return 200 when des returns a match for the crn and ct utr" in {
      requestIsAuthenticatedWithNoEnrolments()
      ctUtrRecordExists(crn)

      val response = await(new Resource("/agent-subscription/corporation-tax-utr/1234567890/crn/SC123456", port).get)
      response.status shouldBe 200
    }
  }
}
