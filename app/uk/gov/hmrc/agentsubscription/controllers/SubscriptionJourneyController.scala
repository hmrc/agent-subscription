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

import com.google.inject.Inject
import play.api.Logging
import play.api.libs.json.Json.toJson
import play.api.libs.json.JsError
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.ControllerComponents
import play.api.mvc.Result
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.auth.core.AuthorisedFunctions
import uk.gov.hmrc.agentsubscription.model.AuthProviderId
import uk.gov.hmrc.agentsubscription.model.subscriptionJourney.SubscriptionJourneyRecord
import uk.gov.hmrc.agentsubscription.repository.SubscriptionJourneyRepository
import uk.gov.hmrc.agentsubscription.utils._
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.time.LocalDateTime
import java.time.ZoneOffset
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class SubscriptionJourneyController @Inject() (
  val authConnector: AuthConnector,
  subscriptionJourneyRepository: SubscriptionJourneyRepository,
  cc: ControllerComponents
)(implicit ec: ExecutionContext)
extends BackendController(cc)
with Logging
with AuthorisedFunctions {

  def findByAuthId(authProviderId: AuthProviderId): Action[AnyContent] = Action.async { implicit request =>
    authorised() {
      subscriptionJourneyRepository.findByAuthId(authProviderId).map {
        case Some(record) => Ok(toJson(record))
        case None => NoContent
      }
    }
  }

  def findByUtr(utr: Utr): Action[AnyContent] = Action.async { implicit request =>
    authorised() {
      subscriptionJourneyRepository.findByUtr(utr.value).map {
        case Some(record) => Ok(toJson(record))
        case None => NoContent
      }
    }
  }

  def findByContinueId(continueId: String): Action[AnyContent] = Action.async { implicit request =>
    authorised() {
      subscriptionJourneyRepository.findByContinueId(continueId).map {
        case Some(record) => Ok(toJson(record))
        case None => NoContent
      }
    }
  }

  def createOrUpdate(authProviderId: AuthProviderId): Action[JsValue] =
    Action.async(parse.json) { implicit request =>
      authorised() {
        request.body.validate[SubscriptionJourneyRecord] match {
          case JsSuccess(journeyRecord, _) =>
            val mappedAuthIds = journeyRecord.userMappings.map(_.authProviderId)

            if (journeyRecord.authProviderId != authProviderId) {
              Future.successful(BadRequest("Auth ids in request URL and body do not match"))
            }
            else if (mappedAuthIds.distinct.size != mappedAuthIds.size) {
              Future.successful(BadRequest("Duplicate mapped auth ids in request body"))
            }
            else {
              subscriptionJourneyRepository.findByUtr(journeyRecord.businessDetails.utr).map(result =>
                if (result.isEmpty || result.exists(_.authProviderId == authProviderId)) {
                  val updatedRecord = journeyRecord.copy(lastModifiedDate = Some(LocalDateTime.now(ZoneOffset.UTC)))
                  subscriptionJourneyRepository.upsert(authProviderId, updatedRecord).map(_ => NoContent)
                }
                else {
                  updateExistingRecordWithNewCred(journeyRecord)
                }
              ).flatten
            }
          case JsError(errors) =>
            logger.warn(s"Failed to parse request body: $errors")
            Future.successful(BadRequest("Invalid SubscriptionJourneyRecord payload"))
        }
      }
    }

  // Find the SJR then overwrite the authProviderId, businessDetails and cleanCredsAuthProviderId. This allows the journey to progress when
  // the agent uses a different cred to continue the journey after some days/weeks and during that time some details may have changed e.g. address.
  private def updateExistingRecordWithNewCred(sjr: SubscriptionJourneyRecord)(implicit ec: ExecutionContext): Future[Result] = {
    val utr = sjr.businessDetails.utr
    for {
      modifiedRecord <- subscriptionJourneyRepository
        .updateOnUtr(
          utr,
          sjr.authProviderId,
          sjr.businessDetails,
          sjr.cleanCredsAuthProviderId
        )
      result <-
        modifiedRecord match {
          case Some(record: SubscriptionJourneyRecord) => Ok(toJson(record)).toFuture
          case _ => logUTRError(utr).toFailure
        }
    } yield result
  }

  private def logUTRError(utr: String): IllegalStateException = {
    logger.warn(s"Conflict saving SJR with UTR $utr")
    new IllegalStateException(s"Could not find existing SJR with UTR = $utr")
  }

}
