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

import play.api.Logging
import play.api.libs.json.{Format, Json}
import play.api.mvc.RequestHeader
import play.mvc.Http.Status._
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr}
import uk.gov.hmrc.agentsubscription.config.AppConfig
import uk.gov.hmrc.agentsubscription.connectors.AgentAssuranceConnector.{CreateAmlsRequest, CreateOverseasAmlsRequest}
import uk.gov.hmrc.agentsubscription.model.{AmlsDetails, OverseasAmlsDetails}
import uk.gov.hmrc.agentsubscription.utils.HttpAPIMonitor
import uk.gov.hmrc.agentsubscription.utils.RequestSupport.hc
import uk.gov.hmrc.http.HttpErrorFunctions._
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AgentAssuranceConnector @Inject() (appConfig: AppConfig, http: HttpClientV2, val metrics: Metrics)(implicit
  val ec: ExecutionContext
) extends Logging with HttpAPIMonitor {

  val baseUrl: String = appConfig.agentAssuranceBaseUrl

  def createAmls(utr: Utr, amlsDetails: AmlsDetails)(implicit
    rh: RequestHeader
  ): Future[Boolean] =
    monitor("ConsumedAPI-AgentAssurance-amls-POST") {
      http
        .post(url"$baseUrl/agent-assurance/amls")
        .withBody(Json.toJson(CreateAmlsRequest(utr, amlsDetails)))
        .execute[HttpResponse]
        .map { response =>
          response.status match {
            case CREATED     => true
            case FORBIDDEN   => false // 403 -> There is an existing AMLS record for the Utr with Arn set
            case BAD_REQUEST => throw new BadRequestException(s"BAD_REQUEST")
            case s =>
              val message = s"Unexpected response: $s"
              logger.error(message)
              throw UpstreamErrorResponse(message, s)
          }
        }
    }

  def updateAmls(utr: Utr, arn: Arn)(implicit rh: RequestHeader): Future[Option[AmlsDetails]] =
    monitor("ConsumedAPI-AgentAssurance-amls-PUT") {
      http
        .put(url"$baseUrl/agent-assurance/amls/utr/${utr.value}")
        .withBody(Json.obj("value" -> arn.value))
        .execute[HttpResponse]
        .map { response =>
          response.status match {
            case s if is2xx(s) => response.json.asOpt[AmlsDetails]
            case NOT_FOUND =>
              None // 404 -> Partially subscribed agents may not have any stored amls details, then updating fails with 404
            case BAD_REQUEST => throw new BadRequestException(s"BAD_REQUEST")
            case s =>
              val message = s"Unexpected response: $s"
              logger.error(message)
              throw UpstreamErrorResponse(message, s)
          }
        }
    }

  def createOverseasAmls(arn: Arn, amlsDetails: OverseasAmlsDetails)(implicit
    rh: RequestHeader
  ): Future[Unit] =
    monitor("ConsumedAPI-AgentAssurance-overseas-agents-amls-POST") {
      http
        .post(url"$baseUrl/agent-assurance/overseas-agents/amls")
        .withBody(Json.toJson(CreateOverseasAmlsRequest(arn, amlsDetails)))
        .execute[HttpResponse]
        .map { response =>
          response.status match {
            case s if is2xx(s) => ()
            case CONFLICT      => ()
            case BAD_REQUEST   => throw new BadRequestException(s"BAD_REQUEST")
            case s =>
              val message = s"Unexpected response: $s"
              logger.error(message)
              throw UpstreamErrorResponse(message, s)
          }
        }
    }
}

object AgentAssuranceConnector {
  case class CreateAmlsRequest(utr: Utr, amlsDetails: AmlsDetails)
  object CreateAmlsRequest {
    implicit val format: Format[CreateAmlsRequest] = Json.format[CreateAmlsRequest]
  }
  case class CreateOverseasAmlsRequest(arn: Arn, amlsDetails: OverseasAmlsDetails)

  object CreateOverseasAmlsRequest {
    implicit val format: Format[CreateOverseasAmlsRequest] = Json.format[CreateOverseasAmlsRequest]
  }
}
