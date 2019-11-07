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
import play.api.Logger
import play.api.libs.json._
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentsubscription.config.AppConfig
import uk.gov.hmrc.agentsubscription.model.ApplicationStatus.{ Complete, Registered }
import uk.gov.hmrc.agentsubscription.model._
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class AgentOverseasApplicationConnector @Inject() (
  appConfig: AppConfig,
  http: HttpClient,
  metrics: Metrics) extends HttpAPIMonitor {

  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  val baseUrl = appConfig.agentOverseasApplicationBaseUrl

  def updateApplicationStatus(status: ApplicationStatus, authId: String, safeId: Option[SafeId] = None, arn: Option[Arn] = None)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Boolean] = {

    val url = s"$baseUrl/agent-overseas-application/application/${status.key}"

    val body = status match {
      case Registered => Json.obj("safeId" -> JsString(safeId.map(_.value).getOrElse("")))
      case Complete => Json.obj("arn" -> JsString(arn.map(_.value).getOrElse("")))
      case _ => Json.obj()
    }

    monitor("ConsumedAPI-Agent-Overseas-Application-updateStatus-PUT") {
      http.PUT[JsValue, HttpResponse](url.toString, body)
        .map(_.status == 204)
        .recover {
          case e => throw new RuntimeException(s"Could not update overseas agent application status to ${status.key} for userId: $authId with ${e.getMessage}")
        }
    }
  }

  def currentApplication(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[CurrentApplication] = {
    val activeStatuses = ApplicationStatus.ActiveStatuses.map(status => s"statusIdentifier=${status.key}").mkString("&")
    val url = s"$baseUrl/agent-overseas-application/application?$activeStatuses"

    monitor("ConsumedAPI-Agent-Overseas-Application-application-GET") {
      http.GET(url.toString).map { response =>
        val json = response.json.head
        val status = (json \ "status").as[ApplicationStatus]

        val safeId = (json \ "safeId").validateOpt[SafeId] match {
          case JsSuccess(validSafeId, _) => validSafeId
          case JsError(errors) => throw new JsResultException(errors)
        }

        val amlsDetails = (json \ "amls").asOpt[OverseasAmlsDetails]
        val businessDetails = (json \ "tradingDetails").as[TradingDetails]
        val businessContactDetails = (json \ "contactDetails").as[OverseasContactDetails]
        val agencyDetails = (json \ "agencyDetails").as[OverseasAgencyDetails]

        CurrentApplication(status, safeId, amlsDetails, businessContactDetails, businessDetails, agencyDetails)
      }.recover {
        case e: JsResultException =>
          val errors: Seq[String] = e.errors.flatMap(_._2.map(_.message))
          Logger.error(s"The retrieved current application is invalid: $errors")
          throw e

        case e => throw new RuntimeException(s"Could not retrieve overseas agent application", e)
      }
    }
  }
}