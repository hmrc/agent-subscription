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

package uk.gov.hmrc.agentsubscription.connectors

import play.api.Logging
import play.api.http.Status._
import play.api.mvc.RequestHeader
import play.utils.UriEncoding
import uk.gov.hmrc.agentsubscription.config.AppConfig
import uk.gov.hmrc.agentsubscription.model.CompaniesHouseOfficer
import uk.gov.hmrc.agentsubscription.model.Crn
import uk.gov.hmrc.agentsubscription.model.ReducedCompanyInformation
import uk.gov.hmrc.agentsubscription.utils.HttpAPIMonitor
import uk.gov.hmrc.agentsubscription.utils.RequestSupport.hc
import uk.gov.hmrc.http.HttpErrorFunctions._
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.BadRequestException
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class CompaniesHouseApiProxyConnector @Inject() (
  appConfig: AppConfig,
  http: HttpClientV2,
  val metrics: Metrics
)(implicit val ec: ExecutionContext)
extends Logging
with HttpAPIMonitor {

  val baseUrl: String = appConfig.companiesHouseApiProxyBaseUrl

  def getCompanyOfficers(
    crn: Crn,
    surname: String
  )(implicit
    rh: RequestHeader
  ): Future[Seq[CompaniesHouseOfficer]] =
    monitor("ConsumedAPI-getCompanyOfficers-GET") {
      val encodedCrn = UriEncoding.encodePathSegment(crn.value, "UTF-8")
      val encodedSurname = UriEncoding.encodePathSegment(surname, "UTF-8")
      http
        .get(url"$baseUrl/companies-house-api-proxy/company/$encodedCrn/officers?surname=$encodedSurname")
        .execute[HttpResponse]
        .map { response =>
          response.status match {
            case s if is2xx(s) => (response.json \ "items").as[Seq[CompaniesHouseOfficer]]
            case BAD_REQUEST => throw new BadRequestException(s"BAD_REQUEST")
            case s if is4xx(s) =>
              logger.warn(s"getCompanyOfficers http status: $s")
              Seq.empty
            case s =>
              logger.error(s"getCompanyOfficers http status: $s")
              Seq.empty
          }
        }
    }

  def getCompany(
    crn: Crn
  )(implicit rh: RequestHeader): Future[Option[ReducedCompanyInformation]] =
    monitor("ConsumedAPI-getCompany-GET") {
      val encodedCrn = UriEncoding.encodePathSegment(crn.value, "UTF-8")
      http
        .get(url"$baseUrl/companies-house-api-proxy/company/$encodedCrn")
        .execute[HttpResponse]
        .map { response =>
          response.status match {
            case s if is2xx(s) => response.json.asOpt[ReducedCompanyInformation]
            case s @ (BAD_REQUEST | UNAUTHORIZED) => throw UpstreamErrorResponse(s"Unexpected response: $s", s)
            case s if is4xx(s) =>
              logger.warn(s"getCompany http status: $s")
              Option.empty[ReducedCompanyInformation]
            case s =>
              logger.error(s"getCompany http status: $s")
              Option.empty[ReducedCompanyInformation]
          }
        }
    }

}
