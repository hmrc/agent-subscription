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
import javax.inject.{ Inject, Named, Singleton }

import com.kenshoo.play.metrics.Metrics
import play.api.Logger
import play.api.libs.json.{ JsObject, JsValue, Json }
import play.api.libs.json.Json.{ format, toJson }
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.http._

import scala.concurrent.{ ExecutionContext, Future }

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
class TaxEnrolmentsConnector @Inject() (
  @Named("tax-enrolments-baseUrl") teBaseUrl: URL,
  @Named("enrolment-store-proxy-baseUrl") espBaseUrl: URL,
  http: HttpPut with HttpPost with HttpGet with HttpDelete,
  metrics: Metrics) extends HttpAPIMonitor {
  override val kenshooRegistry = metrics.defaultRegistry

  // EACD's ES6 API
  def addKnownFacts(arn: String, knownFactKey: String, knownFactValue: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Integer] = {
    val request = KnownFactsRequest(List(KnownFact(knownFactKey, knownFactValue)), None)

    monitor("EMAC-AddKnownFacts-HMRC-AS-AGENT-PUT") {
      http.PUT[JsValue, HttpResponse](s"""${teBaseUrl}/tax-enrolments/enrolments/${enrolmentKey(arn)}""", Json.toJson(request)) map {
        response => response.status
      }
    }
  }

  // EACD's ES7 API
  def deleteKnownFacts(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Integer] = {
    monitor("EMAC-DeleteKnownFacts-HMRC-AS-AGENT-DELETE") {
      http.DELETE[HttpResponse](s"""${espBaseUrl}/enrolment-store-proxy/enrolment-store/enrolments/${enrolmentKey(arn.value)}""")
        .map(_.status)
    }
  }

  // EACD's ES8 API
  def enrol(groupId: String, arn: Arn, enrolmentRequest: EnrolmentRequest)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Integer] = {
    val serviceUrl = s"""${teBaseUrl}/tax-enrolments/groups/$groupId/enrolments/${enrolmentKey(arn.value)}"""

    monitor("EMAC-Enrol-HMRC-AS-AGENT-POST") {
      http.POST[JsValue, HttpResponse](serviceUrl, Json.toJson(enrolmentRequest)) map {
        response => response.status
      }
    }
  }

  // EACD's ES1 API (principal)
  def hasPrincipalGroupIds(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Boolean] = {
    val url = new URL(espBaseUrl, s"/enrolment-store-proxy/enrolment-store/enrolments/${enrolmentKey(arn.value)}/groups?type=principal")

    monitor("EMAC-GetPrincipalGroupIdFor-HMRC-AS-AGENT-GET") {
      http.GET[HttpResponse](url.toString)
    }.map(response => response.status match {
      case 200 => (response.json \ "principalGroupIds").as[Seq[String]].nonEmpty
      case 204 => false
    })
  }

  private def enrolmentKey(arn: String): String = s"HMRC-AS-AGENT~AgentReferenceNumber~$arn"

}
