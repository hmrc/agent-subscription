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

import com.codahale.metrics.MetricRegistry
import com.kenshoo.play.metrics.Metrics
import javax.inject.{ Inject, Singleton }
import play.api.libs.json.Json.toJson
import play.api.libs.json.{ JsValue, Json, OFormat, Writes }
import play.api.mvc.Results.{ Forbidden, Unauthorized }
import play.api.mvc.{ AnyContent, Result, _ }
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentsubscription.auth.AuthActions._
import uk.gov.hmrc.agentsubscription.connectors.MicroserviceAuthConnector
import uk.gov.hmrc.agentsubscription.utils.{ WithMdcExecutionContext, toFuture }
import uk.gov.hmrc.auth.core
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.Retrievals.{ affinityGroup, credentials, groupIdentifier, allEnrolments }
import uk.gov.hmrc.auth.core.retrieve.{ Credentials, ~ }
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future

@Singleton
class AuthActions @Inject() (metrics: Metrics, microserviceAuthConnector: MicroserviceAuthConnector)
  extends HttpAPIMonitor with AuthorisedFunctions with BaseController with WithMdcExecutionContext {

  override def authConnector: core.AuthConnector = microserviceAuthConnector

  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  private val AuthProvider: AuthProviders = AuthProviders(GovernmentGateway)
  private val agentEnrol = "HMRC-AS-AGENT"
  private val agentEnrolId = "AgentReferenceNumber"
  private val isAnAgent = true

  def authorisedWithAffinityGroup(action: SubscriptionAuthAction): Action[JsValue] = Action.async(parse.json) { implicit request =>
    authorised(AuthProvider)
      .retrieve(affinityGroup and credentials and groupIdentifier) {
        case Some(affinityG) ~ Credentials(providerId, _) ~ Some(groupId) =>
          if (isAgent(affinityG)) {
            action(request)(AuthIds(providerId, groupId))
          } else {
            NotAnAgent
          }

        case _ => GenericUnauthorized

      } recover {
        handleFailure()
      }
  }

  def overseasAgentAuth(action: OverseasAuthAction): Action[AnyContent] = Action.async { implicit request =>
    authorised(AuthProvider)
      .retrieve(allEnrolments and affinityGroup and credentials and groupIdentifier) {
        case enrolments ~ Some(affinityG) ~ Credentials(providerId, _) ~ Some(groupId) =>
          if (!isAgent(affinityG))
            NotAnAgent
          else if (enrolments.enrolments.nonEmpty)
            AgentCannotSubscribe
          else
            action(request)(AuthIds(providerId, groupId))
        case _ => GenericUnauthorized
      } recover {
        handleFailure()
      }
  }

  def authorisedWithAffinityGroupAndCredentials(action: RegistrationAuthAction): Action[AnyContent] = Action.async { implicit request =>
    authorised(AuthProvider)
      .retrieve(affinityGroup and credentials) {
        case Some(affinityG) ~ Credentials(providerId, providerType) =>
          if (isAgent(affinityG)) {
            action(request)(Provider(providerId, providerType))
          } else {
            NotAnAgent
          }

        case _ => GenericUnauthorized

      } recover {
        handleFailure()
      }
  }

  def handleFailure()(implicit request: Request[_]): PartialFunction[Throwable, Result] = {
    case _: NoActiveSession ⇒ GenericUnauthorized
    case _: UnsupportedAuthProvider ⇒ GenericUnauthorized
  }

  private def isAgent(group: AffinityGroup): Boolean = group.toString.contains("Agent")
}

object AuthActions {

  type SubscriptionAuthAction = Request[JsValue] => AuthIds => Future[Result]
  type OverseasAuthAction = Request[AnyContent] => AuthIds => Future[Result]
  type RegistrationAuthAction = Request[AnyContent] => Provider => Future[Result]

  case class Provider(providerId: String, providerType: String)

  case class AuthIds(userId: String, groupId: String)

  object AuthIds {
    implicit val authIdsFormat: OFormat[AuthIds] = Json.format[AuthIds]
  }

  case class ErrorBody(code: String, message: String)

  implicit val errorBodyWrites: Writes[ErrorBody] = new Writes[ErrorBody] {
    override def writes(body: ErrorBody): JsValue = Json.obj("code" -> body.code, "message" -> body.message)
  }

  val GenericUnauthorized: Result = Unauthorized(toJson(ErrorBody("UNAUTHORIZED", "Bearer token is missing or not authorized.")))

  val AgentCannotSubscribe: Result = Forbidden(toJson(ErrorBody("AGENT_CAN_NOT_SUBSCRIBE", "The user either is not an Agent or already has enrolments")))

  val NotAnAgent: Result = Forbidden(toJson(ErrorBody("USER_NOT_AN_AGENT", "The user is not an Agent")))
}
