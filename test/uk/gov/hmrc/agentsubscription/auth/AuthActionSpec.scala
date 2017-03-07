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

import org.scalatest.mock.MockitoSugar
import play.api.mvc.{ActionBuilder, Request, Result, Results}
import uk.gov.hmrc.agentsubscription.connectors.AuthConnector
import uk.gov.hmrc.play.test.UnitSpec
import org.mockito.Mockito._
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito
import org.scalatest.BeforeAndAfterEach
import play.api.test.FakeRequest
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class AuthActionSpec extends UnitSpec with AuthActions with MockitoSugar with Results with BeforeAndAfterEach {
  override val authConnector = mock[AuthConnector]

  "agentWithEnrolments" should {
    "return Unauthorized" when {
      "user is not an agent" in {
        when(authConnector.currentAuthority()(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future successful Some(Authority("Individual", "/enrolments")))

        status(response(agentWithEnrolments)) shouldBe 401

      }
    }

    "decorate the request with enrolments" when {
      "user has enrolments" in {
        when(authConnector.currentAuthority()(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future successful Some(Authority("Agent", "/enrolments")))
        when(authConnector.enrolments(any[String])(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(List(Enrolment("MY-ENROLMENT")))

        status(response(agentWithEnrolments)) shouldBe 200
      }
    }
  }

  private def response(actionBuilder: ActionBuilder[RequestWithEnrolments]): Result = {
    val action = actionBuilder { r => r.enrolments match {
      case Enrolment(key) :: Nil if key == "MY-ENROLMENT" => Ok
    }}
    await(action(FakeRequest()))
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset(authConnector)
  }
}
