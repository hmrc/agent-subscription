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

import play.api.Logger.logger
import play.api.libs.json.Json
import play.api.mvc.{ Action, AnyContent, ControllerComponents }
import uk.gov.hmrc.agentsubscription.connectors.DesConnector
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{ Inject, Singleton }
import scala.concurrent.ExecutionContext

@Singleton
class AmlsSubscriptionController @Inject() (des: DesConnector, cc: ControllerComponents)(implicit ec: ExecutionContext) extends BackendController(cc) {

  private def is5xx(u: UpstreamErrorResponse): Boolean = u.statusCode >= 500 && u.statusCode < 600

  def getAmlsSubscription(amlsRegistrationNumber: String): Action[AnyContent] = Action.async { implicit request =>
    des.getAmlsSubscriptionStatus(amlsRegistrationNumber).map(amls => Ok(Json.toJson(amls))).recover {
      case e: UpstreamErrorResponse if is5xx(e) => {
        logger.warn(s"DES return status ${e.statusCode} ${e.message}")
        InternalServerError
      }
    }
  }
}
