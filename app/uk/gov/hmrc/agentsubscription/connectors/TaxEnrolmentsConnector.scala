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

import com.kenshoo.play.metrics.Metrics
import play.api.libs.json.{JsValue, Json}
import play.api.libs.json.Json.{format, toJson}
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.http.{HeaderCarrier, HttpPost, HttpPut, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

case class KnownFact(key: String, value: String)
case class Legacy(previousVerifiers: Seq[KnownFact])
case class KnownFactsRequest(verifiers: Seq[KnownFact], legacy: Option[Legacy])

object KnownFact {
  implicit val formatKf = format[KnownFact]
}

object Legacy {
  implicit val formatL = format[Legacy]
}

object KnownFactsRequest {
  implicit val formatKFR = format[KnownFactsRequest]
}
case class EnrolmentRequest(userId: String, `type`: String, friendlyName: String, verifiers: Seq[KnownFact])

object EnrolmentRequest {
  implicit val formats = format[EnrolmentRequest]
}

@Singleton
class TaxEnrolmentsConnector @Inject()(@Named("tax-enrolments-baseUrl") baseUrl: URL, http: HttpPut with HttpPost, metrics:Metrics) extends HttpAPIMonitor {
  override val kenshooRegistry = metrics.defaultRegistry

  def sendKnownFacts(arn: String, postcode: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Integer] = {
    val request = KnownFactsRequest(List(KnownFact("AgencyPostcode",postcode)), None)

    monitor("EMAC-AddKnownFacts-HMRC-AS-AGENT-POST") {
      http.PUT[JsValue, HttpResponse](s"""${baseUrl}/tax-enrolments/enrolments/${enrolmentKey(arn)}""", Json.toJson(request)) map {
        response => response.status
      }
    }
  }

  def enrol(groupId: String, arn: Arn, enrolmentRequest: EnrolmentRequest)
           (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Integer] = {
    val serviceUrl = s"""${baseUrl}/tax-enrolments/groups/$groupId/enrolments/${enrolmentKey(arn.value)}"""

    monitor("EMAC-Enrol-HMRC-AS-AGENT-POST") {
      http.POST[JsValue, HttpResponse](serviceUrl, Json.toJson(enrolmentRequest)) map {
        response => response.status
      }
    }
  }

  private def enrolmentKey(arn: String): String = s"HMRC-AS-AGENT~AgentReferenceNumber~$arn"

}
