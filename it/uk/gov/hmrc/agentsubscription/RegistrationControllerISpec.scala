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

package uk.gov.hmrc.agentsubscription

import org.scalatestplus.play.OneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.agentsubscription.controllers.RegistrationController
import uk.gov.hmrc.agentsubscription.stubs.AuthStub.{requestIsAuthenticated, requestIsNotAuthenticated}
import uk.gov.hmrc.agentsubscription.stubs.DesStubs
import uk.gov.hmrc.agentsubscription.support.{Resource, WireMockSupport}
import uk.gov.hmrc.play.test.UnitSpec

import scala.language.postfixOps

class RegistrationControllerISpec extends UnitSpec with OneServerPerSuite with WireMockSupport with DesStubs {
  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      "microservice.services.auth.port" -> wireMockPort,
      "microservice.services.des.port" -> wireMockPort
    )
    .build()

  private implicit val materializer = app.materializer

  private lazy val controller: RegistrationController = app.injector.instanceOf[RegistrationController]

  "GET of /registration/:utr/postcode/:postcode" should {
    "return a 401 when the user is not authenticated" in {
      requestIsNotAuthenticated()
      val response = await(new Resource("/agent-subscription/registration/0123456789/postcode/BN12 4SE", port).get)
      response.status shouldBe 401
    }

    "return 404 when no match is found in des" in {
      requestIsAuthenticated()
      noMatchForUtr()
      val response = await(new Resource("/agent-subscription/registration/0000000000/postcode/BN12 4SE", port).get)
      response.status shouldBe 404
    }

    "return 404 when des reports an invalid utr" in {
      requestIsAuthenticated()
      utrIsInvalid()
      val response = await(new Resource("/agent-subscription/registration/xyz/postcode/BN12 4SE", port).get)
      response.status shouldBe 404
    }

    "return 404 when des returns a match for the utr but the post codes do not match" in {
      requestIsAuthenticated()
      findMatchForUtrForASAgent()
      val response = await(new Resource("/agent-subscription/registration/0123456789/postcode/NOMATCH", port).get)
      response.status shouldBe 404
    }

    "return 200 when des returns an AS Agent for the utr and the postcodes match" in {
      requestIsAuthenticated()
      findMatchForUtrForASAgent()
      val response = await(new Resource("/agent-subscription/registration/0123456789/postcode/BN12 4SE", port).get)
      response.status shouldBe 200
      (response.json \ "isSubscribedToAgentServices" ).as[Boolean] shouldBe true
    }

    "return 200 when des returns a non-AS Agent for the utr and the postcodes match" in {
      requestIsAuthenticated()
      findMatchForUtrForNonASAgent()
      val response = await(new Resource("/agent-subscription/registration/0123456789/postcode/BN12 4SE", port).get)
      response.status shouldBe 200
      (response.json \ "isSubscribedToAgentServices" ).as[Boolean] shouldBe false
    }
  }
}
