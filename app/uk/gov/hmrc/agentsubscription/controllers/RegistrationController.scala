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

import play.api.libs.json.Json
import play.api.libs.json.Json.toJson
import play.api.mvc.{Action, AnyContent, Request, Result}
import uk.gov.hmrc.agentsubscription.auth.AuthActions
import uk.gov.hmrc.agentsubscription.connectors.AuthConnector
import uk.gov.hmrc.agentsubscription.model.{Utr, postcodeWithoutSpacesRegex}
import uk.gov.hmrc.agentsubscription.service.RegistrationService
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future

@Singleton
class RegistrationController @Inject()(service: RegistrationService, override val authConnector: AuthConnector)
  extends BaseController with AuthActions {

  private[controllers] def getRegistrationBlock(utr: String, postcode: String): Request[AnyContent] => Future[Result] = { implicit request =>
    if (!Utr.isValid(utr))
      badRequest("INVALID_UTR")
    else if (!validPostcode(postcode))
      badRequest("INVALID_POSTCODE")
    else
      service.getRegistration(utr, postcode).map(_
        .map(registrationDetails => Ok(toJson(registrationDetails)))
        .getOrElse(NotFound))
  }

  private def badRequest(code: String) = {
    Future successful BadRequest(Json.obj("code" -> code))
  }

  def getRegistration(utr: String, postcode: String): Action[AnyContent] =
    withAgentAffinityGroup.async(getRegistrationBlock(utr, postcode))

  private def validPostcode(postcode: String): Boolean = {
    postcode.replaceAll("\\s", "").matches(postcodeWithoutSpacesRegex)
  }
}
