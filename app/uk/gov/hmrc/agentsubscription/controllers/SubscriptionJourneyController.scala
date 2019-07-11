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

import java.util.UUID

import com.google.inject.Inject
import com.kenshoo.play.metrics.Metrics
import play.api.libs.json.{ JsError, JsSuccess, JsValue }
import play.api.mvc.{ Action, AnyContent, Result, Results }
import uk.gov.hmrc.agentsubscription.auth.AuthActions
import uk.gov.hmrc.agentsubscription.connectors.MicroserviceAuthConnector
import uk.gov.hmrc.agentsubscription.repository.SubscriptionJourneyRepository
import uk.gov.hmrc.play.microservice.controller.BaseController
import play.api.libs.json.Json.toJson
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscription.model.InternalId
import uk.gov.hmrc.agentsubscription.model.subscriptionJourney.SubscriptionJourneyRecord

import scala.concurrent.Future
import scala.util.{ Failure, Success, Try }

class SubscriptionJourneyController @Inject() (implicit
  metrics: Metrics,
  microserviceAuthConnector: MicroserviceAuthConnector,
  subscriptionJourneyRepository: SubscriptionJourneyRepository)
  extends AuthActions(metrics, microserviceAuthConnector) with BaseController {

  private def localWithJsonBody(f: SubscriptionJourneyRecord => Future[Result], request: JsValue): Future[Result] =
    Try(request.validate[SubscriptionJourneyRecord]) match {
      case Success(JsSuccess(payload, _)) => f(payload)
      case Success(JsError(errs)) => Future successful BadRequest(s"Invalid payload: $errs")
      case Failure(e) => Future successful BadRequest(s"could not parse body due to ${e.getMessage}")
    }

  def findByPrimaryId(internalId: InternalId): Action[AnyContent] = authorisedWithAgentAffinity { implicit request =>
    subscriptionJourneyRepository.findByPrimaryId(internalId).map {
      case Some(record) => Ok(toJson(record))
      case None => NotFound("journey record not found for this primary internal id")
    }
  }

  def findByMappedId(internalId: InternalId): Action[AnyContent] = authorisedWithAgentAffinity { implicit request =>
    subscriptionJourneyRepository.findByMappedId(internalId).map {
      case Some(record) => Ok(toJson(record))
      case None => NotFound("journey record not found for this mapped internal id")
    }
  }

  def findByUtr(utr: Utr): Action[AnyContent] = authorisedWithAgentAffinity { implicit request =>
    subscriptionJourneyRepository.findByUtr(utr).map {
      case Some(record) => Ok(toJson(record))
      case None => NotFound("journey record not found for this utr")
    }
  }

  def findByContinueId(continueId: UUID): Action[AnyContent] = authorisedWithAgentAffinity { implicit request =>
    subscriptionJourneyRepository.findByContinueId(continueId).map {
      case Some(record) => Ok(toJson(record))
      case None => NotFound("journey record not found for this continue id")
    }
  }

  def createOrUpdate(internalId: InternalId): Action[AnyContent] = authorisedWithAgentAffinity { implicit request =>
    val json: Option[JsValue] = request.body.asJson
    localWithJsonBody(subscriptionJourneyRecord =>
      subscriptionJourneyRepository.upsert(internalId, subscriptionJourneyRecord)
        .map(_ => Ok), json.get)
  }

  def delete(internalId: InternalId): Action[AnyContent] = authorisedWithAgentAffinity { implicit request =>
    subscriptionJourneyRepository.delete(internalId).map(_ => Ok)
  }

}
