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
import play.api.http.Status._
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

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MappingConnector @Inject() (appConfig: AppConfig, http: HttpClientV2, val metrics: Metrics)(implicit
  val ec: ExecutionContext
) extends Logging with HttpAPIMonitor {

  val baseUrl: String = appConfig.agentMappingBaseUrl

  // valid status can be CREATED or CONFLICT
  def createMappings(arn: Arn)(implicit rh: RequestHeader): Future[Unit] =
    monitor("ConsumedAPI-Mapping-CreateMappings-PUT") {
      http
        .put(url"$baseUrl/agent-mapping/mappings/task-list/arn/${arn.value}")
        .execute[HttpResponse]
        .map { response =>
          response.status match {
            case s if is2xx(s) =>
              logger.info("mapping was successful"); ()
            case FORBIDDEN =>
              logger.error("user is forbidden to perform mapping"); ()
            case CONFLICT =>
              logger.error("user has already mapped"); ()
            case s => logger.error("mapping failed for unknown reason, status code: $s"); ()
          }
        }
    }

  def createMappingDetails(arn: Arn)(implicit rh: RequestHeader): Future[Unit] =
    monitor("ConsumedAPI-Mapping-createOrUpdateMappingDetails-POST") {
      http
        .put(url"$baseUrl/agent-mapping/mappings/task-list/details/arn/${arn.value}")
        .execute[HttpResponse]
        .map {
          _.status match {
            case CREATED   => logger.info("creating mapping details from subscription journey record was successful")
            case OK        => logger.info(s"user mappings were empty")
            case NOT_FOUND => logger.warn(s"no user mappings found for this auth provider")
            case e         => logger.warn(s"create user mappings failed with status $e")
          }
        }
    }
}
