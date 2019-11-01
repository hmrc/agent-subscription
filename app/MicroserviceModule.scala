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

import com.google.inject.AbstractModule
import org.slf4j.MDC
import play.api.{ Configuration, Environment, Logger }
import uk.gov.hmrc.agentsubscription.connectors.DesConnector
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.http.{ DefaultHttpClient, HttpClient }

class MicroserviceModule(val environment: Environment, val configuration: Configuration) extends AbstractModule {

  val runModeConfiguration: Configuration = configuration

  protected def mode = environment.mode

  def configure(): Unit = {

    bind(classOf[HttpClient]).to(classOf[DefaultHttpClient])
    bind(classOf[HttpGet]).to(classOf[DefaultHttpClient])
    bind(classOf[HttpPost]).to(classOf[DefaultHttpClient])
    bind(classOf[HttpPut]).to(classOf[DefaultHttpClient])
    bind(classOf[HttpDelete]).to(classOf[DefaultHttpClient])
    ()
  }
}

