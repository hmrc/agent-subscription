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

package uk.gov.hmrc.agentsubscription.support

import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.AnyContent
import play.api.mvc.Request
import play.api.test.FakeRequest

abstract class BaseISpec
extends UnitSpec
with GuiceOneServerPerSuite
with WireMockSupport {

  override implicit lazy val app: Application = appBuilder
    .build()

  protected def appBuilder: GuiceApplicationBuilder = new GuiceApplicationBuilder()
    .configure(
      "microservice.services.auth.port" -> wireMockPort,
      "microservice.services.des.port" -> wireMockPort,
      "microservice.services.gg.port" -> wireMockPort,
      "microservice.services.tax-enrolments.port" -> wireMockPort,
      "microservice.services.enrolment-store-proxy.port" -> wireMockPort,
      "microservice.services.agent-assurance.port" -> wireMockPort,
      "microservice.services.agent-overseas-application.host" -> wireMockHost,
      "microservice.services.agent-overseas-application.port" -> wireMockPort,
      "microservice.services.citizen-details.port" -> wireMockPort,
      "microservice.services.email.port" -> wireMockPort,
      "microservice.services.agent-mapping.port" -> wireMockPort,
      "microservice.services.agent-mapping.host" -> wireMockHost,
      "microservice.services.companies-house-api-proxy.port" -> wireMockPort,
      "microservice.services.companies-house-api-proxy.host" -> wireMockHost
    )

  implicit val fakeRequest: Request[AnyContent] = FakeRequest().withHeaders("Authorization" -> "Bearer secret")

}
