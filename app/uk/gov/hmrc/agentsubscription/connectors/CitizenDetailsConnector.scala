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

package uk.gov.hmrc.agentsubscription.connectors

import java.net.URL

import com.codahale.metrics.MetricRegistry
import com.google.inject.ImplementedBy
import com.kenshoo.play.metrics.Metrics
import javax.inject.{ Inject, Named, Singleton }
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentsubscription.model.DesignatoryDetails
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ ExecutionContext, Future }

@ImplementedBy(classOf[CitizenDetailsConnectorImpl])
trait CitizenDetailsConnector {
  def getDesignatoryDetails(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[DesignatoryDetails]
}

@Singleton
class CitizenDetailsConnectorImpl @Inject() (
  @Named("citizen-details-baseUrl") baseUrl: URL,
  httpClient: HttpClient,
  metrics: Metrics)
  extends CitizenDetailsConnector with HttpAPIMonitor {

  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  def getDesignatoryDetails(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[DesignatoryDetails] =
    monitor("ConsumedAPI-getDesignatoryDetails-GET") {
      val url = new URL(baseUrl, s"/citizen-details/${nino.value}/designatory-details")
      httpClient.GET[DesignatoryDetails](url.toString)
    }
}
