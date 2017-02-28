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
import uk.gov.hmrc.agentsubscription.connectors.{AuthConnector, DesConnector}
import uk.gov.hmrc.agentsubscription.model.RegistrationDetails
import uk.gov.hmrc.play.http.{BadRequestException, HeaderCarrier}
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.play.microservice.controller.BaseController
import uk.gov.hmrc.agentsubscription._

import scala.concurrent.Future

@Singleton
class RegistrationController @Inject()(val desConnector: DesConnector, val authConnector: AuthConnector )
  extends BaseController {
  def getRegistration(utr: String, postcode: String) = Action.async { implicit request =>
    ensureAuthenticated {
      desConnector.getRegistration(utr) map {
        case Some(desRegistrationResponse) if postcodesMatch(desRegistrationResponse.postalCode, postcode) => Ok(toJson(RegistrationDetails(desRegistrationResponse.isAnASAgent)))
        case Some(desRegistrationResponse) if !postcodesMatch(desRegistrationResponse.postalCode, postcode) => NotFound
        case None => NotFound
      } recover {
        // TODO return a 400 instead? (we can do so by allowing this exception to propagate)
        case invalidUtr: BadRequestException => NotFound
      }
    }
  }

  private def ensureAuthenticated(action: => Future[Result])(implicit hc: HeaderCarrier): Future[Result] =
    authConnector.isAuthenticated().flatMap { authorised : Boolean =>
      if (authorised) {
        action
      } else {
        Future successful Unauthorized
      }
    }
}
