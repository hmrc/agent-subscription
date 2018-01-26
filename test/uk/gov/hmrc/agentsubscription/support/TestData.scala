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

package uk.gov.hmrc.agentsubscription.support

import play.api.libs.json.{JsValue, Json}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.auth.core.{AffinityGroup, Enrolment, EnrolmentIdentifier, Enrolments}
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, Retrievals, ~}

import scala.concurrent.Future

trait TestData {

  val arn = Arn("arn1")

  val agentEnrolment = Set(
    Enrolment("HMRC-AS-AGENT", Seq(EnrolmentIdentifier("AgentReferenceNumber", arn.value)), state = "",
      delegatedAuthRule = None)
  )

  val agentAffinityAndEnrolments: Future[~[Option[AffinityGroup], Enrolments]] =
    Future successful new ~[Option[AffinityGroup], Enrolments](Some(AffinityGroup.Agent), Enrolments(agentEnrolment))

  val agentAffinityAndEnrolments1: Future[~[~[~[Option[AffinityGroup], Enrolments], Credentials], Option[String]]] = {
    val r1: ~[Option[AffinityGroup], Enrolments] = ~(Some(AffinityGroup.Agent, Enrolments(agentEnrolment)))
    Future successful new ~[~[~[Option[AffinityGroup], Enrolments], Credentials], Option[String]](((Some(AffinityGroup.Agent), Enrolments(agentEnrolment)), Credentials("credId", "credType")), Some(""))
  }


  val agentNoEnrolments: Future[~[Option[AffinityGroup], Enrolments]] =
    Future successful new ~[Option[AffinityGroup], Enrolments](Some(AffinityGroup.Agent), Enrolments(Set.empty[Enrolment]))
  val agentIncorrectAffinity: Future[~[Option[AffinityGroup], Enrolments]] =
    Future successful new ~[Option[AffinityGroup], Enrolments](Some(AffinityGroup.Individual), Enrolments(agentEnrolment))
  val neitherHaveAffinityOrEnrolment: Future[~[Option[AffinityGroup], Enrolments]] =
    Future successful new ~[Option[AffinityGroup], Enrolments](None, Enrolments(Set.empty[Enrolment]))
  val failedStubForAgent: Future[~[Option[AffinityGroup], Enrolments]] =
    Future failed new NullPointerException


  val validAgentAffinity: Future[~[Option[AffinityGroup], Credentials]] =
    Future successful new ~[Option[AffinityGroup], Credentials](Some(AffinityGroup.Agent), Credentials("credId","credType"))
  val invalidAgentAffinity: Future[~[Option[AffinityGroup], Credentials]] =
    Future successful new ~[Option[AffinityGroup], Credentials](Some(AffinityGroup.Individual), Credentials("credId","credType"))
  val noAffinity: Future[~[Option[AffinityGroup], Credentials]] =
    Future successful new ~[Option[AffinityGroup], Credentials](None, Credentials("credId","credType"))

  val fakeRequestJson: FakeRequest[JsValue] = FakeRequest().withBody(Json.obj("" -> ""))
  val fakeRequestAny: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

}
