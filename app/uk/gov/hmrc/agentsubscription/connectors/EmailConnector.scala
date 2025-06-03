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
import play.api.http.Status.ACCEPTED
import play.api.libs.json.Json
import play.api.mvc.RequestHeader
import uk.gov.hmrc.agentsubscription.config.AppConfig
import uk.gov.hmrc.agentsubscription.model.EmailInformation
import uk.gov.hmrc.agentsubscription.utils.HttpAPIMonitor
import uk.gov.hmrc.agentsubscription.utils.RequestSupport.hc
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HttpResponse, StringContextOps}
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class EmailConnector @Inject() (appConfig: AppConfig, http: HttpClientV2, val metrics: Metrics)(implicit
  val ec: ExecutionContext
) extends HttpAPIMonitor with Logging {

  val baseUrl: String = appConfig.emailBaseUrl

  def sendEmail(emailInformation: EmailInformation)(implicit rh: RequestHeader): Future[Unit] =
    monitor(s"ConsumedAPI-Send-Email-${emailInformation.templateId}") {
      http
        .post(url"$baseUrl/hmrc/email")
        .withBody(Json.toJson(emailInformation))
        .execute[HttpResponse]
        .map { response =>
          response.status match {
            case ACCEPTED => logger.info(s"sent email success! template: ${emailInformation.templateId}")
            case e => logger.warn(s"sent email FAILED with status $e for template: ${emailInformation.templateId}")
          }
        }
    }
}
