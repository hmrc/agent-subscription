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
import javax.inject.{Inject, Named, Singleton}

import com.codahale.metrics.MetricRegistry
import com.kenshoo.play.metrics.Metrics
import play.api.libs.json.JsValue
import play.api.libs.json.Json.{format, toJson}
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor

import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.http.{HeaderCarrier, HttpPost, HttpResponse}

case class KnownFact(`type`: String, value: String)

object KnownFact {
  implicit val formats = format[KnownFact]
}

case class KnownFacts(facts: List[KnownFact])

object KnownFacts {
  implicit val formats = format[KnownFacts]
}

@Singleton
class GovernmentGatewayAdminConnector @Inject() (@Named("gg-admin-baseUrl") baseUrl: URL, httpPost: HttpPost, metrics:Metrics) extends HttpAPIMonitor {
  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  private val serviceUrl = s"""${baseUrl}/government-gateway-admin/service/HMRC-AS-AGENT/known-facts"""

  def createKnownFacts(arn: String, postcode: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Integer] = {
    val facts = KnownFacts(List(KnownFact("AgentReferenceNumber",arn),KnownFact("AgencyPostcode", postcode)))
    val jsonData = toJson(facts)

    monitor("GGW-AddKnownFacts-HMRC-AS-AGENT-POST") {
      httpPost.POST[JsValue, HttpResponse](serviceUrl, jsonData) map {
        response => response.status
      }
    }
  }
}
