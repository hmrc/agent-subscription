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

import play.api.http.Status._
import play.api.libs.json.Json.format
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import play.api.mvc.RequestHeader
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentsubscription.config.AppConfig
import uk.gov.hmrc.agentsubscription.utils.HttpAPIMonitor
import uk.gov.hmrc.agentsubscription.utils.RequestSupport.hc
import uk.gov.hmrc.http.HttpErrorFunctions._
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

case class KnownFact(
  key: String,
  value: String
)

case class Legacy(previousVerifiers: Seq[KnownFact])

case class KnownFactsRequest(
  verifiers: Seq[KnownFact],
  legacy: Option[Legacy]
)

object KnownFact {
  implicit val formatKf: OFormat[KnownFact] = format
}

object Legacy {
  implicit val formatL: OFormat[Legacy] = format
}

object KnownFactsRequest {
  implicit val formatKFR: OFormat[KnownFactsRequest] = format
}

case class EnrolmentRequest(
  userId: String,
  `type`: String,
  friendlyName: String,
  verifiers: Seq[KnownFact]
)

object EnrolmentRequest {
  implicit val formats: OFormat[EnrolmentRequest] = format
}

@Singleton
class TaxEnrolmentsConnector @Inject() (
  appConfig: AppConfig,
  http: HttpClientV2,
  val metrics: Metrics
)(implicit
  val ec: ExecutionContext
)
extends HttpAPIMonitor {

  val taxEnrolmentsBaseUrl: String = appConfig.taxEnrolmentsBaseUrl
  val espBaseUrl: String = appConfig.enrolmentStoreProxyBaseUrl

  // EACD's ES6 API
  def addKnownFacts(
    arn: String,
    knownFactKey: String,
    knownFactValue: String
  )(implicit
    rh: RequestHeader
  ): Future[Integer] = {
    val request = KnownFactsRequest(List(KnownFact(knownFactKey, knownFactValue)), None)

    monitor("ConsumedAPI-EMAC-AddKnownFacts-HMRC-AS-AGENT-PUT") {
      http
        .put(url"$taxEnrolmentsBaseUrl/tax-enrolments/enrolments/${enrolmentKey(arn)}")
        .withBody(Json.toJson(request))
        .execute[HttpResponse]
        .map { response =>
          response.status match {
            case s if is2xx(s) => s
            case s => throw UpstreamErrorResponse(s"Unexpected response: $s", s)
          }
        }
    }
  }

  // EACD's ES7 API
  def deleteKnownFacts(arn: Arn)(implicit rh: RequestHeader): Future[Integer] =
    monitor("ConsumedAPI-EMAC-DeleteKnownFacts-HMRC-AS-AGENT-DELETE") {
      http
        .delete(url"$espBaseUrl/enrolment-store-proxy/enrolment-store/enrolments/${enrolmentKey(
            arn.value
          )}")
        .execute[HttpResponse]
        .map { response =>
          response.status match {
            case s if is2xx(s) => s
            case s => throw UpstreamErrorResponse(s"Unexpected response: $s", s)
          }
        }
    }

  // EACD's ES8 API
  def enrol(
    groupId: String,
    arn: Arn,
    enrolmentRequest: EnrolmentRequest
  )(implicit
    rh: RequestHeader
  ): Future[Integer] =
    monitor("ConsumedAPI-EMAC-Enrol-HMRC-AS-AGENT-POST") {
      http
        .post(url"$taxEnrolmentsBaseUrl/tax-enrolments/groups/$groupId/enrolments/${enrolmentKey(arn.value)}")
        .withBody(Json.toJson(enrolmentRequest))
        .execute[HttpResponse]
        .map { response =>
          response.status match {
            case s if is2xx(s) => s
            case s => throw UpstreamErrorResponse(s"Unexpected response: $s", s)
          }
        }
    }

  // EACD's ES1 API (principal)
  def hasPrincipalGroupIds(arn: Arn)(implicit rh: RequestHeader): Future[Boolean] =
    monitor("ConsumedAPI-EMAC-GetPrincipalGroupIdFor-HMRC-AS-AGENT-GET") {
      http
        .get(
          url"$espBaseUrl/enrolment-store-proxy/enrolment-store/enrolments/${enrolmentKey(arn.value)}/groups?type=principal"
        )
        .execute[HttpResponse]
        .map { response =>
          response.status match {
            case OK => (response.json \ "principalGroupIds").as[Seq[String]].nonEmpty
            case NO_CONTENT => false
            case BAD_REQUEST => throw new BadRequestException(s"BAD_REQUEST")
            case s => throw UpstreamErrorResponse(s"Unexpected response: $s", s)
          }
        }
    }

  private def enrolmentKey(arn: String): String = s"HMRC-AS-AGENT~AgentReferenceNumber~$arn"

}
