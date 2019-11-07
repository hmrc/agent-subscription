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
import com.google.inject.ImplementedBy
import com.kenshoo.play.metrics.Metrics
import javax.inject.{ Inject, Singleton }
import play.api.libs.json.{ Format, JsObject, Json }
import play.mvc.Http.Status.CREATED
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentmtdidentifiers.model.{ Arn, Utr }
import uk.gov.hmrc.agentsubscription.config.AppConfig
import uk.gov.hmrc.agentsubscription.connectors.AgentAssuranceConnector.{ CreateAmlsRequest, CreateOverseasAmlsRequest }
import uk.gov.hmrc.agentsubscription.model.{ AmlsDetails, OverseasAmlsDetails }
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ ExecutionContext, Future }

@ImplementedBy(classOf[AgentAssuranceConnectorImpl])
trait AgentAssuranceConnector {

  def appConfig: AppConfig

  def createAmls(utr: Utr, amlsDetails: AmlsDetails)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Boolean]

  def updateAmls(utr: Utr, arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[AmlsDetails]]

  def createOverseasAmls(arn: Arn, amlsDetails: OverseasAmlsDetails)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit]
}

@Singleton
class AgentAssuranceConnectorImpl @Inject() (
  val appConfig: AppConfig,
  http: HttpClient,
  metrics: Metrics)
  extends AgentAssuranceConnector with HttpAPIMonitor {

  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  val baseUrl = appConfig.agentAssuranceBaseUrl

  override def createAmls(utr: Utr, amlsDetails: AmlsDetails)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Boolean] = {

    val url: String = s"$baseUrl/agent-assurance/amls"

    monitor("ConsumedAPI-AgentAssurance-amls-POST") {
      http
        .POST(url, CreateAmlsRequest(utr, amlsDetails))
        .map(_.status == CREATED)
        .recover {
          //403 -> There is an existing AMLS record for the Utr with Arn set
          case e: Upstream4xxResponse if e.upstreamResponseCode == 403 => false
        }
    }
  }

  def updateAmls(utr: Utr, arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[AmlsDetails]] = {

    val url = s"$baseUrl/agent-assurance/amls/utr/${utr.value}"

    monitor(s"ConsumedAPI-AgentAssurance-amls-PUT") {
      http.PUT[JsObject, HttpResponse](url, Json.obj("value" -> arn.value))
        .map[Option[AmlsDetails]](r => Some(r.json.as[AmlsDetails]))
    }.recover {
      //404 -> Partially subscribed agents may not have any stored amls details, then updating fails with 404
      case _: NotFoundException => None
    }
  }

  override def createOverseasAmls(arn: Arn, amlsDetails: OverseasAmlsDetails)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] = {

    val url = s"$baseUrl/agent-assurance/overseas-agents/amls"

    monitor("ConsumedAPI-AgentAssurance-overseas-agents-amls-POST") {
      http.POST(url, CreateOverseasAmlsRequest(arn, amlsDetails))
        .map(_ => ())
        .recover {
          case e: Upstream4xxResponse if e.upstreamResponseCode == 409 => ()
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
