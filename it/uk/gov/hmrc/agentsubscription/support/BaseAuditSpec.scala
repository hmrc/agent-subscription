package uk.gov.hmrc.agentsubscription.support

import play.api.inject.guice.GuiceApplicationBuilder

abstract class BaseAuditSpec extends BaseISpec {
  override protected def appBuilder: GuiceApplicationBuilder = super.appBuilder
    .configure(
      "auditing.enabled"               -> true,
      "auditing.consumer.baseUri.host" -> wireMockHost,
      "auditing.consumer.baseUri.port" -> wireMockPort
    )
}
