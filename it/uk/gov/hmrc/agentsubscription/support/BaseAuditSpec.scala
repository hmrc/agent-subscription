package uk.gov.hmrc.agentsubscription.support

import org.scalatestplus.play.OneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.play.test.UnitSpec

abstract class BaseAuditSpec extends UnitSpec with OneServerPerSuite with WireMockSupport {
  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      "auditing.enabled" -> true,
      "auditing.consumer.baseUri.host" -> wireMockHost,
      "auditing.consumer.baseUri.port" -> wireMockPort,
      "microservice.services.auth.port" -> wireMockPort,
      "microservice.services.des.port" -> wireMockPort,
      "microservice.services.gg.port" -> wireMockPort,
      "microservice.services.gg-admin.port" -> wireMockPort
    )
    .build()
}
