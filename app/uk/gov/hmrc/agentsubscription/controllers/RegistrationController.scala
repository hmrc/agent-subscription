/*
 * Copyright 2018 HM Revenue & Customs
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

import com.kenshoo.play.metrics.Metrics
import play.api.libs.json.Json
import play.api.libs.json.Json.toJson
import play.api.mvc.{AnyContent, Request, Result}
import uk.gov.hmrc.agentsubscription.connectors.{AuthActions, Provider}
import uk.gov.hmrc.agentsubscription.model.postcodeWithoutSpacesRegex
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscription.MicroserviceAuthConnector
import uk.gov.hmrc.agentsubscription.service.RegistrationService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future

@Singleton
class RegistrationController @Inject()(service: RegistrationService)
                                      (implicit metrics: Metrics, microserviceAuthConnector: MicroserviceAuthConnector)
  extends AuthActions(metrics, microserviceAuthConnector) with BaseController {

  private[controllers] def register(utr: Utr, postcode: String)
                                   (implicit hc: HeaderCarrier, provider: Provider, request: Request[AnyContent]): Future[Result] = {
    if (!Utr.isValid(utr.value))
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

  def getRegistration(utr: Utr, postcode: String) = affinityGroupAndCredentials {
    implicit request =>
      implicit provider => {
        register(utr, postcode)
      }
  }

  private def validPostcode(postcode: String): Boolean = {
    postcode.replaceAll("\\s", "").matches(postcodeWithoutSpacesRegex)
  }
}
