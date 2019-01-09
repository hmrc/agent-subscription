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

package uk.gov.hmrc.agentsubscription.support

import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.auth.core.retrieve.{ Credentials, ~ }
import uk.gov.hmrc.auth.core.{ AffinityGroup, Enrolment, EnrolmentIdentifier, Enrolments }

import scala.concurrent.Future

trait AuthData {

  val arn = Arn("arn1")

  val agentEnrolment = Set(
    Enrolment("HMRC-AS-AGENT", Seq(EnrolmentIdentifier("AgentReferenceNumber", arn.value)), state = "Activated",
      delegatedAuthRule = None))

  val agentAffinityWithCredentialsAndGroupId: Future[~[~[Option[AffinityGroup], Credentials], Option[String]]] = {
    val retrievals = new ~(new ~(Some(AffinityGroup.Agent), Credentials("providerId", "providerType")), Some("groupId"))
    Future.successful(retrievals)
  }

  val agentAffinityWithCredentials: Future[~[Option[AffinityGroup], Credentials]] = {
    val retrievals = new ~(Some(AffinityGroup.Agent), Credentials("providerId", "providerType"))
    Future.successful(retrievals)
  }

  val agentIncorrectAffinity: Future[~[~[Option[AffinityGroup], Credentials], Option[String]]] = {
    val retrievals = new ~(new ~(Some(AffinityGroup.Individual), Credentials("providerId", "providerType")), Some("groupId"))
    Future.successful(retrievals)
  }

  val neitherHaveAffinityOrEnrolment: Future[~[~[Option[AffinityGroup], Credentials], Option[String]]] = {
    val retrievals = new ~(new ~(None, Credentials("providerId", "providerType")), Some("groupId"))
    Future.successful(retrievals)
  }

  val failedStubForAgent = Future.failed(new Exception("oh no !"))

  val validAgentAffinity: Future[~[Option[AffinityGroup], Credentials]] =
    Future successful new ~[Option[AffinityGroup], Credentials](Some(AffinityGroup.Agent), Credentials("credId", "credType"))
  val invalidAgentAffinity: Future[~[Option[AffinityGroup], Credentials]] =
    Future successful new ~[Option[AffinityGroup], Credentials](Some(AffinityGroup.Individual), Credentials("credId", "credType"))
  val noAffinity: Future[~[Option[AffinityGroup], Credentials]] =
    Future successful new ~[Option[AffinityGroup], Credentials](None, Credentials("credId", "credType"))

}
