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

import javax.inject.{Inject, Singleton}
import play.api.Logging
import uk.gov.hmrc.agentsubscription.connectors.DesConnector
import uk.gov.hmrc.agentsubscription.model.MatchDetailsResponse
import uk.gov.hmrc.agentsubscription.model.MatchDetailsResponse._
import uk.gov.hmrc.domain.Vrn
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier, NotFoundException}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class VatKnownfactsService @Inject() (desConnector: DesConnector)(implicit ec: ExecutionContext) extends Logging {

  def matchVatKnownfacts(vrn: Vrn, vatRegistrationDate: String)(implicit
    hc: HeaderCarrier
  ): Future[MatchDetailsResponse] =
    desConnector
      .getVatKnownfacts(vrn)
      .map { dateOfReg =>
        if (dateOfReg == vatRegistrationDate)
          Match
        else {
          logger.warn(
            "The supplied VAT registration date does not match with the date of registration from DES records"
          )
          NoMatch
        }
      }
      .recover {
        case ex: NotFoundException =>
          logger.warn(s"No records found for the vrn ${vrn.value}", ex)
          RecordNotFound
        case ex: BadRequestException =>
          logger.warn(s"The vrn ${vrn.value} supplied is invalid", ex)
          InvalidIdentifier
        case ex =>
          logger.warn(s"Some exception occured ${ex.getMessage}")
          UnknownError
      }
}
