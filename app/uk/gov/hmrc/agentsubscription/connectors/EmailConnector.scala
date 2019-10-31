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
import javax.inject.Inject
import play.api.Logger
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentsubscription.config.AppConfig
import uk.gov.hmrc.agentsubscription.model.EmailInformation
import uk.gov.hmrc.http.{ HeaderCarrier, HttpResponse }
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ ExecutionContext, Future }

@ImplementedBy(classOf[EmailConnectorImpl])
trait EmailConnector {
  def appConfig: AppConfig
  def sendEmail(emailInformation: EmailInformation)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit]
}

class EmailConnectorImpl @Inject() (val appConfig: AppConfig, http: HttpClient, metrics: Metrics)
  extends HttpAPIMonitor with EmailConnector {

  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  val baseUrl = appConfig.emailBaseUrl

  def sendEmail(emailInformation: EmailInformation)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] = {
    val url = s"$baseUrl/hmrc/email"
    monitor(s"Send-Email-${emailInformation.templateId}") {
      http
        .POST[EmailInformation, HttpResponse](url, emailInformation)
        .map(_ => ())
    }.recover {
      case e => Logger(getClass).warn(s"sending email failed: $e")
    }
  }
}
