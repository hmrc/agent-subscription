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

import com.google.inject.ImplementedBy

import javax.inject.Inject
import play.api.Logging
import play.api.http.Status.ACCEPTED
import uk.gov.hmrc.agentsubscription.config.AppConfig
import uk.gov.hmrc.agentsubscription.model.EmailInformation
import uk.gov.hmrc.agentsubscription.utils.HttpAPIMonitor
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.http.HttpClient
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[EmailConnectorImpl])
trait EmailConnector extends Logging {
  def appConfig: AppConfig
  def sendEmail(emailInformation: EmailInformation)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit]
}

class EmailConnectorImpl @Inject() (val appConfig: AppConfig, val http: HttpClient, val metrics: Metrics)(implicit
  val ec: ExecutionContext
) extends EmailConnector with HttpAPIMonitor {

  val baseUrl: String = appConfig.emailBaseUrl

  def sendEmail(emailInformation: EmailInformation)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] = {
    val url = s"$baseUrl/hmrc/email"
    monitor(s"ConsumedAPI-Send-Email-${emailInformation.templateId}") {
      http
        .POST[EmailInformation, HttpResponse](url, emailInformation)
        .map { response =>
          response.status match {
            case ACCEPTED => logger.info(s"sent email success! template: ${emailInformation.templateId}")
            case e => logger.warn(s"sent email FAILED with status $e for template: ${emailInformation.templateId}")
          }
        }
    }
  }
}
