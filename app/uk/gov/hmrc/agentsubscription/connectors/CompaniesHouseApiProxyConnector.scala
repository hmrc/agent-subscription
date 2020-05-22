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

package uk.gov.hmrc.agentsubscription.connectors

import com.codahale.metrics.MetricRegistry
import com.google.inject.ImplementedBy
import com.kenshoo.play.metrics.Metrics
import javax.inject.{ Inject, Singleton }
import play.api.Logger
import play.api.libs.json.JsValue
import play.utils.UriEncoding
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentsubscription.config.AppConfig
import uk.gov.hmrc.agentsubscription.model.{ CompaniesHouseOfficer, Crn }
import uk.gov.hmrc.http.{ HeaderCarrier, Upstream4xxResponse }
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ ExecutionContext, Future }

@ImplementedBy(classOf[CompaniesHouseApiProxyConnectorImpl])
trait CompaniesHouseApiProxyConnector {

  def appConfig: AppConfig
  def getCompanyOfficers(crn: Crn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[CompaniesHouseOfficer]]

}

@Singleton
class CompaniesHouseApiProxyConnectorImpl @Inject() (
  val appConfig: AppConfig,
  httpClient: HttpClient,
  metrics: Metrics) extends CompaniesHouseApiProxyConnector with HttpAPIMonitor {

  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  val baseUrl = appConfig.companiesHouseApiProxyBaseUrl

  override def getCompanyOfficers(crn: Crn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[CompaniesHouseOfficer]] = {
    monitor(s"ConsumedAPI-getCompanyOfficers-GET") {
      val encodedCrn = UriEncoding.encodePathSegment(crn.value, "UTF-8")
      val url = s"$baseUrl/companies-house-api-proxy/company/$encodedCrn/officers"
      httpClient.GET[JsValue](url).map { response =>
        val json = response
        (json \ "items").as[Seq[CompaniesHouseOfficer]]
      }.recover {
        case e: Upstream4xxResponse => {
          Logger.warn(s"${e.message}")
          Seq.empty
        }
      }
    }
  }
}
