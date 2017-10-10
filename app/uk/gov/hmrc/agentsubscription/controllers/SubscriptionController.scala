/*
 * Copyright 2017 HM Revenue & Customs
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

import play.api.libs.json.Json.toJson
import play.api.mvc._
import uk.gov.hmrc.agentsubscription.auth.AuthActions
import uk.gov.hmrc.agentsubscription.connectors.AuthConnector
import uk.gov.hmrc.agentsubscription.model.{SubscriptionRequest, SubscriptionResponse}
import uk.gov.hmrc.agentsubscription.service.SubscriptionService
import uk.gov.hmrc.play.microservice.controller.BaseController

import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails
import scala.concurrent.Future
import uk.gov.hmrc.http.Upstream4xxResponse

@Singleton
class SubscriptionController @Inject()(subscriptionService: SubscriptionService,
                                       override val authConnector: AuthConnector) extends BaseController with AuthActions {
  private val parseToSubscriptionRequest = parse.json[SubscriptionRequest]

  def createSubscription: Action[SubscriptionRequest] = withAgentAffinityGroupAndEnrolments.async(parseToSubscriptionRequest) { implicit request =>
    if (request.enrolments.isEmpty) {
      subscriptionService.subscribeAgentToMtd(request.body).map {
        case Some(a) => Created(toJson(SubscriptionResponse(a)))
        case None => Forbidden
      }.recover {
        case e: Upstream4xxResponse if e.upstreamResponseCode == CONFLICT => Conflict
      }
    } else {
      Future successful Forbidden
    }
  }
}
