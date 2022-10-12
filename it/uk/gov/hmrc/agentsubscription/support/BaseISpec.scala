package uk.gov.hmrc.agentsubscription.support

import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder

abstract class BaseISpec extends UnitSpec with GuiceOneServerPerSuite with WireMockSupport {
  override implicit lazy val app: Application = appBuilder
    .build()

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.auth.port"                       -> wireMockPort,
        "microservice.services.des.port"                        -> wireMockPort,
        "microservice.services.gg.port"                         -> wireMockPort,
        "microservice.services.tax-enrolments.port"             -> wireMockPort,
        "microservice.services.enrolment-store-proxy.port"      -> wireMockPort,
        "microservice.services.agent-assurance.port"            -> wireMockPort,
        "microservice.services.agent-overseas-application.host" -> wireMockHost,
        "microservice.services.agent-overseas-application.port" -> wireMockPort,
        "microservice.services.citizen-details.port"            -> wireMockPort,
        "microservice.services.email.port"                      -> wireMockPort,
        "microservice.services.agent-mapping.port"              -> wireMockPort,
        "microservice.services.agent-mapping.host"              -> wireMockHost,
        "microservice.services.companies-house-api-proxy.port"  -> wireMockPort,
        "microservice.services.companies-house-api-proxy.host"  -> wireMockHost
      )
}
