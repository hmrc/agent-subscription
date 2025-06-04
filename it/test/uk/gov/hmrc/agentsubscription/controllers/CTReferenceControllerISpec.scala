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
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscription.model.Crn
import uk.gov.hmrc.agentsubscription.stubs.AuthStub
import uk.gov.hmrc.agentsubscription.stubs.DesStubs
import uk.gov.hmrc.agentsubscription.support.BaseISpec
import uk.gov.hmrc.agentsubscription.support.Resource

class CTReferenceControllerISpec
extends BaseISpec
with DesStubs
with AuthStub {

  val utr = Utr("7000000002")
  val crn = Crn("SC123456")

  implicit val ws: WSClient = app.injector.instanceOf[WSClient]

  "GET of /corporation-tax-utr/:utr/crn/:crn" should {
    "return a 401 when the user is not authenticated" in {
      requestIsNotAuthenticated()
      val response = new Resource("/agent-subscription/corporation-tax-utr/7000000002/crn/SC123456", port).get()
      response.status shouldBe 401
    }

    "return a 401 when auth returns unexpected response code in the headers" in {
      requestIsNotAuthenticated(header = "some strange response from auth")
      val response = new Resource("/agent-subscription/corporation-tax-utr/7000000002/crn/SC123456", port).get()
      response.status shouldBe 401
    }

    "return 404 when no match is found in des" in {
      requestIsAuthenticatedWithNoEnrolments()
      ctUtrRecordDoesNotExist(crn)

      val response = new Resource("/agent-subscription/corporation-tax-utr/7000000002/crn/SC123456", port).get()
      response.status shouldBe 404
    }

    "return 400 when the CRN is invalid" in {
      requestIsAuthenticatedWithNoEnrolments()
      crnIsInvalid(crn)
      val response = new Resource("/agent-subscription/corporation-tax-utr/7000000002/crn/SC123456", port).get()
      response.status shouldBe 400
    }

    "return 500 when DES unexpectedly reports 5xx exception" in {
      requestIsAuthenticatedWithNoEnrolments()
      ctUtrRecordFails()
      val response = new Resource("/agent-subscription/corporation-tax-utr/7000000002/crn/SC123456", port).get()
      response.status shouldBe 500
    }

    "return 404 when des returns a record for the crn but the ct utr supplied does not match" in {
      requestIsAuthenticatedWithNoEnrolments()
      ctUtrRecordExists(crn)

      val response = new Resource("/agent-subscription/corporation-tax-utr/8000000007/crn/SC123456", port).get()
      response.status shouldBe 404
    }

    "return 200 when des returns a match for the crn and ct utr" in {
      requestIsAuthenticatedWithNoEnrolments()
      ctUtrRecordExists(crn)

      val response = new Resource("/agent-subscription/corporation-tax-utr/1234567890/crn/SC123456", port).get()
      response.status shouldBe 200
    }
  }

}
