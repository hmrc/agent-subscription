/*
 * Copyright 2018 HM Revenue & Customs
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

import com.kenshoo.play.metrics.Metrics
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscription.auth.AuthActions.Provider
import uk.gov.hmrc.agentsubscription.connectors.MicroserviceAuthConnector
import uk.gov.hmrc.agentsubscription.service.RegistrationService
import uk.gov.hmrc.agentsubscription.support.{ AkkaMaterializerSpec, ResettingMockitoSugar, AuthData }
import uk.gov.hmrc.auth.core.{ AffinityGroup, AuthConnector, authorise }
import uk.gov.hmrc.auth.core.retrieve.{ Credentials, Retrieval, ~ }
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class RegistrationControllerSpec extends UnitSpec with AkkaMaterializerSpec with ResettingMockitoSugar with AuthData {

  private val metrics: Metrics = resettingMock[Metrics]
  private val microserviceAuthConnector: MicroserviceAuthConnector = resettingMock[MicroserviceAuthConnector]
  private val registrationService = resettingMock[RegistrationService]
  private val authConnector = resettingMock[AuthConnector]

  private val controller = new RegistrationController(registrationService)(metrics, microserviceAuthConnector)

  private val validUtr = Utr("2000000000")
  private val validPostcode = "AA1 1AA"

  private val invalidUtr = Utr("not a UTR")
  private val invalidPostcode = "not a postcode"

  val hc: HeaderCarrier = new HeaderCarrier
  val provider = Provider("provId", "provType")

  private def agentAuthStub(returnValue: Future[~[Option[AffinityGroup], Credentials]]) =
    when(microserviceAuthConnector.authorise(any[authorise.Predicate](), any[Retrieval[~[Option[AffinityGroup], Credentials]]]())(any(), any())).thenReturn(returnValue)

  "register" should {
    "return 400 INVALID_UTR if the UTR is invalid " in {
      agentAuthStub(agentAffinityWithCredentials)
      val result = controller.getRegistration(invalidUtr, validPostcode)(FakeRequest())
      status(result) shouldBe 400
      (contentAsJson(result) \ "code").as[String] shouldBe "INVALID_UTR"
    }

    "return 400 INVALID_POSTCODE if the postcode is invalid " in {
      agentAuthStub(agentAffinityWithCredentials)
      val result = controller.getRegistration(validUtr, invalidPostcode)(FakeRequest())
      status(result) shouldBe 400
      (contentAsJson(result) \ "code").as[String] shouldBe "INVALID_POSTCODE"
    }
  }
}
