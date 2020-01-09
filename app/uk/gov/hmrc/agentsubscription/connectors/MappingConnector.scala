/*
 * Copyright 2020 HM Revenue & Customs
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
import play.api.Logger
import play.api.http.Status
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentsubscription.config.AppConfig
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class MappingConnector @Inject() (
  appConfig: AppConfig,
  http: HttpClient,
  metrics: Metrics) extends HttpAPIMonitor {

  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  val baseUrl = appConfig.agentMappingBaseUrl

  //valid status can be CREATED or CONFLICT
  def createMappings(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] = {
    val createUrl = s"$baseUrl/agent-mapping/mappings/task-list/arn/${arn.value}"
    monitor("ConsumedAPI-Mapping-CreateMappings-PUT") {
      http
        .PUT(createUrl, "")
        .map { _ => Logger.info("mapping was successful"); ()
        }.recover {
          case e: Upstream4xxResponse if Status.FORBIDDEN.equals(e.upstreamResponseCode) =>
            Logger.error("user is forbidden to perform mapping"); ()

          case e: Upstream4xxResponse if Status.CONFLICT.equals(e.upstreamResponseCode) =>
            Logger.error("user has already mapped"); ()

          case _ =>
            Logger.error("mapping failed for unknown reason"); ()
        }
    }
  }

  def createMappingDetails(arn: Arn)(
    implicit
    hc: HeaderCarrier,
    ec: ExecutionContext): Future[Unit] = {

    val createMappingDetailsUrl = s"$baseUrl/agent-mapping/mappings/task-list/details/arn/${arn.value}"

    monitor("ConsumedAPI-Mapping-createOrUpdateMappingDetails-POST") {
      http.PUT(createMappingDetailsUrl, "").map { _ =>
        Logger.info("creating mapping details from subscription journey record was successful"); ()
      }.recover {
        case ex =>
          Logger.error(s"creating or updating mapping details failed for some reason: $ex"); ()
      }
    }
  }
}

