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

package uk.gov.hmrc.agentsubscription.controllers

import com.kenshoo.play.metrics.Metrics
import javax.inject._
import play.api.libs.json.Json.toJson
import play.api.libs.json.{ JsError, JsSuccess }
import play.api.mvc.{ Action, AnyContent }
import uk.gov.hmrc.agentsubscription.auth.AuthActions
import uk.gov.hmrc.agentsubscription.connectors.MicroserviceAuthConnector
import uk.gov.hmrc.agentsubscription.model.{ SubscriptionRequest, SubscriptionResponse, UpdateSubscriptionRequest }
import uk.gov.hmrc.agentsubscription.service.{ EnrolmentAlreadyAllocated, SubscriptionService }
import uk.gov.hmrc.agentsubscription.utils.toFuture
import uk.gov.hmrc.http.Upstream5xxResponse
import uk.gov.hmrc.play.microservice.controller.BaseController

@Singleton
class SubscriptionController @Inject() (subscriptionService: SubscriptionService)(implicit
  metrics: Metrics,
  microserviceAuthConnector: MicroserviceAuthConnector)
  extends AuthActions(metrics, microserviceAuthConnector) with BaseController {

  def createSubscription: Action[AnyContent] = authorisedWithAffinityGroup { implicit request => implicit authIds =>
    request.body.asJson.map(_.validate[SubscriptionRequest]) match {
      case Some(JsSuccess(subscriptionRequest, _)) ⇒
        subscriptionService.createSubscription(subscriptionRequest, authIds).map {
          case Some(a) => Created(toJson(SubscriptionResponse(a)))
          case None => Forbidden
        }.recover {
          case _: EnrolmentAlreadyAllocated => Conflict
          case _: IllegalStateException | _: Upstream5xxResponse => InternalServerError
        }

      case Some(JsError(_)) ⇒
        BadRequest("Could not parse SubscriptionRequest JSON from the request")

      case None ⇒
        BadRequest("No SubscriptionRequest JSON found in the request body")
    }
  }

  def updateSubscription: Action[AnyContent] = authorisedWithAffinityGroup { implicit request => implicit authIds =>
    request.body.asJson.map(_.validate[UpdateSubscriptionRequest]) match {
      case Some(JsSuccess(updateSubscriptionRequest, _)) ⇒
        subscriptionService.updateSubscription(updateSubscriptionRequest, authIds).map {
          case Some(arn) => Ok(toJson(SubscriptionResponse(arn)))
          case None => Forbidden
        }.recover {
          case _: EnrolmentAlreadyAllocated => Conflict
          case _: IllegalStateException | _: Upstream5xxResponse => InternalServerError
        }

      case Some(JsError(_)) ⇒
        BadRequest("Could not parse updateSubscriptionRequest JSON from the request")

      case None ⇒
        BadRequest("No updateSubscriptionRequest JSON found in the request body")
    }

  }
}
