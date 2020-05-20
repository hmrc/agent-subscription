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

package uk.gov.hmrc.agentsubscription.service

import javax.inject.{Inject, Singleton}
import play.api.Logger
import uk.gov.hmrc.agentsubscription.connectors.CompaniesHouseApiProxyConnector
import uk.gov.hmrc.agentsubscription.model.MatchDetailsResponse._
import uk.gov.hmrc.agentsubscription.model.{Crn, MatchDetailsResponse}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CompaniesHouseService @Inject() (companiesHouseConnector: CompaniesHouseApiProxyConnector)(implicit ec: ExecutionContext) {

  def knownFactCheck(crn: Crn, nameToMatch: String)(implicit hc: HeaderCarrier): Future[MatchDetailsResponse] = {
    companiesHouseConnector.getCompanyOfficers(crn).map {
      case Nil => RecordNotFound
      case result =>

        val matchResult: Boolean = result
          .filterNot(_.resignedOn.isDefined)
          .map(_.name.toLowerCase)
          .mkString
          .contains(nameToMatch.toLowerCase)

        if (matchResult) Match
        else {
          Logger.warn(s"Companies House known fact check failed for $nameToMatch and crn $crn")
          NoMatch
        }
    }
  }

}
