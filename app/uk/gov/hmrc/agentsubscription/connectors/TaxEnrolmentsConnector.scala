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

import com.codahale.metrics.MetricRegistry
import com.kenshoo.play.metrics.Metrics
import javax.inject.{ Inject, Singleton }
import play.api.libs.json.Json.format
import play.api.libs.json.{ JsValue, Json, OFormat }
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentsubscription.config.AppConfig
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ ExecutionContext, Future }

case class KnownFact(key: String, value: String)
case class Legacy(previousVerifiers: Seq[KnownFact])
case class KnownFactsRequest(verifiers: Seq[KnownFact], legacy: Option[Legacy])

object KnownFact {
  implicit val formatKf: OFormat[KnownFact] = format
}

object Legacy {
  implicit val formatL: OFormat[Legacy] = format
}

object KnownFactsRequest {
  implicit val formatKFR: OFormat[KnownFactsRequest] = format
}
case class EnrolmentRequest(userId: String, `type`: String, friendlyName: String, verifiers: Seq[KnownFact])

object EnrolmentRequest {
  implicit val formats: OFormat[EnrolmentRequest] = format
}

@Singleton
class TaxEnrolmentsConnector @Inject() (
  appConfig: AppConfig,
  http: HttpClient,
  metrics: Metrics) extends HttpAPIMonitor {
  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  val taxEnrolmentsBaseUrl = appConfig.taxEnrolmentsBaseUrl
  val espBaseUrl = appConfig.enrolmentStoreProxyBaseUrl

  // EACD's ES6 API
  def addKnownFacts(arn: String, knownFactKey: String, knownFactValue: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Integer] = {
    val request = KnownFactsRequest(List(KnownFact(knownFactKey, knownFactValue)), None)

    monitor("EMAC-AddKnownFacts-HMRC-AS-AGENT-PUT") {
      http.PUT[JsValue, HttpResponse](s"""$taxEnrolmentsBaseUrl/tax-enrolments/enrolments/${enrolmentKey(arn)}""", Json.toJson(request)) map {
        response => response.status
      }
    }
  }

  // EACD's ES7 API
  def deleteKnownFacts(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Integer] = {
    monitor("EMAC-DeleteKnownFacts-HMRC-AS-AGENT-DELETE") {
      http.DELETE[HttpResponse](s"""$espBaseUrl/enrolment-store-proxy/enrolment-store/enrolments/${enrolmentKey(arn.value)}""")
        .map(_.status)
    }
  }

  // EACD's ES8 API
  def enrol(groupId: String, arn: Arn, enrolmentRequest: EnrolmentRequest)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Integer] = {
    val serviceUrl = s"""$taxEnrolmentsBaseUrl/tax-enrolments/groups/$groupId/enrolments/${enrolmentKey(arn.value)}"""

    monitor("EMAC-Enrol-HMRC-AS-AGENT-POST") {
      http.POST[JsValue, HttpResponse](serviceUrl, Json.toJson(enrolmentRequest)) map {
        response => response.status
      }
    }
  }

  // EACD's ES1 API (principal)
  def hasPrincipalGroupIds(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Boolean] = {
    val url = s"$espBaseUrl/enrolment-store-proxy/enrolment-store/enrolments/${enrolmentKey(arn.value)}/groups?type=principal"

    monitor("EMAC-GetPrincipalGroupIdFor-HMRC-AS-AGENT-GET") {
      http.GET[HttpResponse](url)
    }.map(response => response.status match {
      case 200 => (response.json \ "principalGroupIds").as[Seq[String]].nonEmpty
      case 204 => false
    })
  }

  private def enrolmentKey(arn: String): String = s"HMRC-AS-AGENT~AgentReferenceNumber~$arn"

}
