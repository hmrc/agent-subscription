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

package uk.gov.hmrc.agentsubscription.connectors

import javax.inject.{Inject, Singleton}

import com.kenshoo.play.metrics.Metrics
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.mvc.{Result, _}
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentsubscription.MicroserviceAuthConnector
import uk.gov.hmrc.agentsubscription.controllers.ErrorResult._
import uk.gov.hmrc.auth.core
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.retrieve.Retrievals.{affinityGroup, allEnrolments, credentials, groupIdentifier}
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, Retrievals, ~}
import uk.gov.hmrc.auth.core.{Enrolment, _}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class Provider(providerId: String, providerType: String)
case class AuthIds(userId: String, groupId: String)

@Singleton
class AuthActions @Inject()(metrics: Metrics, microserviceAuthConnector: MicroserviceAuthConnector)
  extends HttpAPIMonitor with AuthorisedFunctions with BaseController {
  override def authConnector: core.AuthConnector = microserviceAuthConnector

  override val kenshooRegistry = metrics.defaultRegistry
  private type SubscriptionAuthAction = Request[JsValue] => AuthIds => Future[Result]
  private type RegistrationAuthAction = Request[AnyContent] => Provider => Future[Result]

  private val AuthProvider: AuthProviders = AuthProviders(GovernmentGateway)
  private val agentEnrol = "HMRC-AS-AGENT"
  private val agentEnrolId = "AgentReferenceNumber"
  private val isAnAgent = true

  implicit val hc: HeaderCarrier = new HeaderCarrier

  def affinityGroupAndEnrolments(action: SubscriptionAuthAction) = Action.async(parse.json) {
    implicit request =>
      authorised(AuthProvider).retrieve(affinityGroup and allEnrolments and credentials and groupIdentifier) {
        case Some(affinityG) ~ allEnrols ~ Credentials(providerId, _) ~ Some(groupId) =>
          (isAgent(affinityG), extractEnrolmentData(allEnrols.enrolments, agentEnrol, agentEnrolId)) match {
            case (`isAnAgent`, None | Some(_)) => action(request)(AuthIds(providerId, groupId))
            case _ => Future successful GenericUnauthorized
          }
        case _ => Future successful GenericUnauthorized
      } recover {
        case e @ _ => {
          Logger.error("Failed to auth", e)
          GenericUnauthorized
        }
      }
  }

  def affinityGroupAndCredentials(action: RegistrationAuthAction) = Action.async {
    implicit request =>
      authorised(AuthProvider).retrieve(affinityGroup and credentials) {
        case (Some(affinityG) ~ Credentials(providerId, providerType)) => {
          (isAgent(affinityG)) match {
            case `isAnAgent` => action(request)(Provider(providerId, providerType))
            case _ => Future successful GenericUnauthorized
          }
        }
        case _ => Future successful GenericUnauthorized
      } recover {
        case ex: DesConnectorException => {
          Logger.error("DES issue", ex)
          InternalServerError
        }
        case e @ _ => {
          Logger.error("Failed to auth", e)
          GenericUnauthorized
        }
      }
  }

  private def extractEnrolmentData(enrolls: Set[Enrolment], enrolKey: String, enrolId: String): Option[String] =
    enrolls.find(_.key == enrolKey).flatMap(_.getIdentifier(enrolId)).map(_.value)

  private def isAgent(group: AffinityGroup): Boolean = group.toString.contains("Agent")

}
