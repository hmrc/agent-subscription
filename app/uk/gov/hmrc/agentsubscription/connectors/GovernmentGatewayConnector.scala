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

case class EnrolmentRequest(
                             portalId: String,
                             serviceName: String,
                             friendlyName: String,
                             knownFacts: Seq[String]
                           )

object EnrolmentRequest {
  implicit val formats = format[EnrolmentRequest]
}


@Singleton
class GovernmentGatewayConnector @Inject()(@Named("gg-baseUrl") baseUrl: URL, httpPost: HttpPost, metrics:Metrics) extends HttpAPIMonitor {
  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  private val serviceUrl = s"""$baseUrl/enrol"""

  def enrol(friendlyName: String, arn : String, postcode: String )
           (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Integer] = {
    val enrolmentRequest = EnrolmentRequest("Default", "HMRC-AS-AGENT", friendlyName, Seq(arn,postcode))
    val jsonData = toJson(enrolmentRequest)

    monitor("GGW-Enrol-HMRC-AS-AGENT-POST") {
      httpPost.POST[JsValue, HttpResponse](serviceUrl, jsonData) map {
        response => response.status
      }
    }
  }
}
