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

import java.time.{ LocalDateTime, ZoneOffset }

import com.google.inject.Inject
import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson
import play.api.mvc.{ Action, AnyContent }
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscription.model.AuthProviderId
import uk.gov.hmrc.agentsubscription.model.subscriptionJourney.SubscriptionJourneyRecord
import uk.gov.hmrc.agentsubscription.repository.SubscriptionJourneyRepository
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.{ ExecutionContext, Future }

class SubscriptionJourneyController @Inject() (implicit
  subscriptionJourneyRepository: SubscriptionJourneyRepository,
  ec: ExecutionContext)
  extends BaseController {

  def findByAuthId(authProviderId: AuthProviderId): Action[AnyContent] = Action.async { implicit request =>
    subscriptionJourneyRepository.findByAuthId(authProviderId).map {
      case Some(record) => Ok(toJson(record))
      case None => NoContent
    }
  }

  def findByUtr(utr: Utr): Action[AnyContent] = Action.async { implicit request =>
    subscriptionJourneyRepository.findByUtr(utr).map {
      case Some(record) => Ok(toJson(record))
      case None => NoContent
    }
  }

  def findByContinueId(continueId: String): Action[AnyContent] = Action.async { implicit request =>
    subscriptionJourneyRepository.findByContinueId(continueId).map {
      case Some(record) => Ok(toJson(record))
      case None => NoContent
    }
  }

  def createOrUpdate(authProviderId: AuthProviderId): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[SubscriptionJourneyRecord] {
      journeyRecord =>
        {
          val mappedAuthIds = journeyRecord.userMappings.map(_.authProviderId)
          if (journeyRecord.authProviderId != authProviderId) {
            Future.successful(BadRequest("Auth ids in request URL and body do not match"))
          } else if (mappedAuthIds.distinct.size != mappedAuthIds.size) {
            Future.successful(BadRequest("Duplicate mapped auth ids in request body"))
          } else {
            val updatedRecord = journeyRecord.copy(lastModifiedDate = Some(LocalDateTime.now(ZoneOffset.UTC)))
            subscriptionJourneyRepository.upsert(authProviderId, updatedRecord).map(_ => NoContent)
          }
        }
    }
  }

  //  def delete(authProviderId: AuthProviderId): Action[AnyContent] = Action.async { implicit request =>
  //    subscriptionJourneyRepository.delete(authProviderId).map(_ => NoContent)
  //  }

}
