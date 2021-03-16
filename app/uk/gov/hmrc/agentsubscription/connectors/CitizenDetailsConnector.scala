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
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentsubscription.config.AppConfig
import uk.gov.hmrc.agentsubscription.model.DesignatoryDetails
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{ HeaderCarrier, HttpClient, HttpResponse, UpstreamErrorResponse }
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.HttpErrorFunctions._

import scala.concurrent.{ ExecutionContext, Future }

@ImplementedBy(classOf[CitizenDetailsConnectorImpl])
trait CitizenDetailsConnector {

  def appConfig: AppConfig
  def getDesignatoryDetails(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[DesignatoryDetails]
}

@Singleton
class CitizenDetailsConnectorImpl @Inject() (
  val appConfig: AppConfig,
  httpClient: HttpClient,
  metrics: Metrics)
  extends CitizenDetailsConnector with HttpAPIMonitor {

  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  val baseUrl = appConfig.citizenDetailsBaseUrl

  def getDesignatoryDetails(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[DesignatoryDetails] =
    monitor(s"ConsumedAPI-getDesignatoryDetails-GET") {
      val url = s"$baseUrl/citizen-details/${nino.value}/designatory-details"
      httpClient
        .GET[HttpResponse](url.toString)
        .map(response =>
          response.status match {
            case s if is2xx(s) => response.json.as[DesignatoryDetails]
            case s => throw UpstreamErrorResponse(response.body, s)
          })
    }
}
