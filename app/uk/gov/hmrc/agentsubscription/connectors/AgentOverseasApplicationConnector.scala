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
import play.api.libs.json._
import play.api.mvc.RequestHeader
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentsubscription.config.AppConfig
import uk.gov.hmrc.agentsubscription.model.ApplicationStatus.Complete
import uk.gov.hmrc.agentsubscription.model.ApplicationStatus.Registered
import uk.gov.hmrc.agentsubscription.model._
import uk.gov.hmrc.agentsubscription.utils.HttpAPIMonitor
import uk.gov.hmrc.agentsubscription.utils.RequestSupport.hc
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class AgentOverseasApplicationConnector @Inject() (
  appConfig: AppConfig,
  http: HttpClientV2,
  val metrics: Metrics
)(
  implicit val ec: ExecutionContext
)
extends Logging
with HttpAPIMonitor {

  val baseUrl: String = appConfig.agentOverseasApplicationBaseUrl

  def updateApplicationStatus(
    status: ApplicationStatus,
    authId: String,
    safeId: Option[SafeId] = None,
    arn: Option[Arn] = None
  )(implicit rh: RequestHeader): Future[Boolean] =
    monitor("ConsumedAPI-Agent-Overseas-Application-updateStatus-PUT") {
      val body =
        status match {
          case Registered => Json.obj("safeId" -> JsString(safeId.map(_.value).getOrElse("")))
          case Complete => Json.obj("arn" -> JsString(arn.map(_.value).getOrElse("")))
          case _ => Json.obj()
        }
      http
        .put(url"$baseUrl/agent-overseas-application/application/${status.key}")
        .withBody(body)
        .execute[HttpResponse]
        .map { response =>
          response.status match {
            case NO_CONTENT => true
            case s =>
              logger.error(s"Unexpected response: $s")
              throw new RuntimeException(
                s"Could not update overseas agent application status to ${status.key} for userId: $authId"
              )
          }
        }
    }

  def currentApplication(implicit rh: RequestHeader): Future[CurrentApplication] = {
    val activeStatuses = ApplicationStatus.ActiveStatuses.map(status => s"statusIdentifier=${status.key}").mkString("&")
    val url = s"$baseUrl/agent-overseas-application/application?$activeStatuses"
    monitor("ConsumedAPI-Agent-Overseas-Application-application-GET") {
      http
        .get(url"$url")
        .execute[HttpResponse]
        .map { response =>
          val json = response.json.head
          val status = (json \ "status").as[ApplicationStatus]

          val safeId =
            (json \ "safeId").validateOpt[SafeId] match {
              case JsSuccess(validSafeId, _) => validSafeId
              case JsError(errors) => throw new JsResultException(errors)
            }
          val amlsDetails = (json \ "amls").asOpt[OverseasAmlsDetails]
          val businessDetails = (json \ "tradingDetails").as[TradingDetails]
          val businessContactDetails = (json \ "contactDetails").as[OverseasContactDetails]
          val agencyDetails = (json \ "agencyDetails").as[OverseasAgencyDetails]
          CurrentApplication(
            status,
            safeId,
            amlsDetails,
            businessContactDetails,
            businessDetails,
            agencyDetails
          )
        }
        .recover {
          case e: JsResultException =>
            val errors = e.errors.flatMap(_._2.map(_.message))
            logger.error(s"The retrieved current application is invalid: $errors")
            throw e

          case e => throw new RuntimeException(s"Could not retrieve overseas agent application", e)
        }
    }
  }

}
