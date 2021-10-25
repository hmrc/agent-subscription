/*
 * Copyright 2021 HM Revenue & Customs
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
import play.api.Logging
import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson
import play.api.mvc.{ Action, AnyContent, ControllerComponents, Result }
import reactivemongo.core.errors.DatabaseException
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscription.model.AuthProviderId
import uk.gov.hmrc.agentsubscription.model.subscriptionJourney.SubscriptionJourneyRecord
import uk.gov.hmrc.agentsubscription.repository.SubscriptionJourneyRepository
import uk.gov.hmrc.agentsubscription.utils._
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ ExecutionContext, Future }

class SubscriptionJourneyController @Inject() (
  subscriptionJourneyRepository: SubscriptionJourneyRepository,
  cc: ControllerComponents)(implicit ec: ExecutionContext) extends BackendController(cc) with Logging {

  def findByAuthId(authProviderId: AuthProviderId): Action[AnyContent] = Action.async {
    subscriptionJourneyRepository.findByAuthId(authProviderId).map {
      case Some(record) => Ok(toJson(record))
      case None => NoContent
    }
  }

  def findByUtr(utr: Utr): Action[AnyContent] = Action.async {
    subscriptionJourneyRepository.findByUtr(utr).map {
      case Some(record) => Ok(toJson(record))
      case None => NoContent
    }
  }

  def findByContinueId(continueId: String): Action[AnyContent] = Action.async {
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
            subscriptionJourneyRepository.upsert(authProviderId, updatedRecord).map(_ => NoContent) recoverWith {
              case ex: DatabaseException if ex.code.contains(11000) =>
                logger.error(ex.getMessage(), ex)
                handleConflict(journeyRecord)
            }
          }
        }
    }
  }

  // Find the SJR then overwrite the authProviderId, businessDetails and cleanCredsAuthProviderId. This allows the journey to progress when
  // the agent uses a different cred to continue the journey after some days/weeks and during that time some details may have changed e.g. address.
  def handleConflict(sjr: SubscriptionJourneyRecord)(implicit ec: ExecutionContext): Future[Result] = {
    val utr = sjr.businessDetails.utr
    for {
      optExistingSjr <- subscriptionJourneyRepository.findByUtr(utr)
      existingSjr <- optExistingSjr.fold[Future[SubscriptionJourneyRecord]](logUTRError(sjr).toFailure)(_.toFuture)
      updatedSjr = existingSjr.copy(
        authProviderId = sjr.authProviderId,
        businessDetails = sjr.businessDetails,
        cleanCredsAuthProviderId = sjr.cleanCredsAuthProviderId)
      _ <- subscriptionJourneyRepository.updateOnUtr(utr, updatedSjr)
      result <- Ok(toJson(updatedSjr)).toFuture
    } yield result
  }

  def logUTRError(sjr: SubscriptionJourneyRecord) = {
    logger.warn(s"Conflict saving SJR with UTR ${sjr.businessDetails.utr} \n ${toJson(sjr).toString()}")
    new IllegalStateException(s"Could not find existing SJR with UTR = ${sjr.businessDetails.utr}")
  }
}
