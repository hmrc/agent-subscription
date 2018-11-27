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

import com.codahale.metrics.MetricRegistry
import com.google.inject.ImplementedBy
import com.kenshoo.play.metrics.Metrics
import javax.inject.{ Inject, Named, Singleton }
import play.api.libs.json.{ JsObject, Json }
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentmtdidentifiers.model.{ Arn, Utr }
import uk.gov.hmrc.agentsubscription.model.AmlsDetails
import uk.gov.hmrc.http._

import scala.concurrent.{ ExecutionContext, Future }

@ImplementedBy(classOf[AgentAssuranceConnectorImpl])
trait AgentAssuranceConnector {
  def updateAmls(utr: Utr, arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[AmlsDetails]]
}

@Singleton
class AgentAssuranceConnectorImpl @Inject() (
  @Named("agent-assurance-baseUrl") baseUrl: URL,
  http: HttpPut,
  metrics: Metrics)
  extends AgentAssuranceConnector with HttpAPIMonitor {

  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  def updateAmls(utr: Utr, arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[AmlsDetails]] = {

    val url = new URL(baseUrl, s"/agent-assurance/amls/utr/${utr.value}")

    monitor(s"ConsumedAPI-Put-AgentAssurance-Update-amls") {
      http.PUT[JsObject, HttpResponse](url.toString, Json.obj("value" -> arn.value))
        .map[Option[AmlsDetails]](r => Some(r.json.as[AmlsDetails]))
        .recover {
          case _: NotFoundException | _: Upstream4xxResponse =>
            //allow agent to continue with subscription if existing Amls record is not found or already contains the same arn during update
            None
        }
    }
  }
}
