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

package uk.gov.hmrc.agentsubscription.service

import javax.inject.{ Inject, Singleton }
import play.api.Logging
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscription.connectors.DesConnector
import uk.gov.hmrc.agentsubscription.model.{ Crn, MatchDetailsResponse }
import uk.gov.hmrc.agentsubscription.model.MatchDetailsResponse._
import uk.gov.hmrc.http.{ BadRequestException, HeaderCarrier, NotFoundException }

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class CTReferenceService @Inject() (desConnector: DesConnector) extends Logging {

  def matchCorporationTaxUtrWithCrn(utr: Utr, crn: Crn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[MatchDetailsResponse] = {
    desConnector.getCorporationTaxUtr(crn).map { ctUtr =>
      if (ctUtr == utr)
        Match
      else {
        logger.warn("The supplied utr does not match with the utr from DES records")
        NoMatch
      }
    }.recover {
      case _: NotFoundException =>
        logger.warn(s"No ct utr found for the crn ${crn.value}")
        RecordNotFound
      case _: BadRequestException =>
        logger.warn(s"The crn ${crn.value} supplied is invalid")
        InvalidIdentifier
      case ex =>
        logger.warn(s"Some exception occured ${ex.getMessage}")
        UnknownError
    }
  }
}
