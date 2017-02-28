/*
 * Copyright 2016 HM Revenue & Customs
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

import uk.gov.hmrc.agentsubscription.stubs.AuthStub.{requestIsAuthenticated, requestIsNotAuthenticated}
import uk.gov.hmrc.agentsubscription.stubs.DesStubs
import uk.gov.hmrc.agentsubscription.support.{BaseISpec, Resource}

import scala.language.postfixOps

class RegistrationControllerISpec extends BaseISpec with DesStubs {

  "GET of /registration/:utr/postcode/:postcode" should {
    "return a 401 when the user is not authenticated" in {
      requestIsNotAuthenticated()
      val response = await(new Resource("/agent-subscription/registration/0123456789/postcode/AA1%201AA", port).get)
      response.status shouldBe 401
    }

    "return 404 when no match is found in des" in {
      requestIsAuthenticated()
      registrationDoesNotExist("0000000000")
      val response = await(new Resource("/agent-subscription/registration/0000000000/postcode/AA1%201AA", port).get)
      response.status shouldBe 404
    }

    "return 404 when des reports an invalid utr" in {
      requestIsAuthenticated()
      utrIsInvalid()
      val response = await(new Resource("/agent-subscription/registration/xyz/postcode/AA1%201AA", port).get)
      response.status shouldBe 404
    }

    "return 404 when des returns a match for the utr but the post codes do not match" in {
      requestIsAuthenticated()
      registrationExists("0123456789")
      val response = await(new Resource("/agent-subscription/registration/0123456789/postcode/NOMATCH", port).get)
      response.status shouldBe 404
    }

    "return 404 when des returns a response with no postcode" in {
      requestIsAuthenticated()
      registrationExistsWithNoPostcode("0123456789")
      val response = await(new Resource("/agent-subscription/registration/0123456789/postcode/AA1%201AA", port).get)
      response.status shouldBe 404
    }

    "return 200 when des returns an AS Agent for the utr and the postcodes match" in {
      requestIsAuthenticated()
      registrationExists("0123456789", true)
      val response = await(new Resource("/agent-subscription/registration/0123456789/postcode/AA1%201AA", port).get)
      response.status shouldBe 200
      (response.json \ "isSubscribedToAgentServices" ).as[Boolean] shouldBe true
    }

    "return 200 when des returns a non-AS Agent for the utr and the postcodes match" in {
      requestIsAuthenticated()
      registrationExists("0123456789", false)
      val response = await(new Resource("/agent-subscription/registration/0123456789/postcode/AA1%201AA", port).get)
      response.status shouldBe 200
      (response.json \ "isSubscribedToAgentServices" ).as[Boolean] shouldBe false
    }
  }
}
