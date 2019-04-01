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
import javax.inject.{ Inject, Singleton }
import play.api.libs.json.{ JsError, JsSuccess }
import play.api.mvc.{ Action, AnyContent }
import uk.gov.hmrc.agentsubscription.auth.AuthActions
import uk.gov.hmrc.agentsubscription.connectors.MicroserviceAuthConnector
import uk.gov.hmrc.agentsubscription.model.MatchDetailsResponse._
import uk.gov.hmrc.agentsubscription.model.CitizenDetailsRequest
import uk.gov.hmrc.agentsubscription.service.CitizenDetailsService
import uk.gov.hmrc.play.microservice.controller.BaseController
import uk.gov.hmrc.agentsubscription.utils._

@Singleton
class CitizenDetailsController @Inject() (service: CitizenDetailsService)(implicit metrics: Metrics, microserviceAuthConnector: MicroserviceAuthConnector)
  extends AuthActions(metrics, microserviceAuthConnector) with BaseController {

  def checkCitizenDetails: Action[AnyContent] = authorisedWithAgentAffinity { implicit request =>
    request.body.asJson.map(_.validate[CitizenDetailsRequest]) match {
      case Some(JsSuccess(cd, _)) =>
        service.checkDetails(cd).map {
          case Match => Ok
          case NoMatch | RecordNotFound => NotFound
        }
      case Some(JsError(_)) => BadRequest("could not parse nino and dob JSON request")
      case _ => InternalServerError
    }
  }
}
