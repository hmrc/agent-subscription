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

package uk.gov.hmrc.agentsubscription.auth

import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json.toJson
import play.api.libs.json.{JsValue, Json, OFormat, Writes}
import play.api.mvc.Results.{Forbidden, Unauthorized}
import play.api.mvc.{AnyContent, Result, _}
import uk.gov.hmrc.agentsubscription.auth.AuthActions._
import uk.gov.hmrc.agentsubscription.utils.valueOps
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.{affinityGroup, allEnrolments, credentials, groupIdentifier}
import uk.gov.hmrc.auth.core.retrieve.{Credentials, ~}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AuthActions @Inject() (cc: ControllerComponents, val authConnector: AuthConnector)(implicit ec: ExecutionContext)
    extends BackendController(cc) with AuthorisedFunctions {

  private val AuthProvider: AuthProviders = AuthProviders(GovernmentGateway)

  def authorisedWithAffinityGroup(action: SubscriptionAuthAction): Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      authorised(AuthProvider)
        .retrieve(affinityGroup and credentials and groupIdentifier) {
          case Some(affinityG) ~ Some(Credentials(providerId, _)) ~ Some(groupId) =>
            if (isAgent(affinityG)) {
              action(request)(AuthIds(providerId, groupId))
            } else
              NotAnAgent.toFuture

          case _ => GenericUnauthorized.toFuture

        } recover {
        handleFailure()
      }
  }

  def authorisedWithAgentAffinity(action: Request[AnyContent] => Future[Result]): Action[AnyContent] = Action.async {
    implicit request =>
      authorised(AuthProvider).retrieve(affinityGroup) {
        case Some(affinityG) =>
          if (isAgent(affinityG))
            action(request)
          else
            NotAnAgent.toFuture
        case _ => GenericUnauthorized.toFuture
      } recover {
        handleFailure()
      }
  }

  def overseasAgentAuth(action: OverseasAuthAction): Action[AnyContent] = Action.async { implicit request =>
    authorised(AuthProvider)
      .retrieve(allEnrolments and affinityGroup and credentials and groupIdentifier) {
        case enrolments ~ Some(affinityG) ~ Some(Credentials(providerId, _)) ~ Some(groupId) =>
          if (!isAgent(affinityG))
            NotAnAgent.toFuture
          else if (enrolments.enrolments.nonEmpty)
            AgentCannotSubscribe.toFuture
          else
            action(request)(AuthIds(providerId, groupId))
        case _ => GenericUnauthorized.toFuture
      } recover {
      handleFailure()
    }
  }

  def authorisedWithAffinityGroupAndCredentials(action: RegistrationAuthAction): Action[AnyContent] = Action.async {
    implicit request =>
      authorised(AuthProvider)
        .retrieve(affinityGroup and credentials) {
          case Some(affinityG) ~ Some(Credentials(providerId, providerType)) =>
            if (isAgent(affinityG)) {
              action(request)(Provider(providerId, providerType))
            } else
              NotAnAgent.toFuture

          case _ => GenericUnauthorized.toFuture

        } recover {
        handleFailure()
      }
  }

  def handleFailure(): PartialFunction[Throwable, Result] = {
    case _: NoActiveSession         => GenericUnauthorized
    case _: UnsupportedAuthProvider => GenericUnauthorized
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

  val GenericUnauthorized: Result = Unauthorized(
    toJson(ErrorBody("UNAUTHORIZED", "Bearer token is missing or not authorized."))
  )

  val AgentCannotSubscribe: Result = Forbidden(
    toJson(ErrorBody("AGENT_CAN_NOT_SUBSCRIBE", "The user either is not an Agent or already has enrolments"))
  )

  val NotAnAgent: Result = Forbidden(toJson(ErrorBody("USER_NOT_AN_AGENT", "The user is not an Agent")))
}
