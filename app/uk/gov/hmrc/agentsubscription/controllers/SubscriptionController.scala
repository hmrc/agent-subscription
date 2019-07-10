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

package uk.gov.hmrc.agentsubscription.controllers

import com.kenshoo.play.metrics.Metrics
import javax.inject._
import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson
import play.api.mvc.{ Action, AnyContent }
import uk.gov.hmrc.agentsubscription.auth.AuthActions
import uk.gov.hmrc.agentsubscription.connectors.MicroserviceAuthConnector
import uk.gov.hmrc.agentsubscription.model.{ OverseasAgencyDetails, SubscriptionRequest, SubscriptionResponse, UpdateSubscriptionRequest }
import uk.gov.hmrc.agentsubscription.service.{ EnrolmentAlreadyAllocated, SubscriptionService }
import uk.gov.hmrc.http.Upstream5xxResponse
import uk.gov.hmrc.play.bootstrap.controller.BaseController

@Singleton
class SubscriptionController @Inject() (subscriptionService: SubscriptionService)(implicit
  metrics: Metrics,
  microserviceAuthConnector: MicroserviceAuthConnector)
  extends AuthActions(metrics, microserviceAuthConnector) with BaseController {

  def createSubscription: Action[JsValue] = authorisedWithAffinityGroup { implicit request => implicit authIds =>
    withJsonBody[SubscriptionRequest] { subscriptionRequest =>
      subscriptionService.createSubscription(subscriptionRequest, authIds).map {
        case Some(a) => Created(toJson(SubscriptionResponse(a)))
        case None => Forbidden
      }.recover {
        case _: EnrolmentAlreadyAllocated => Conflict
        case _: IllegalStateException | _: Upstream5xxResponse => InternalServerError
      }
    }
  }

  def updateSubscription: Action[JsValue] = authorisedWithAffinityGroup { implicit request => implicit authIds =>
    withJsonBody[UpdateSubscriptionRequest] { updateSubscriptionRequest =>
      subscriptionService.updateSubscription(updateSubscriptionRequest, authIds).map {
        case Some(arn) => Ok(toJson(SubscriptionResponse(arn)))
        case None => Forbidden
      }.recover {
        case _: EnrolmentAlreadyAllocated => Conflict
        case _: IllegalStateException | _: Upstream5xxResponse => InternalServerError
      }
    }
  }

  def createOverseasSubscription: Action[AnyContent] = overseasAgentAuth { implicit request => implicit authIds =>
    subscriptionService.createOverseasSubscription(authIds).map {
      case Some(arn) => Created(toJson(SubscriptionResponse(arn)))
      case None => Forbidden
    }.recover {
      case _: EnrolmentAlreadyAllocated => Conflict
      case _: IllegalStateException | _: Upstream5xxResponse => InternalServerError
    }

  }
}
