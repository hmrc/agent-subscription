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

import javax.inject._
import com.kenshoo.play.metrics.Metrics
import play.api.libs.json.Json.toJson
import uk.gov.hmrc.agentsubscription.connectors.{ AuthActions, MicroserviceAuthConnector }
import uk.gov.hmrc.agentsubscription.model.{ SubscriptionRequest, SubscriptionResponse, UpdateSubscriptionRequest }
import uk.gov.hmrc.agentsubscription.service.{ EnrolmentAlreadyAllocated, SubscriptionService }
import uk.gov.hmrc.play.microservice.controller.BaseController
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails
import uk.gov.hmrc.play.HeaderCarrierConverter.fromHeadersAndSession

@Singleton
class SubscriptionController @Inject() (subscriptionService: SubscriptionService)(implicit
  metrics: Metrics,
  microserviceAuthConnector: MicroserviceAuthConnector)
  extends AuthActions(metrics, microserviceAuthConnector) with BaseController {

  def createSubscription = affinityGroupAndEnrolments { implicit request => implicit authIds =>
    implicit val hc = fromHeadersAndSession(request.headers, None)
    withJsonBody[SubscriptionRequest] { subscriptionRequest =>
      subscriptionService.subscribeAgentToMtd(subscriptionRequest, authIds).map {
        case Some(a) => Created(toJson(SubscriptionResponse(a)))
        case None => Forbidden
      }.recover {
        case _: EnrolmentAlreadyAllocated => Conflict
        case _: IllegalStateException => InternalServerError
      }
    }
  }

  def updateSubscription = affinityGroupAndEnrolments { implicit request => implicit authIds =>
    implicit val hc = fromHeadersAndSession(request.headers, None)
    withJsonBody[UpdateSubscriptionRequest] { subscriptionRequest =>
      subscriptionService.updateSubscription(subscriptionRequest, authIds).map {
        case Some(arn) => Created(toJson(SubscriptionResponse(arn)))
        case None => Forbidden
      }.recover {
        case _: EnrolmentAlreadyAllocated => Conflict
        case _: IllegalStateException => InternalServerError
      }
    }
  }
}
