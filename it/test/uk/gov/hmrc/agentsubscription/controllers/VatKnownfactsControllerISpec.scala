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
import uk.gov.hmrc.agentsubscription.stubs.AuthStub
import uk.gov.hmrc.agentsubscription.stubs.DesStubs
import uk.gov.hmrc.agentsubscription.support.BaseISpec
import uk.gov.hmrc.agentsubscription.support.Resource
import uk.gov.hmrc.domain.Vrn

class VatKnownfactsControllerISpec
extends BaseISpec
with DesStubs
with AuthStub {

  private val vrn = Vrn("888913457")
  implicit val ws: WSClient = app.injector.instanceOf[WSClient]

  "GET of /vat-known-facts/vrn/:vrn/dateOfRegistration/:dateOfReg" should {
    "return a 401 when the user is not authenticated" in {
      requestIsNotAuthenticated()
      val response = new Resource("/agent-subscription/vat-known-facts/vrn/888913457/dateOfRegistration/2010-03-31", port).get()
      response.status shouldBe 401
    }

    "return a 401 when auth returns unexpected response code in the headers" in {
      requestIsNotAuthenticated(header = "some strange response from auth")
      val response = new Resource("/agent-subscription/vat-known-facts/vrn/888913457/dateOfRegistration/2010-03-31", port).get()
      response.status shouldBe 401
    }

    "return 404 when no match is found in des" in {
      vatKnownfactsRecordDoesNotExist(vrn)
      requestIsAuthenticatedWithNoEnrolments()

      val response = new Resource("/agent-subscription/vat-known-facts/vrn/888913457/dateOfRegistration/2010-03-31", port).get()
      response.status shouldBe 404
    }

    "return 400 when the VRN is invalid" in {
      requestIsAuthenticatedWithNoEnrolments()
      vrnIsInvalid(Vrn("0000"))

      val response = new Resource("/agent-subscription/vat-known-facts/vrn/0000/dateOfRegistration/2010-03-31", port).get()
      response.status shouldBe 400
    }

    "return 500 when DES unexpectedly reports 5xx exception" in {
      requestIsAuthenticatedWithNoEnrolments()
      vatKnownfactsRecordFails()

      val response = new Resource("/agent-subscription/vat-known-facts/vrn/888913457/dateOfRegistration/2010-03-31", port).get()
      response.status shouldBe 500
    }

    "return 404 when des returns a record for vrn  but the date of registration supplied does not match" in {
      vatKnownfactsRecordExists(vrn)
      requestIsAuthenticatedWithNoEnrolments()

      val response = new Resource("/agent-subscription/vat-known-facts/vrn/888913457/dateOfRegistration/2012-04-11", port).get()
      response.status shouldBe 404
    }

    "return 200 when des returns a match for the vrn and date of registration" in {
      requestIsAuthenticatedWithNoEnrolments()
      vatKnownfactsRecordExists(vrn)

      val response = new Resource("/agent-subscription/vat-known-facts/vrn/888913457/dateOfRegistration/2010-03-31", port).get()
      response.status shouldBe 200
    }
  }

}
