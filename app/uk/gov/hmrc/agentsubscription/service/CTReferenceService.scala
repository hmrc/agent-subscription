/*
 * Copyright 2024 HM Revenue & Customs
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

import play.api.Logging
import play.api.mvc.RequestHeader
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentsubscription.connectors.DesConnector
import uk.gov.hmrc.agentsubscription.model.MatchDetailsResponse._
import uk.gov.hmrc.agentsubscription.model.Crn
import uk.gov.hmrc.agentsubscription.model.MatchDetailsResponse
import uk.gov.hmrc.http.BadRequestException
import uk.gov.hmrc.http.NotFoundException

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class CTReferenceService @Inject() (desConnector: DesConnector)(implicit ec: ExecutionContext)
extends Logging {

  def matchCorporationTaxUtrWithCrn(
    utr: Utr,
    crn: Crn
  )(implicit
    rh: RequestHeader
  ): Future[MatchDetailsResponse] = desConnector
    .getCorporationTaxUtr(crn)
    .map { ctUtr =>
      if (ctUtr == utr)
        Match
      else {
        logger.warn("The supplied utr does not match with the utr from DES records")
        NoMatch
      }
    }
    .recover {
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
