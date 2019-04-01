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

package uk.gov.hmrc.agentsubscription.service

import javax.inject.{ Inject, Singleton }
import play.api.Logger
import uk.gov.hmrc.agentsubscription.connectors.CitizenDetailsConnector
import uk.gov.hmrc.agentsubscription.model.CitizenDetailsMatchResponse._
import uk.gov.hmrc.agentsubscription.model.{ CitizenDetailsMatchResponse, CitizenDetailsRequest }
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class CitizenDetailsService @Inject() (citizenDetailsConnector: CitizenDetailsConnector) {

  def checkDetails(cd: CitizenDetailsRequest)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[CitizenDetailsMatchResponse] = {
    citizenDetailsConnector.getDateOfBirth(cd.nino).map {
      case Some(dob) => if (dob.value equals cd.dateOfBirth.value) {
        Match
      } else {
        Logger.warn("The date of birth supplied did not match Citizen Details records")
        NoMatch
      }
      case None => {
        Logger.warn("The supplied nino was not recognised by Citizen Details")
        RecordNotFound
      }
      case e => UnknownError
    }
  }
}
