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

package uk.gov.hmrc.connectors

import com.kenshoo.play.metrics.Metrics
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{ reset, when }
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import play.api.http.Status._
import play.api.libs.json.JsValue
import play.api.mvc.Results.Ok
import play.api.mvc.{ AnyContent, Request, Result }
import uk.gov.hmrc.agentsubscription.connectors.{ AuthActions, AuthIds, MicroserviceAuthConnector, Provider }
import uk.gov.hmrc.agentsubscription.support.TestData
import uk.gov.hmrc.auth.core.{ authorise, _ }
import uk.gov.hmrc.auth.core.retrieve.{ Credentials, Retrieval, ~ }
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class AuthActionsSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach with TestData {

  val mockMicroserviceAuthConnector: MicroserviceAuthConnector = mock[MicroserviceAuthConnector]
  val mockMetrics: Metrics = mock[Metrics]
  val mockAuthConnector: AuthActions = new AuthActions(mockMetrics, mockMicroserviceAuthConnector)

  private type SubscriptionAuthAction = Request[JsValue] => AuthIds => Future[Result]
  private type RegistrationAuthAction = Request[AnyContent] => Provider => Future[Result]

  val subscriptionAction: SubscriptionAuthAction = { implicit request => implicit authIds => Future successful Ok }
  val registrationAction: RegistrationAuthAction = { implicit request => implicit provider => Future successful Ok }

  private def agentAuthStub(returnValue: Future[~[~[~[Option[AffinityGroup], Enrolments], Credentials], Option[String]]]) =
    when(mockMicroserviceAuthConnector.authorise(any[authorise.Predicate](), any[Retrieval[~[~[~[Option[AffinityGroup], Enrolments], Credentials], Option[String]]]]())(any(), any())).thenReturn(returnValue)

  override def beforeEach(): Unit = reset(mockMicroserviceAuthConnector)

  "affinityGroupAndEnrolments" should {
    "return OK for an Agent with HMRC-AS-AGENT enrolment" in {
      agentAuthStub(agentAffinityAndEnrolments)

      val response: Result = await(mockAuthConnector.affinityGroupAndEnrolments(subscriptionAction).apply(fakeRequestJson))

      status(response) shouldBe OK
    }
    "return OK for an Agent with no enrolment" in {
      agentAuthStub(agentNoEnrolments)

      val response: Result = await(mockAuthConnector.affinityGroupAndEnrolments(subscriptionAction).apply(fakeRequestJson))

      status(response) shouldBe OK
    }

    "return UNAUTHORISED when the user does not belong to Agent affinity group" in {
      agentAuthStub(agentIncorrectAffinity)

      val response: Result = await(mockAuthConnector.affinityGroupAndEnrolments(subscriptionAction).apply(fakeRequestJson))

      status(response) shouldBe UNAUTHORIZED
    }

    "return UNAUTHORISED when auth fails to return an AffinityGroup or Enrolments" in {
      agentAuthStub(neitherHaveAffinityOrEnrolment)

      val response: Result = await(mockAuthConnector.affinityGroupAndEnrolments(subscriptionAction).apply(fakeRequestJson))

      status(response) shouldBe UNAUTHORIZED
    }

    "return UNAUTHORISED when auth throws an error" in {
      agentAuthStub(failedStubForAgent)

      val response: Result = await(mockAuthConnector.affinityGroupAndEnrolments(subscriptionAction).apply(fakeRequestJson))

      status(response) shouldBe UNAUTHORIZED
    }
  }

  "affinityGroupAndCredentials" should {

    "return OK when we have the correct affinity group" in {
      when(mockMicroserviceAuthConnector.authorise(any(), any[Retrieval[~[Option[AffinityGroup], Credentials]]]())(any(), any()))
        .thenReturn(validAgentAffinity)

      val response: Result = await(mockAuthConnector.affinityGroupAndCredentials(registrationAction).apply(fakeRequestAny))

      status(response) shouldBe OK
    }

    "return UNAUTHORISED when we have the wrong affinity group" in {
      when(mockMicroserviceAuthConnector.authorise(any(), any[Retrieval[~[Option[AffinityGroup], Credentials]]]())(any(), any()))
        .thenReturn(invalidAgentAffinity)

      val response: Result = await(mockAuthConnector.affinityGroupAndCredentials(registrationAction).apply(fakeRequestAny))

      status(response) shouldBe UNAUTHORIZED
    }

    "return UNAUTHORISED when we have no affinity group" in {
      when(mockMicroserviceAuthConnector.authorise(any(), any[Retrieval[~[Option[AffinityGroup], Credentials]]]())(any(), any()))
        .thenReturn(noAffinity)

      val response: Result = await(mockAuthConnector.affinityGroupAndCredentials(registrationAction).apply(fakeRequestAny))

      status(response) shouldBe UNAUTHORIZED
    }

    "return UNAUTHORISED when auth throws an error" in {
      when(mockMicroserviceAuthConnector.authorise(any(), any[Retrieval[~[Option[AffinityGroup], Credentials]]]())(any(), any()))
        .thenReturn(Future failed new NullPointerException)

      val response: Result = await(mockAuthConnector.affinityGroupAndCredentials(registrationAction).apply(fakeRequestAny))

      status(response) shouldBe UNAUTHORIZED
    }
  }
}
