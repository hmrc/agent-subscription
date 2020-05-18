/*
 * Copyright 2020 HM Revenue & Customs
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

import javax.inject.{ Inject, Singleton }
import play.api.mvc.{ Action, AnyContent, ControllerComponents }
import uk.gov.hmrc.agentsubscription.auth.AuthActions
import uk.gov.hmrc.agentsubscription.model.Crn
import uk.gov.hmrc.agentsubscription.model.MatchDetailsResponse.{ InvalidIdentifier, Match, NoMatch, RecordNotFound, UnknownError }
import uk.gov.hmrc.agentsubscription.service.CompaniesHouseService
import uk.gov.hmrc.play.bootstrap.controller.BackendController

import scala.concurrent.ExecutionContext

@Singleton
class CompaniesHouseController @Inject() (companiesHouseService: CompaniesHouseService, val authActions: AuthActions, cc: ControllerComponents)(implicit ec: ExecutionContext) extends BackendController(cc) {

  import authActions._

  def getCompanyOfficers(crn: Crn, nameToMatch: String): Action[AnyContent] = authorisedWithAgentAffinity { implicit request =>
    companiesHouseService.officerListContainsNameToMatch(crn, nameToMatch).map {
      case Match => Ok
      case NoMatch => NotFound
      case _ => InternalServerError
    }
  }
}
