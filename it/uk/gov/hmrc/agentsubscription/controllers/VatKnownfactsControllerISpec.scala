package uk.gov.hmrc.agentsubscription.controllers

import play.api.libs.ws.WSClient
import uk.gov.hmrc.agentsubscription.stubs.{ AuthStub, DesStubs }
import uk.gov.hmrc.agentsubscription.support.{ BaseISpec, Resource }
import uk.gov.hmrc.domain.Vrn

class VatKnownfactsControllerISpec extends BaseISpec with DesStubs with AuthStub {
  private val vrn = Vrn("888913457")
  implicit val ws = app.injector.instanceOf[WSClient]

  "GET of /vat-known-facts/vrn/:vrn/dateOfRegistration/:dateOfReg" should {
    "return a 401 when the user is not authenticated" in {
      requestIsNotAuthenticated()
      val response = await(new Resource("/agent-subscription/vat-known-facts/vrn/888913457/dateOfRegistration/2010-03-31", port).get)
      response.status shouldBe 401
    }

    "return a 401 when auth returns unexpected response code in the headers" in {
      requestIsNotAuthenticated(header = "some strange response from auth")
      val response = await(new Resource("/agent-subscription/vat-known-facts/vrn/888913457/dateOfRegistration/2010-03-31", port).get)
      response.status shouldBe 401
    }

    "return 404 when no match is found in des" in {
      vatKnownfactsRecordDoesNotExist(vrn)

      val response = await(new Resource("/agent-subscription/vat-known-facts/vrn/888913457/dateOfRegistration/2010-03-31", port).get)
      response.status shouldBe 404
    }

    "return 400 when the VRN is invalid" in {
      requestIsAuthenticatedWithNoEnrolments()
      vrnIsInvalid(Vrn("0000"))

      val response = await(new Resource("/agent-subscription/vat-known-facts/vrn/0000/dateOfRegistration/2010-03-31", port).get)
      response.status shouldBe 400
    }

    "return 500 when DES unexpectedly reports 5xx exception" in {
      requestIsAuthenticatedWithNoEnrolments()
      vatKnownfactsRecordFails()

      val response = await(new Resource("/agent-subscription/vat-known-facts/vrn/888913457/dateOfRegistration/2010-03-31", port).get)
      response.status shouldBe 500
    }

    "return 404 when des returns a record for vrn  but the date of registration supplied does not match" in {
      vatKnownfactsRecordExists(vrn)

      val response = await(new Resource("/agent-subscription/vat-known-facts/vrn/888913457/dateOfRegistration/2012-04-11", port).get)
      response.status shouldBe 404
    }

    "return 200 when des returns a match for the vrn and date of registration" in {
      requestIsAuthenticatedWithNoEnrolments()
      vatKnownfactsRecordExists(vrn)

      val response = await(new Resource("/agent-subscription/vat-known-facts/vrn/888913457/dateOfRegistration/2010-03-31", port).get)
      response.status shouldBe 200
    }
  }
}
