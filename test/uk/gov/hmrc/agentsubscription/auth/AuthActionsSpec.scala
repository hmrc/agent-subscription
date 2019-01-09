/*
 * Copyright 2019 HM Revenue & Customs
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

import com.kenshoo.play.metrics.Metrics
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import play.api.http.Status._
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Result
import play.api.mvc.Results.Ok
import play.api.test.FakeRequest
import uk.gov.hmrc.agentsubscription.auth.AuthActions.{OverseasAuthAction, RegistrationAuthAction, SubscriptionAuthAction}
import uk.gov.hmrc.agentsubscription.connectors.MicroserviceAuthConnector
import uk.gov.hmrc.agentsubscription.support.AuthData
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, ~}
import uk.gov.hmrc.auth.core.{authorise, _}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class AuthActionsSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach with AuthData {

  val mockMicroserviceAuthConnector: MicroserviceAuthConnector = mock[MicroserviceAuthConnector]
  val mockMetrics: Metrics = mock[Metrics]
  val mockAuthConnector: AuthActions = new AuthActions(mockMetrics, mockMicroserviceAuthConnector)

  val subscriptionAction: SubscriptionAuthAction = { implicit request => implicit authIds => Future successful Ok }
  val registrationAction: RegistrationAuthAction = { implicit request => implicit provider => Future successful Ok }
  val overseasAgentAction: OverseasAuthAction = { implicit request => implicit provider => Future successful Ok }

  private def agentAuthStub(returnValue: Future[~[~[Option[AffinityGroup], Credentials], Option[String]]]) =
    when(mockMicroserviceAuthConnector.authorise(any[authorise.Predicate](), any[Retrieval[~[~[Option[AffinityGroup], Credentials], Option[String]]]]())(any(), any())).thenReturn(returnValue)

  override def beforeEach(): Unit = reset(mockMicroserviceAuthConnector)

  val fakeRequest = FakeRequest().withBody[JsValue](Json.parse("""{}"""))

  "authorisedWithAffinityGroup" should {
    "return OK for an Agent with Agent affinity group" in {
      agentAuthStub(agentAffinityWithCredentialsAndGroupId)

      val response: Result = await(mockAuthConnector.authorisedWithAffinityGroup(subscriptionAction).apply(fakeRequest))

      status(response) shouldBe OK
    }

    "return UNAUTHORISED when the user does not belong to Agent affinity group" in {
      agentAuthStub(agentIncorrectAffinity)

      val response: Result = await(mockAuthConnector.authorisedWithAffinityGroup(subscriptionAction).apply(fakeRequest))

      status(response) shouldBe 403
    }

    "return UNAUTHORISED when auth fails to return an AffinityGroup or Enrolments" in {
      agentAuthStub(neitherHaveAffinityOrEnrolment)

      val response: Result = await(mockAuthConnector.authorisedWithAffinityGroup(subscriptionAction).apply(fakeRequest))

      status(response) shouldBe UNAUTHORIZED
    }

    "return the same error when auth throws an error" in {
      agentAuthStub(failedStubForAgent)

      val thrown = intercept[Exception] {
        await(mockAuthConnector.authorisedWithAffinityGroup(subscriptionAction).apply(fakeRequest))
      }

      thrown.getMessage shouldBe "oh no !"
    }
  }

  "authorisedWithAffinityGroupAndCredentials" should {

    val fakeRequest = FakeRequest()

    "return OK when we have the correct affinity group" in {
      when(mockMicroserviceAuthConnector.authorise(any(), any[Retrieval[~[Option[AffinityGroup], Credentials]]]())(any(), any()))
        .thenReturn(validAgentAffinity)

      val response: Result = await(mockAuthConnector.authorisedWithAffinityGroupAndCredentials(registrationAction).apply(fakeRequest))

      status(response) shouldBe OK
    }

    "return UNAUTHORISED when we have the wrong affinity group" in {
      when(mockMicroserviceAuthConnector.authorise(any(), any[Retrieval[~[Option[AffinityGroup], Credentials]]]())(any(), any()))
        .thenReturn(invalidAgentAffinity)

      val response: Result = await(mockAuthConnector.authorisedWithAffinityGroupAndCredentials(registrationAction).apply(fakeRequest))

      status(response) shouldBe 403
    }

    "return UNAUTHORISED when we have no affinity group" in {
      when(mockMicroserviceAuthConnector.authorise(any(), any[Retrieval[~[Option[AffinityGroup], Credentials]]]())(any(), any()))
        .thenReturn(noAffinity)

      val response: Result = await(mockAuthConnector.authorisedWithAffinityGroupAndCredentials(registrationAction).apply(fakeRequest))

      status(response) shouldBe UNAUTHORIZED
    }

    "return the same error when auth throws an error" in {
      when(mockMicroserviceAuthConnector.authorise(any(), any[Retrieval[~[Option[AffinityGroup], Credentials]]]())(any(), any()))
        .thenReturn(Future.failed(new Exception("unexpected error")))

      val thrown = intercept[Exception] {
        await(mockAuthConnector.authorisedWithAffinityGroupAndCredentials(registrationAction).apply(fakeRequest))
      }

      thrown.getMessage shouldBe "unexpected error"
    }
  }

  "overseasAgentAuth" should {
    val fakeRequest = FakeRequest()

   /* "return OK when we have the correct affinity group" in {
      when(mockMicroserviceAuthConnector.authorise(any(), any[Retrieval[~[Enrolments, ~[Option[AffinityGroup], Credentials]]]]())(any(), any()))
        .thenReturn(validAgentAffinity)

      val response: Result = await(mockAuthConnector.overseasAgentAuth(overseasAgentAction).apply(fakeRequest))

      status(response) shouldBe OK
    }*/

  /*  "return UNAUTHORISED when we have the wrong affinity group" in {
      when(mockMicroserviceAuthConnector.authorise(any(), any[Retrieval[~[Enrolments, ~[Option[AffinityGroup], Credentials]]]]())(any(), any()))
        .thenReturn(agentWithoutAffinityandEnrolments)

      val response: Result = await(mockAuthConnector.overseasAgentAuth(overseasAgentAction).apply(fakeRequest))

      status(response) shouldBe 403
    }*/

   /* "return UNAUTHORISED when we have no affinity group" in {
      when(mockMicroserviceAuthConnector.authorise(any(), any[Retrieval[~[Option[AffinityGroup], Credentials]]]())(any(), any()))
        .thenReturn(noAffinity)

      val response: Result = await(mockAuthConnector.overseasAgentAuth(overseasAgentAction).apply(fakeRequest))

      status(response) shouldBe UNAUTHORIZED
    }

    "return the same error when auth throws an error" in {
      when(mockMicroserviceAuthConnector.authorise(any(), any[Retrieval[~[Option[AffinityGroup], Credentials]]]())(any(), any()))
        .thenReturn(Future.failed(new Exception("unexpected error")))

      val thrown = intercept[Exception] {
        await(mockAuthConnector.overseasAgentAuth(overseasAgentAction).apply(fakeRequest))
      }

      thrown.getMessage shouldBe "unexpected error"
    }*/
  }

}
