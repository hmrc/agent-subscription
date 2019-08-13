package uk.gov.hmrc.agentsubscription.connectors

import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentsubscription.stubs.MappingStubs
import uk.gov.hmrc.agentsubscription.support.BaseISpec
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global

class MappingConnectorISpec extends BaseISpec with MappingStubs {

  val connector: MappingConnector = app.injector.instanceOf[MappingConnector]

  val arn = Arn("TARN0000001")

  private implicit val hc = HeaderCarrier()

  "createMappings" should {
    "return unit when mapping is successfully created" in {
      givenMappingCreationWithStatus(arn, 201)

      val result = await(connector.createMappings(arn))

      result shouldBe (())
    }

    "return unit when the mappings are already mapped" in {
      givenMappingCreationWithStatus(arn, 409)

      val result = await(connector.createMappings(arn))

      result shouldBe (())
    }

    "return unit when the user is not allowed to map" in {
      givenMappingCreationWithStatus(arn, 403)

      val result = await(connector.createMappings(arn))

      result shouldBe (())
    }
  }

}
