package uk.gov.hmrc.agentsubscription.support

import org.scalatestplus.play.OneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.play.test.UnitSpec

abstract class BaseISpec extends UnitSpec with OneServerPerSuite with WireMockSupport {
  override implicit lazy val app: Application = appBuilder
    .build()

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.auth.port" -> wireMockPort,
        "microservice.services.des.port" -> wireMockPort,
        "microservice.services.gg.port" -> wireMockPort,
        "microservice.services.tax-enrolments.port" -> wireMockPort,
        "microservice.services.enrolment-store-proxy.port" -> wireMockPort,
        "microservice.services.agent-assurance.port" -> wireMockPort,
        "microservice.services.agent-overseas-application.host" -> wireMockHost,
        "microservice.services.agent-overseas-application.port" -> wireMockPort,
        "microservice.services.citizen-details.port" -> wireMockPort)
}
