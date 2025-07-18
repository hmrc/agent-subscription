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

package uk.gov.hmrc.agentsubscription.controllers

import javax.inject._
import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.ControllerComponents
import uk.gov.hmrc.agentsubscription.auth.AuthActions
import uk.gov.hmrc.agentsubscription.model.SubscriptionRequest
import uk.gov.hmrc.agentsubscription.model.SubscriptionResponse
import uk.gov.hmrc.agentsubscription.model.UpdateSubscriptionRequest
import uk.gov.hmrc.agentsubscription.service.EnrolmentAlreadyAllocated
import uk.gov.hmrc.agentsubscription.service.SubscriptionService
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.ExecutionContext

@Singleton
class SubscriptionController @Inject() (
  subscriptionService: SubscriptionService,
  authActions: AuthActions,
  cc: ControllerComponents
)(implicit ec: ExecutionContext)
extends BackendController(cc) {

  import authActions._

  def createSubscription: Action[JsValue] = authorisedWithAffinityGroup { implicit request => implicit authIds =>
    withJsonBody[SubscriptionRequest] { subscriptionRequest =>
      subscriptionService
        .createSubscription(subscriptionRequest, authIds)
        .map {
          case Some(a) => Created(toJson(SubscriptionResponse(a)))
          case None => Forbidden(s"No business partner record found for ${subscriptionRequest.utr}")
        }
        .recover {
          case _: EnrolmentAlreadyAllocated => Conflict
          case _: IllegalStateException | _: UpstreamErrorResponse => InternalServerError
        }
    }
  }

  def updateSubscription: Action[JsValue] = authorisedWithAffinityGroup { implicit request => implicit authIds =>
    withJsonBody[UpdateSubscriptionRequest] { updateSubscriptionRequest =>
      subscriptionService
        .updateSubscription(updateSubscriptionRequest, authIds)
        .map {
          case Some(arn) => Ok(toJson(SubscriptionResponse(arn)))
          case None => Forbidden("No business partner record found for ${subscriptionRequest.utr}")
        }
        .recover {
          case _: EnrolmentAlreadyAllocated => Conflict
          case _: IllegalStateException | _: UpstreamErrorResponse => InternalServerError
        }
    }
  }

  def createOverseasSubscription: Action[AnyContent] = overseasAgentAuth { implicit request => implicit authIds =>
    subscriptionService
      .createOverseasSubscription(authIds)
      .map {
        case Some(arn) => Created(toJson(SubscriptionResponse(arn)))
        case None => Forbidden
      }
      .recover {
        case _: EnrolmentAlreadyAllocated => Conflict
        case _: IllegalStateException | _: UpstreamErrorResponse => InternalServerError
      }

  }

}
