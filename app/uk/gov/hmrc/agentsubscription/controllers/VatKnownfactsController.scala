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
import play.api.mvc.{ Action, AnyContent }
import uk.gov.hmrc.agentsubscription.auth.AuthActions
import uk.gov.hmrc.agentsubscription.connectors.MicroserviceAuthConnector
import uk.gov.hmrc.agentsubscription.model.MatchDetailsResponse._
import uk.gov.hmrc.agentsubscription.service.VatKnownfactsService
import uk.gov.hmrc.domain.Vrn
import uk.gov.hmrc.play.bootstrap.controller.BaseController

@Singleton
class VatKnownfactsController @Inject() (service: VatKnownfactsService)(implicit metrics: Metrics, microserviceAuthConnector: MicroserviceAuthConnector)
  extends AuthActions(metrics, microserviceAuthConnector) with BaseController {

  def matchVatKnownfacts(vrn: Vrn, vatRegistrationDate: String): Action[AnyContent] = authorisedWithAgentAffinity { implicit request =>
    service.matchVatKnownfacts(vrn, vatRegistrationDate).map {
      case Match => Ok
      case NoMatch | RecordNotFound => NotFound
      case InvalidIdentifier => BadRequest
      case _ => InternalServerError
    }
  }
}

