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
import uk.gov.hmrc.agentsubscription.model.InternalId
import uk.gov.hmrc.agentsubscription.model.subscriptionJourney.SubscriptionJourneyRecord
import uk.gov.hmrc.agentsubscription.repository.SubscriptionJourneyRepository
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.{ ExecutionContext, Future }

class SubscriptionJourneyController @Inject() (implicit
  subscriptionJourneyRepository: SubscriptionJourneyRepository,
  ec: ExecutionContext)
  extends BaseController {

  def findByPrimaryId(internalId: InternalId): Action[AnyContent] = Action.async { implicit request =>
    subscriptionJourneyRepository.findByPrimaryId(internalId).map {
      case Some(record) => Ok(toJson(record))
      case None => NoContent
    }
  }

  def findByMappedId(internalId: InternalId): Action[AnyContent] = Action.async { implicit request =>
    subscriptionJourneyRepository.findByMappedId(internalId).map {
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

  def createOrUpdate(internalId: InternalId): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[SubscriptionJourneyRecord] {
      journeyRecord =>
        {
          val mappedInternalIds = journeyRecord.userMappings.map(_.internalId)
          if (journeyRecord.internalId != internalId) {
            Future.successful(BadRequest("Internal ids in request URL and body do not match"))
          } else if (mappedInternalIds.distinct.size != mappedInternalIds.size) {
            Future.successful(BadRequest("Duplicate mapped internal ids in request body"))
          } else {
            val updatedRecord = journeyRecord.copy(lastModifiedDate = Some(LocalDateTime.now(ZoneOffset.UTC)))
            subscriptionJourneyRepository.upsert(internalId, updatedRecord).map(_ => NoContent)
          }
        }
    }
  }

  def delete(internalId: InternalId): Action[AnyContent] = Action.async { implicit request =>
    subscriptionJourneyRepository.delete(internalId).map(_ => NoContent)
  }

}
