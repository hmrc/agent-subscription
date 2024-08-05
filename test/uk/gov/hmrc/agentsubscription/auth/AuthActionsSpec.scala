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

package uk.gov.hmrc.agentsubscription.auth

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import org.mockito.stubbing.OngoingStubbing
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Results.Ok
import play.api.mvc.{AnyContent, ControllerComponents, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentsubscription.auth.AuthActions.{OverseasAuthAction, RegistrationAuthAction, SubscriptionAuthAction}
import uk.gov.hmrc.agentsubscription.support.{AuthData, UnitSpec}
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, ~}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class AuthActionsSpec(implicit val ec: ExecutionContext)
    extends UnitSpec with MockitoSugar with BeforeAndAfterEach with AuthData {
  import uk.gov.hmrc.auth.core.{Enrolment, authorise, _}

  val mockPlayAuthConnector = mock[PlayAuthConnector]
  val mockCC = mock[ControllerComponents]
  val mockAuthConnector = mock[AuthConnector]
  val mockAuthActions: AuthActions = new AuthActions(mockCC, mockAuthConnector)

  val subscriptionAction: SubscriptionAuthAction = { request => authIds => Future successful Ok }
  val registrationAction: RegistrationAuthAction = { request => provider => Future successful Ok }
  val overseasAgentAction: OverseasAuthAction = { request => provider => Future successful Ok }
  val agentAction: Request[AnyContent] => Future[Result] = { request => Future successful Ok }

  private def agentAuthStub(
    returnValue: Future[~[~[Option[AffinityGroup], Option[Credentials]], Option[String]]]
  ): OngoingStubbing[Future[Option[AffinityGroup] ~ Option[Credentials] ~ Option[String]]] =
    when(
      mockPlayAuthConnector
        .authorise(
          any[authorise.Predicate](),
          any[Retrieval[~[~[Option[AffinityGroup], Option[Credentials]], Option[String]]]]()
        )(any[HeaderCarrier](), any[ExecutionContext]())
    )
      .thenReturn(returnValue)

  private def agentAuthStubWithAffinity(returnValue: Future[Option[AffinityGroup]]) =
    when(
      mockPlayAuthConnector
        .authorise(any[authorise.Predicate](), any[Retrieval[Option[AffinityGroup]]]())(
          any[HeaderCarrier](),
          any[ExecutionContext]()
        )
    )
      .thenReturn(returnValue)

  override def beforeEach(): Unit = reset(mockPlayAuthConnector)

  val fakeRequest = FakeRequest().withBody[JsValue](Json.parse("""{}"""))

  "authorisedWithAffinityGroup" should {
    "return OK for an Agent with Agent affinity group" in {
      agentAuthStub(agentAffinityWithCredentialsAndGroupId)

      val response: Result = await(mockAuthActions.authorisedWithAffinityGroup(subscriptionAction).apply(fakeRequest))

      status(response) shouldBe OK
    }

    "return UNAUTHORISED when the user does not belong to Agent affinity group" in {
      agentAuthStub(agentIncorrectAffinity)

      val response: Result = await(mockAuthActions.authorisedWithAffinityGroup(subscriptionAction).apply(fakeRequest))

      status(response) shouldBe 403
    }

    "return UNAUTHORISED when auth fails to return an AffinityGroup or Enrolments" in {
      agentAuthStub(neitherHaveAffinityOrEnrolment)

      val response: Result = await(mockAuthActions.authorisedWithAffinityGroup(subscriptionAction).apply(fakeRequest))

      status(response) shouldBe UNAUTHORIZED
    }

    "return the same error when auth throws an error" in {
      agentAuthStub(failedStubForAgent)

      val thrown = intercept[Exception] {
        await(mockAuthActions.authorisedWithAffinityGroup(subscriptionAction).apply(fakeRequest))
      }

      thrown.getMessage shouldBe "oh no !"
    }
  }

  "authorisedWithAgentAffinity" should {
    "return OK for an Agent with Agent affinity group" in {
      agentAuthStubWithAffinity(agentAffinity)

      val response: Result = await(mockAuthActions.authorisedWithAgentAffinity(agentAction).apply(FakeRequest()))

      status(response) shouldBe OK
    }

    "return FORBIDDEN when the user does not belong to Agent affinity group" in {
      agentAuthStubWithAffinity(individualAffinity)

      val response: Result = await(mockAuthActions.authorisedWithAgentAffinity(agentAction).apply(FakeRequest()))

      status(response) shouldBe 403
    }

    "return UNAUTHORISED when auth fails to return an AffinityGroup" in {
      agentAuthStubWithAffinity(Future.successful(None))

      val response: Result = await(mockAuthActions.authorisedWithAgentAffinity(agentAction).apply(FakeRequest()))

      status(response) shouldBe UNAUTHORIZED
    }

    "return the same error when auth throws an error" in {
      agentAuthStubWithAffinity(failedStubForAgent)

      val thrown = intercept[Exception] {
        await(mockAuthActions.authorisedWithAgentAffinity(agentAction).apply(FakeRequest()))
      }

      thrown.getMessage shouldBe "oh no !"
    }
  }

  "authorisedWithAffinityGroupAndCredentials" should {

    val fakeRequest = FakeRequest()

    "return OK when we have the correct affinity group" in {
      when(
        mockPlayAuthConnector.authorise(
          any[Predicate](),
          any[Retrieval[~[Option[AffinityGroup], Option[Credentials]]]]()
        )(any[HeaderCarrier](), any[ExecutionContext]())
      )
        .thenReturn(validAgentAffinity)

      val response: Result =
        await(mockAuthActions.authorisedWithAffinityGroupAndCredentials(registrationAction).apply(fakeRequest))

      status(response) shouldBe OK
    }

    "return UNAUTHORISED when we have the wrong affinity group" in {
      when(
        mockPlayAuthConnector.authorise(
          any[Predicate](),
          any[Retrieval[~[Option[AffinityGroup], Option[Credentials]]]]()
        )(any[HeaderCarrier](), any[ExecutionContext]())
      )
        .thenReturn(invalidAgentAffinity)

      val response: Result =
        await(mockAuthActions.authorisedWithAffinityGroupAndCredentials(registrationAction).apply(fakeRequest))

      status(response) shouldBe 403
    }

    "return UNAUTHORISED when we have no affinity group" in {
      when(
        mockPlayAuthConnector.authorise(
          any[Predicate](),
          any[Retrieval[~[Option[AffinityGroup], Option[Credentials]]]]()
        )(any[HeaderCarrier](), any[ExecutionContext]())
      )
        .thenReturn(noAffinity)

      val response: Result =
        await(mockAuthActions.authorisedWithAffinityGroupAndCredentials(registrationAction).apply(fakeRequest))

      status(response) shouldBe UNAUTHORIZED
    }

    "return the same error when auth throws an error" in {
      when(
        mockPlayAuthConnector.authorise(any[Predicate](), any[Retrieval[~[Option[AffinityGroup], Credentials]]]())(
          any[HeaderCarrier](),
          any[ExecutionContext]()
        )
      )
        .thenReturn(Future.failed(new Exception("unexpected error")))

      val thrown = intercept[Exception] {
        await(mockAuthActions.authorisedWithAffinityGroupAndCredentials(registrationAction).apply(fakeRequest))
      }

      thrown.getMessage shouldBe "unexpected error"
    }
  }

  "overseasAgentAuth" should {
    val fakeRequest = FakeRequest()

    "return OK when the user has Agent affinity and no enrolments" in {
      mockAuthRetrieval(affinityGroup = Some(AffinityGroup.Agent), enrolments = Enrolments(Set.empty))

      val response: Result = await(mockAuthActions.overseasAgentAuth(overseasAgentAction).apply(fakeRequest))

      status(response) shouldBe OK
    }

    "return UNAUTHORISED when user does not have Agent affinity" in {
      mockAuthRetrieval(affinityGroup = Some(AffinityGroup.Individual), enrolments = Enrolments(Set.empty))

      val response: Result = await(mockAuthActions.overseasAgentAuth(overseasAgentAction).apply(fakeRequest))

      status(response) shouldBe 403
    }

    "return UNAUTHORISED when we have no affinity group" in {
      mockAuthRetrieval(affinityGroup = None, enrolments = Enrolments(Set.empty))

      val response: Result = await(mockAuthActions.overseasAgentAuth(overseasAgentAction).apply(fakeRequest))

      status(response) shouldBe UNAUTHORIZED
    }

    "return UNAUTHORISED when Agent has a HMRC-AS-AGENT enrolment already" in {
      mockAuthRetrieval(affinityGroup = Some(AffinityGroup.Agent), enrolments = Enrolments(agentEnrolment))

      val response: Result = await(mockAuthActions.overseasAgentAuth(overseasAgentAction).apply(fakeRequest))

      status(response) shouldBe FORBIDDEN
    }

    "return UNAUTHORISED when Agent has some other enrolment already" in {
      val someOtherEnrolments = Set(
        Enrolment(
          "SOME-ENROLMENT",
          Seq(EnrolmentIdentifier("SomeId", "SomeValue")),
          state = "Activated",
          delegatedAuthRule = None
        )
      )

      mockAuthRetrieval(affinityGroup = Some(AffinityGroup.Agent), enrolments = Enrolments(someOtherEnrolments))

      val response: Result = await(mockAuthActions.overseasAgentAuth(overseasAgentAction).apply(fakeRequest))

      status(response) shouldBe FORBIDDEN
    }

    "return the same error when auth throws an error" in {
      when(
        mockPlayAuthConnector.authorise(any[Predicate](), any[Retrieval[~[Option[AffinityGroup], Credentials]]]())(
          any[HeaderCarrier](),
          any[ExecutionContext]()
        )
      )
        .thenReturn(Future.failed(new Exception("unexpected error")))

      val thrown = intercept[Exception] {
        await(mockAuthActions.overseasAgentAuth(overseasAgentAction).apply(fakeRequest))
      }

      thrown.getMessage shouldBe "unexpected error"
    }

    def mockAuthRetrieval(affinityGroup: Option[AffinityGroup], enrolments: Enrolments) = {
      val retrievedCredentials = Credentials("credId", "credType")
      val retrievedGroupId = Some("groupId")

      type Retrievals = ~[~[~[Enrolments, Option[AffinityGroup]], Credentials], Option[String]]

      val retrievalResponse: Future[Retrievals] =
        Future successful new ~(new ~(new ~(enrolments, affinityGroup), retrievedCredentials), retrievedGroupId)

      when(
        mockPlayAuthConnector
          .authorise(any[Predicate](), any[Retrieval[Retrievals]]())(any[HeaderCarrier](), any[ExecutionContext]())
      )
        .thenReturn(retrievalResponse)
    }
  }

}
