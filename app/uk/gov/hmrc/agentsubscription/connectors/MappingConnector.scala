/*
 * Copyright 2021 HM Revenue & Customs
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
import play.api.Logging
import play.api.http.Status._
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentsubscription.config.AppConfig
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.HttpClient
import uk.gov.hmrc.http.HttpReads.Implicits._
import scala.concurrent.{ ExecutionContext, Future }
import uk.gov.hmrc.http.HttpErrorFunctions._

@Singleton
class MappingConnector @Inject() (
  appConfig: AppConfig,
  http: HttpClient,
  metrics: Metrics) extends HttpAPIMonitor with Logging {

  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  val baseUrl = appConfig.agentMappingBaseUrl

  //valid status can be CREATED or CONFLICT
  def createMappings(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] = {
    val createUrl = s"$baseUrl/agent-mapping/mappings/task-list/arn/${arn.value}"
    monitor("ConsumedAPI-Mapping-CreateMappings-PUT") {
      http
        .PUT[String, HttpResponse](createUrl, "")
        .map(response =>
          response.status match {
            case s if is2xx(s) =>
              logger.info("mapping was successful"); ()
            case FORBIDDEN =>
              logger.error("user is forbidden to perform mapping"); ()
            case CONFLICT =>
              logger.error("user has already mapped"); ()
            case s => logger.error("mapping failed for unknown reason, status code: $s"); ()
          })
    }
  }

  def createMappingDetails(arn: Arn)(
    implicit
    hc: HeaderCarrier,
    ec: ExecutionContext): Future[Unit] = {

    val createMappingDetailsUrl = s"$baseUrl/agent-mapping/mappings/task-list/details/arn/${arn.value}"

    monitor("ConsumedAPI-Mapping-createOrUpdateMappingDetails-POST") {
      http.PUT[String, HttpResponse](createMappingDetailsUrl, "").map { _ =>
        logger.info("creating mapping details from subscription journey record was successful"); ()
      }.recover {
        case ex =>
          logger.error(s"creating or updating mapping details failed for some reason: $ex"); ()
      }
    }
  }
}

