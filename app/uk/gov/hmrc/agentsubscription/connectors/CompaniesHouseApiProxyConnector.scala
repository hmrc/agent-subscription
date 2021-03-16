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

package uk.gov.hmrc.agentsubscription.connectors

import com.codahale.metrics.MetricRegistry
import com.google.inject.ImplementedBy
import com.kenshoo.play.metrics.Metrics
import javax.inject.{ Inject, Singleton }
import play.api.Logging
import play.utils.UriEncoding
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentsubscription.config.AppConfig
import uk.gov.hmrc.agentsubscription.model.{ CompaniesHouseOfficer, Crn }
import uk.gov.hmrc.http.HttpErrorFunctions._
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{ HeaderCarrier, HttpClient, HttpResponse, UpstreamErrorResponse }
import play.api.http.Status._

import scala.concurrent.{ ExecutionContext, Future }

@ImplementedBy(classOf[CompaniesHouseApiProxyConnectorImpl])
trait CompaniesHouseApiProxyConnector {

  def appConfig: AppConfig
  def getCompanyOfficers(crn: Crn, surname: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[CompaniesHouseOfficer]]

}

@Singleton
class CompaniesHouseApiProxyConnectorImpl @Inject() (
  val appConfig: AppConfig,
  httpClient: HttpClient,
  metrics: Metrics) extends CompaniesHouseApiProxyConnector with HttpAPIMonitor with Logging {

  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  val baseUrl = appConfig.companiesHouseApiProxyBaseUrl

  override def getCompanyOfficers(crn: Crn, surname: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[CompaniesHouseOfficer]] = {
    monitor(s"ConsumedAPI-getCompanyOfficers-GET") {
      val encodedCrn = UriEncoding.encodePathSegment(crn.value, "UTF-8")
      val encodedSurname = UriEncoding.encodePathSegment(surname, "UTF-8")
      val url = s"$baseUrl/companies-house-api-proxy/company/$encodedCrn/officers?surname=$encodedSurname"
      httpClient.GET[HttpResponse](url).map { response =>
        response.status match {
          case s if is2xx(s) =>
            (response.json \ "items").as[Seq[CompaniesHouseOfficer]]
          case BAD_REQUEST => throw UpstreamErrorResponse(response.body, BAD_REQUEST)
          case s if is4xx(s) =>
            logger.warn(s"getCompanyOfficers http status: $s, response:${response.body}")
            Seq.empty
          case s =>
            logger.error(s"getCompanyOfficers http status: $s, response:${response.body}")
            Seq.empty
        }
      }
    }
  }
}
