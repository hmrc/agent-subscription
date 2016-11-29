/*
 * Copyright 2016 HM Revenue & Customs
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

package uk.gov.hmrc.agentsubscription

import java.util.Base64
import javax.inject._
import com.typesafe.config.Config
import play.api.{Application, Configuration, Play}
import play.api.mvc.Call
import uk.gov.hmrc.api.config._
import uk.gov.hmrc.api.connector.ServiceLocatorConnector
import uk.gov.hmrc.play.audit.filters.AuditFilter
import uk.gov.hmrc.play.auth.controllers.AuthParamsControllerConfig
import uk.gov.hmrc.play.config.{AppName, ControllerConfig, RunMode}
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.filters.LoggingFilter
import uk.gov.hmrc.play.microservice.bootstrap.DefaultMicroserviceGlobal
import uk.gov.hmrc.play.auth.microservice.filters.AuthorisationFilter
import net.ceedubs.ficus.Ficus._
import uk.gov.hmrc.play.filters.MicroserviceFilterSupport
import play.api.i18n.Messages.Implicits._
import play.api.Play.current
import uk.gov.hmrc.whitelist.AkamaiWhitelistFilter
import play.api.Logger


object ControllerConfiguration extends ControllerConfig {
  lazy val controllerConfigs = Play.current.configuration.underlying.as[Config]("controllers")
}

object AuthParamsControllerConfiguration extends AuthParamsControllerConfig {
  lazy val controllerConfigs = ControllerConfiguration.controllerConfigs
}

object MicroserviceAuditFilter extends AuditFilter with AppName with MicroserviceFilterSupport {
  override val auditConnector = MicroserviceAuditConnector
  override def controllerNeedsAuditing(controllerName: String) = ControllerConfiguration.paramsForController(controllerName).needsAuditing
}

object MicroserviceLoggingFilter extends LoggingFilter with MicroserviceFilterSupport {
  override def controllerNeedsLogging(controllerName: String) = ControllerConfiguration.paramsForController(controllerName).needsLogging
}

object MicroserviceAuthFilter extends AuthorisationFilter with MicroserviceFilterSupport {
  override lazy val authParamsConfig = AuthParamsControllerConfiguration
  override lazy val authConnector = MicroserviceAuthConnector
  override def controllerNeedsAuth(controllerName: String): Boolean = ControllerConfiguration.paramsForController(controllerName).needsAuth
}

@Singleton
class WhitelistFilter @Inject() (configuration: Configuration) extends AkamaiWhitelistFilter with MicroserviceFilterSupport {

  def enabled(): Boolean = configuration.getBoolean("microservice.whitelist.enabled").getOrElse(true)

  override val whitelist: Seq[String] = whitelistConfig("microservice.whitelist.ips")
  override val destination: Call = Call("GET", "/agent-subscription/forbidden")
  override val excludedPaths: Seq[Call] = Seq(
    Call("GET", "/ping/ping"),
    Call("GET", "/admin/details"),
    Call("GET", "/admin/metrics")
  )

  private def whitelistConfig(key: String): Seq[String] =
    new String(Base64.getDecoder.decode(configuration.getString(key).getOrElse("")), "UTF-8").split(",")
}


object MicroserviceGlobal extends DefaultMicroserviceGlobal
    with RunMode
    with MicroserviceFilterSupport
    with ServiceLocatorRegistration
    with ServiceLocatorConfig {
  override val auditConnector = MicroserviceAuditConnector

  override def microserviceMetricsConfig(implicit app: Application): Option[Configuration] = app.configuration.getConfig(s"microservice.metrics")

  override val loggingFilter = MicroserviceLoggingFilter
  override lazy val slConnector = ServiceLocatorConnector(WSHttp)
  override implicit val hc: HeaderCarrier = HeaderCarrier()

  override val microserviceAuditFilter = MicroserviceAuditFilter
  private lazy val whitelistFilter = Play.current.injector.instanceOf[WhitelistFilter]

  private lazy val whitelistFilterSeq = if (whitelistFilter.enabled()) {
    Logger.info("Starting microservice with IP whitelist enabled")
    Seq(whitelistFilter)
  } else {
    Logger.info("Starting microservice with IP whitelist disabled")
    Seq.empty
  }

  override val authFilter = Some(MicroserviceAuthFilter)

  override lazy val microserviceFilters = whitelistFilterSeq ++ defaultMicroserviceFilters
}
