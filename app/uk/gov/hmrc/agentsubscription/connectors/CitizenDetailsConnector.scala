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

import play.api.mvc.RequestHeader
import uk.gov.hmrc.agentsubscription.config.AppConfig
import uk.gov.hmrc.agentsubscription.model.DesignatoryDetails
import uk.gov.hmrc.agentsubscription.utils.HttpAPIMonitor
import uk.gov.hmrc.agentsubscription.utils.RequestSupport.hc
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HttpErrorFunctions.is2xx
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HttpResponse, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CitizenDetailsConnector @Inject() (appConfig: AppConfig, http: HttpClientV2, val metrics: Metrics)(implicit
  val ec: ExecutionContext
) extends HttpAPIMonitor {

  val baseUrl: String = appConfig.citizenDetailsBaseUrl

  def getDesignatoryDetails(
    nino: Nino
  )(implicit rh: RequestHeader): Future[DesignatoryDetails] =
    monitor("ConsumedAPI-getDesignatoryDetails-GET") {
      http.get(url"$baseUrl/citizen-details/${nino.value}/designatory-details").execute[HttpResponse].map { response =>
        response.status match {
          case s if is2xx(s) => response.json.as[DesignatoryDetails]
          case s             => throw UpstreamErrorResponse(s"Unexpected response: $s", s)
        }
      }
    }
}
