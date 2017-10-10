/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.agentsubscription.auth

import java.net.URL

import org.mockito.ArgumentMatchers._
import org.mockito.Mockito
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import play.api.mvc.{ActionBuilder, Result, Results}
import play.api.test.FakeRequest
import uk.gov.hmrc.agentsubscription.connectors.AuthConnector
import uk.gov.hmrc.agentsubscription.support.AkkaMaterializerSpec
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.http.HeaderCarrier

class AuthActionSpec extends UnitSpec with AuthActions with MockitoSugar with Results with BeforeAndAfterEach with AkkaMaterializerSpec {
  override val authConnector = mock[AuthConnector]

  private val authorityUrl = new URL("http://localhost/auth/authority")

  "authorisedWithSubscribingAgent" should {
    "return Unauthorized" when {
      "user is not an agent" in {
        when(authConnector.currentAuthority()(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future successful Some(Authority(authorityUrl, Some("12345-credId"), Some("GovernmentGateway"), "Individual", "/enrolments")))

        status(response(withAgentAffinityGroup)) shouldBe 401

      }
    }

    "allow access and decorate the request with Authority" when {
      "user is an agent" in {
        when(authConnector.currentAuthority()(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future successful Some(Authority(authorityUrl, Some("12345-credId"), Some("GovernmentGateway"), "Agent", "/enrolments")))

        status(response(withAgentAffinityGroup)) shouldBe 200
      }
    }

  }

  "authorisedAgentWithEnrolments" should {
    "return Unauthorized" when {
      "user is not an agent" in {
        when(authConnector.currentAuthority()(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future successful Some(Authority(authorityUrl, Some("12345-credId"), Some("GovernmentGateway"), "Individual", "/enrolments")))

        status(responseWithEnrolments(withAgentAffinityGroupAndEnrolments)) shouldBe 401
      }
    }

    "decorate the request with Authority and enrolments" when {
      "user has enrolments" in {
        when(authConnector.currentAuthority()(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future successful Some(Authority(authorityUrl, Some("12345-credId"), Some("GovernmentGateway"), "Agent", "/enrolments")))
        when(authConnector.enrolments(any[Authority])(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(List(Enrolment("MY-ENROLMENT")))

        val result = responseWithEnrolments(withAgentAffinityGroupAndEnrolments)
        status(result) shouldBe 200
      }
    }
  }

  private def response(actionBuilder: ActionBuilder[RequestWithAuthority]): Result = {
    val action = actionBuilder { request =>
      request.authority shouldBe Authority(
        fetchedFrom = authorityUrl,
        authProviderId = Some("12345-credId"),
        authProviderType = Some("GovernmentGateway"),
        affinityGroup = "Agent",
        enrolmentsUrl = "/enrolments")

      Ok
    }
    await(action(FakeRequest()))
  }

  private def responseWithEnrolments(actionBuilder: ActionBuilder[RequestWithEnrolments]): Result = {
    val action = actionBuilder { request =>
      request.enrolments should matchPattern {
        case Enrolment(enrolmentKey) :: Nil if enrolmentKey == "MY-ENROLMENT" =>
      }

      Ok
    }
    await(action(FakeRequest()))
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset(authConnector)
  }
}
