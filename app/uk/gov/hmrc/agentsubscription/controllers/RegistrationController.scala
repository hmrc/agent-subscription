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

import com.kenshoo.play.metrics.Metrics
import javax.inject._
import play.api.Logger
import play.api.libs.json.Json
import play.api.libs.json.Json.toJson
import play.api.mvc.{ Action, AnyContent }
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscription.auth.AuthActions
import uk.gov.hmrc.agentsubscription.connectors.{ InvalidBusinessAddressException, InvalidIsAnASAgentException, MicroserviceAuthConnector }
import uk.gov.hmrc.agentsubscription.model.postcodeWithoutSpacesRegex
import uk.gov.hmrc.agentsubscription.service.RegistrationService
import uk.gov.hmrc.agentsubscription.utils.toFuture
import uk.gov.hmrc.play.bootstrap.controller.BaseController

@Singleton
class RegistrationController @Inject() (service: RegistrationService)(implicit metrics: Metrics, microserviceAuthConnector: MicroserviceAuthConnector)
  extends AuthActions(metrics, microserviceAuthConnector) with BaseController {

  def getRegistration(utr: Utr, postcode: String): Action[AnyContent] = authorisedWithAffinityGroupAndCredentials { implicit request => implicit provider => {
    if (!Utr.isValid(utr.value))
      badRequest("INVALID_UTR")
    else if (!validPostcode(postcode))
      badRequest("INVALID_POSTCODE")
    else
      service.getRegistration(utr, postcode).map(_
        .map(registrationDetails => Ok(toJson(registrationDetails)))
        .getOrElse(NotFound))
        .recover {
          case InvalidBusinessAddressException =>
            Logger.info(InvalidBusinessAddressException.error.getMessage)
            NotFound
          case InvalidIsAnASAgentException =>
            Logger.info(InvalidIsAnASAgentException.error.getMessage)
            InternalServerError
        }
  }
  }

  private def badRequest(code: String) =
    toFuture(BadRequest(Json.obj("code" -> code)))

  private def validPostcode(postcode: String): Boolean = {
    postcode.replaceAll("\\s", "").matches(postcodeWithoutSpacesRegex)
  }
}
