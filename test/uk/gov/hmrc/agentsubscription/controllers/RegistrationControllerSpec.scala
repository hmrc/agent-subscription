/*
 * Copyright 2023 HM Revenue & Customs
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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import play.api.mvc.ControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers
import play.api.test.Helpers.defaultAwaitTimeout
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscription.auth.AuthActions
import uk.gov.hmrc.agentsubscription.auth.AuthActions.Provider
import uk.gov.hmrc.agentsubscription.service.RegistrationService
import uk.gov.hmrc.agentsubscription.support.{AkkaMaterializerSpec, AuthData, ResettingMockitoSugar, UnitSpec}
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, ~}
import uk.gov.hmrc.auth.core.{AffinityGroup, PlayAuthConnector, authorise}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class RegistrationControllerSpec(implicit val ec: ExecutionContext)
    extends UnitSpec with AkkaMaterializerSpec with ResettingMockitoSugar with AuthData {

  private val registrationService = resettingMock[RegistrationService]
  private val authActions = resettingMock[AuthActions]
  private val cc = resettingMock[ControllerComponents]
  private val mockPlayAuthConnector = resettingMock[PlayAuthConnector]

  private val controller = new RegistrationController(registrationService, authActions, cc)(ec)

  private val validUtr = Utr("2000000000")
  private val validPostcode = "AA1 1AA"

  private val invalidUtr = Utr("not a UTR")
  private val invalidPostcode = "not a postcode"

  val hc: HeaderCarrier = new HeaderCarrier
  val provider = Provider("provId", "provType")

  private def agentAuthStub(returnValue: Future[~[Option[AffinityGroup], Option[Credentials]]]) =
    when(
      mockPlayAuthConnector
        .authorise(any[authorise.Predicate](), any[Retrieval[~[Option[AffinityGroup], Option[Credentials]]]]())(
          any[HeaderCarrier](),
          any[ExecutionContext]()
        )
    )
      .thenReturn(returnValue)

  "register" should {
    "return 400 INVALID_UTR if the UTR is invalid " in {
      agentAuthStub(agentAffinityWithCredentials)
      val result = controller.getRegistration(invalidUtr, validPostcode)(FakeRequest())
      status(result) shouldBe 400
      (Helpers.contentAsJson(result) \ "code").as[String] shouldBe "INVALID_UTR"
    }

    "return 400 INVALID_POSTCODE if the postcode is invalid " in {
      agentAuthStub(agentAffinityWithCredentials)
      val result = controller.getRegistration(validUtr, invalidPostcode)(FakeRequest())
      status(result) shouldBe 400
      (Helpers.contentAsJson(result) \ "code").as[String] shouldBe "INVALID_POSTCODE"
    }
  }
}
