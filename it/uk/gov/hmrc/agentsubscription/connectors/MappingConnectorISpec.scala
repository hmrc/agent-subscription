package uk.gov.hmrc.agentsubscription.connectors

import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentsubscription.stubs.MappingStubs
import uk.gov.hmrc.agentsubscription.support.BaseISpec
import uk.gov.hmrc.http.HeaderCarrier
import play.api.test.Helpers._

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

  "createMappingDetails" should {
    "return unit when the mapping is successful" in {
      givenMappingDetailsCreatedWithStatus(arn, 201)

      val result = await(connector.createMappingDetails(arn))

      result shouldBe (())
    }

    "return unit when the mapping is unsuccessful because we don't want to stop subscription" in {
      givenMappingDetailsCreatedWithStatus(arn, 500)

      val result = await(connector.createMappingDetails(arn))

      result shouldBe (())
    }
  }

}
