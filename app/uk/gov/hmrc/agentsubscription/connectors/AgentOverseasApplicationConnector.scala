/*
 * Copyright 2018 HM Revenue & Customs
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
import javax.inject.{ Inject, Named, Singleton }

import com.codahale.metrics.MetricRegistry
import com.kenshoo.play.metrics.Metrics
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentsubscription.model.ApplicationStatus
import uk.gov.hmrc.http._

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class AgentOverseasApplicationConnector @Inject() (
  @Named("agent-overseas-application-baseUrl") baseUrl: URL,
  http: HttpGet,
  metrics: Metrics) extends HttpAPIMonitor {

  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  def updateApplicationStatus(status: ApplicationStatus, authId: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Boolean] = {

    val url: URL = new URL(baseUrl, s"/application/${status.key}")
    monitor(s"Agent-Overseas-Application-updateStatus-GET") {
      http.GET(url.toString)
        .map(_.status == 204)
        .recover {
          case e: Upstream4xxResponse => {
            throw new RuntimeException(s"Could not update overseas agent application status to ${status.key} for userId: $authId with ${e.getMessage}")
          }
        }
    }
  }
}